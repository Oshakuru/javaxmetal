package com.metallum.render;

import com.metallum.Metallum;
import com.metallum.diagnostic.CameraSnapshot;
import com.metallum.diagnostic.DiagnosticCameraCapture;
import com.metallum.visual.MaterialGrid;
import com.metallum.visual.NearbyMaterials;
import com.metallum.visual.VisualState;
import com.mojang.renderpearl.api.textures.GpuTexture;
import io.github.kokodio.metaljvm.metal.*;
import io.github.kokodio.metaljvm.objc.AutoreleasePool;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.lwjgl.system.MemoryStack;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;

/** World-only pass. All native resources belong to the backend's render thread. */
public final class MetalWorldEffects implements AutoCloseable {
    private static MetalWorldEffects active;
    private final MetalDevice device;
    private final MetalCommandEncoder commands;
    private final Matrix4f inverse = new Matrix4f(), projection = new Matrix4f();
    private final Vector3d camera = new Vector3d();
    private NearbyMaterials nearby;
    private MetalGpuTexture scene, half;
    private MTLBuffer grid;
    private MTLRenderPipelineState pipeline, halfPipeline, compositePipeline;
    private MTLPixelFormat format;
    private long generation = -1;
    private Object world;

    MetalWorldEffects(MetalDevice device, MetalCommandEncoder commands) {
        this.device = device;
        this.commands = commands;
        active = this;
    }

    public static void invalidateCell(Object world, net.minecraft.core.BlockPos pos) {
        if (VisualState.enabled && active != null && active.world == world && active.nearby != null)
            active.nearby.invalidate(pos.getX(), pos.getY(), pos.getZ());
    }

    public static void invalidateChunk(Object world, int x, int z) {
        if (VisualState.enabled && active != null && active.world == world && active.nearby != null)
            active.nearby.invalidateChunk(x, z);
    }

    public static void beginFrame() {
        if (active != null && (!VisualState.enabled || Minecraft.getInstance().level == null)) active.releaseScene();
    }

    public static void renderWorld() {
        if (active == null || !VisualState.enabled) return;
        try {
            active.render(Minecraft.getInstance());
        } catch (RuntimeException exception) {
            VisualState.enabled = false;
            active.releaseScene();
            active.releasePipelines();
            Metallum.LOGGER.error("Mixed-material prototype failed; disabled, normal rendering retained", exception);
        }
    }

    private void render(Minecraft client) {
        if (client.level == null) return;
        var target = client.gameRenderer.mainRenderTarget();
        if (!(target.getColorTexture() instanceof MetalGpuTexture color)
                || !(target.getDepthTexture() instanceof MetalGpuTexture depth)) return;
        int width = color.getWidth(0), height = color.getHeight(0);
        if (width < 2 || height < 2 || width != depth.getWidth(0) || height != depth.getHeight(0)
                || !DiagnosticCameraCapture.copyInverse(width, height, inverse)) return;
        DiagnosticCameraCapture.copyPosition(camera);
        if (world != client.level) {
            releaseScene();
            world = client.level;
        }
        if (nearby == null) nearby = new NearbyMaterials();
        nearby.update(client, camera.x, camera.y, camera.z);
        if (!nearby.available()) return;
        if (grid == null || generation != nearby.generation) {
            MTLBuffer next = MetalUtilities.nonNil(device.metalDevice().newBuffer(MaterialGrid.BYTES,
                    MTLResourceOptions.StorageModeShared), "visual material buffer");
            next.contents().reinterpret(MaterialGrid.BYTES).copyFrom(MemorySegment.ofBuffer(nearby.data()));
            if (grid != null) device.queueResourceRelease(grid);
            grid = next;
            generation = nearby.generation;
        }
        if (pipeline == null || format != color.mtlPixelFormat()) {
            releasePipelines();
            format = color.mtlPixelFormat();
            try (var input = MetalWorldEffects.class.getResourceAsStream("/assets/metallum/shaders/mixed_material.metal")) {
                if (input == null) throw new IOException("Missing mixed material shader");
                String shader = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                pipeline = MetalUtilities.buildPipeline(shader, "visual_vs", "visual_fs", new MTLPixelFormat[]{format}, MTLPixelFormat.Invalid, 1);
                halfPipeline = MetalUtilities.buildPipeline(shader, "visual_vs", "visual_half_fs", new MTLPixelFormat[]{MTLPixelFormat.RGBA16Float}, MTLPixelFormat.Invalid, 1);
                compositePipeline = MetalUtilities.buildPipeline(shader, "visual_vs", "visual_composite_fs", new MTLPixelFormat[]{format}, MTLPixelFormat.Invalid, 1);
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot load mixed material shader", exception);
            }
            if (pipeline == null || halfPipeline == null || compositePipeline == null) {
                releasePipelines();
                VisualState.enabled = false;
                releaseScene();
                Metallum.LOGGER.error("Mixed-material shader compilation failed; prototype disabled");
                return;
            }
        }
        if (scene == null || scene.getWidth(0) != width || scene.getHeight(0) != height || scene.getFormat() != color.getFormat()) {
            if (scene != null) scene.close();
            scene = new MetalGpuTexture(device, GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING,
                    "Mixed material world color", color.getFormat(), width, height, 1, 1);
        }
        if (half == null || half.getWidth(0) != (width + 1) / 2 || half.getHeight(0) != (height + 1) / 2) {
            if (half != null) half.close();
            half = new MetalGpuTexture(device, GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
                    "Mixed material half-resolution correction", com.mojang.renderpearl.api.GpuFormat.RGBA16_FLOAT,
                    (width + 1) / 2, (height + 1) / 2, 1, 1);
        }
        commands.prepareWorldEffects(color, depth);
        projection.set(inverse).invert();
        try (AutoreleasePool pool = AutoreleasePool.push(); MemoryStack stack = MemoryStack.stackPush()) {
            var uniforms = stack.callocFloat(40);
            CameraSnapshot.putMatrix(inverse, uniforms);
            CameraSnapshot.putMatrix(projection, uniforms.slice(16, 16));
            uniforms.put(32, (float) (camera.x - nearby.x));
            uniforms.put(33, (float) (camera.y - nearby.y));
            uniforms.put(34, (float) (camera.z - nearby.z));
            uniforms.put(36, VisualState.materials ? 1 : 0);
            uniforms.put(37, nearby.lightCount);
            if (VisualState.materials) {
                encode(pipeline, scene, color, depth, uniforms, false);
            } else {
                encode(halfPipeline, half, color, depth, uniforms, false);
                encode(compositePipeline, scene, color, depth, uniforms, true);
            }
        }
        commands.copyTextureToTexture(scene, color, 0, 0, 0, 0, 0, width, height);
    }

    private void encode(MTLRenderPipelineState state, MetalGpuTexture output, MetalGpuTexture color,
            MetalGpuTexture depth, java.nio.FloatBuffer uniforms, boolean composite) {
        var pass = MTLRenderPassDescriptor.alloc().init();
        MTLRenderCommandEncoder encoder;
        try {
            var attachment = pass.colorAttachments().objectAtIndexedSubscript(0);
            attachment.setTexture(output.metalTexture());
            attachment.setLoadAction(MTLLoadAction.DontCare);
            attachment.setStoreAction(MTLStoreAction.Store);
            encoder = MetalUtilities.nonNil(commands.commandBuffer().renderCommandEncoder(pass), "world visual encoder");
        } finally {
            pass.release();
        }
        try {
            encoder.waitForFence(commands.worldEffectsFence(), MTLRenderStages.Fragment);
            encoder.setRenderPipelineState(state);
            encoder.setViewport(new MTLViewport(0, 0, output.getWidth(0), output.getHeight(0), 0, 1));
            encoder.setFragmentTexture(color.metalTexture(), 0);
            encoder.setFragmentTexture(composite ? half.metalTexture() : depth.metalTexture(), 1);
            if (composite) encoder.setFragmentTexture(depth.metalTexture(), 2);
            encoder.setFragmentBuffer(grid, 0, 1);
            encoder.setFragmentBytes(MemorySegment.ofBuffer(uniforms), 160, 0);
            encoder.drawPrimitives(MTLPrimitiveType.Triangle, 0, 3, 1, 0);
        } finally {
            try { encoder.updateFence(commands.worldEffectsFence(), MTLRenderStages.Fragment); }
            finally { encoder.endEncoding(); }
        }
        output.markContentsDirty();
    }

    private void releaseScene() {
        if (scene != null) { scene.close(); scene = null; }
        if (half != null) { half.close(); half = null; }
        if (grid != null) { device.queueResourceRelease(grid); grid = null; }
        if (nearby != null) { nearby.close(); nearby = null; }
        generation = -1;
        world = null;
    }

    private void releasePipelines() {
        if (pipeline != null) { device.queueResourceRelease(pipeline); pipeline = null; }
        if (halfPipeline != null) { device.queueResourceRelease(halfPipeline); halfPipeline = null; }
        if (compositePipeline != null) { device.queueResourceRelease(compositePipeline); compositePipeline = null; }
    }

    @Override public void close() {
        releaseScene();
        releasePipelines();
        if (active == this) active = null;
    }
}
