package com.metallum.visual;

import com.metallum.Metallum;
import com.metallum.material.MaterialCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.BitSet;
import java.util.IdentityHashMap;
import net.minecraft.client.renderer.texture.SpriteContents;
import com.metallum.mixin.SpriteImageAccessor;

/** Bounded render-thread cache. Only unambiguous opaque cube model faces are supported. */
public final class NearbyMaterials implements AutoCloseable {
    private static final Direction[] FACES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final Identifier CATALOG = Identifier.fromNamespaceAndPath("metallum", "materials/default.json");
    private ByteBuffer data = MemoryUtil.memCalloc(MaterialGrid.BYTES);
    private ByteBuffer building = MemoryUtil.memCalloc(MaterialGrid.BYTES);
    private final BitSet dirty = new BitSet(MaterialGrid.CELLS);
    private final IdentityHashMap<SpriteContents, float[]> colors = new IdentityHashMap<>();
    private final LocalLights lights = new LocalLights();
    public int lightCount;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final RandomSource random = RandomSource.create(0);
    private final List<BlockStateModelPart> parts = new ArrayList<>();
    private MaterialCatalog catalog = MaterialCatalog.empty();
    private Object world;
    private int revision = -1, cursor;
    public int x, y, z;
    public boolean ready;
    public long generation;

    public ByteBuffer data() { return data; }
    public boolean available() { return ready && dirty.isEmpty(); }

    public void invalidate(int worldX, int worldY, int worldZ) {
        int cell = MaterialGrid.cell(worldX - x, worldY - y, worldZ - z);
        if (cell >= 0) dirty.set(cell);
    }

    public void invalidateChunk(int chunkX, int chunkZ) {
        for (int ly = 0; ly < 24; ly++) for (int lz = 0; lz < 24; lz++) for (int lx = 0; lx < 24; lx++) {
            if (((x + lx) >> 4) == chunkX && ((z + lz) >> 4) == chunkZ) dirty.set(MaterialGrid.cell(lx, ly, lz));
        }
    }

    public void update(Minecraft client, double cameraX, double cameraY, double cameraZ) {
        int cx = (int) Math.floor(cameraX), cy = (int) Math.floor(cameraY), cz = (int) Math.floor(cameraZ);
        if (world != client.level || revision != VisualState.revision
                || Math.abs(cx - (x + 12)) > 4 || Math.abs(cy - (y + 12)) > 4 || Math.abs(cz - (z + 12)) > 4) {
            boolean reload = revision != VisualState.revision || world != client.level;
            world = client.level;
            revision = VisualState.revision;
            x = cx - 12; y = cy - 12; z = cz - 12;
            cursor = 0;
            ready = false;
            MemoryUtil.memSet(data, 0);
            MemoryUtil.memSet(building, 0);
            dirty.clear();
            if (reload) {
                colors.clear();
                try (var reader = client.getResourceManager().getResource(CATALOG).orElseThrow().openAsReader()) {
                    catalog = MaterialCatalog.read(reader);
                } catch (Exception exception) {
                    catalog = MaterialCatalog.empty();
                    Metallum.LOGGER.error("Visual material catalog unavailable; keeping surfaces unchanged", exception);
                }
            }
        }
        int budget = 768;
        boolean changed = false;
        for (int cell = dirty.nextSetBit(0); cell >= 0 && budget > 0; cell = dirty.nextSetBit(cell + 1)) {
            rebuild(client, cell, data);
            int at = cell * MaterialGrid.STRIDE;
            for (int i = 0; i < MaterialGrid.STRIDE; i += 4) building.putFloat(at + i, data.getFloat(at + i));
            dirty.clear(cell);
            budget--;
            changed = true;
        }
        for (int limit = Math.min(cursor + budget, MaterialGrid.CELLS); cursor < limit; cursor++) rebuild(client, cursor, building);
        if (cursor == MaterialGrid.CELLS) {
            cursor = 0;
            ByteBuffer previous = data;
            data = building;
            building = previous;
            ready = true;
            changed = true;
        }
        if (changed) {
            lightCount = lights.write(data, cameraX - x, cameraY - y, cameraZ - z);
            generation++;
        }
    }

    private void rebuild(Minecraft client, int cell, ByteBuffer output) {
        int lx = cell % MaterialGrid.SIDE;
        int lz = cell / MaterialGrid.SIDE % MaterialGrid.SIDE;
        int ly = cell / (MaterialGrid.SIDE * MaterialGrid.SIDE);
        pos.set(x + lx, y + ly, z + lz);
        int offset = cell * MaterialGrid.STRIDE;
        for (int i = 0; i < 28; i++) output.putFloat(offset + i * 4, 0);
        if (!client.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) { output.putFloat(offset + 108, -1); return; }
        var state = client.level.getBlockState(pos);
        if (state.isAir()) return;
        if (!state.isSolidRender() || !state.isCollisionShapeFullBlock(client.level, pos)) {
            output.putFloat(offset + 108, -1);
            return;
        }
        output.putFloat(offset + 108, 1);
        parts.clear();
        random.setSeed(state.getSeed(pos));
        client.getModelManager().getBlockStateModelSet().get(state).collectParts(random, parts);
        float red = 0, green = 0, blue = 0;
        int emitFaces = 0;
        for (int face = 0; face < 6; face++) {
            MaterialCatalog.Material material = null;
            SpriteContents sprite = null;
            boolean ambiguous = false;
            int count = 0;
            for (var part : parts) {
                for (int list = 0; list < 2; list++) {
                    for (var quad : part.getQuads(list == 0 ? FACES[face] : null)) {
                        if (quad.direction() != FACES[face]) continue;
                        count++;
                        if (!fullFace(quad, face)) { ambiguous = true; continue; }
                        var candidate = catalog.get(quad.materialInfo().sprite().contents().name().toString());
                        if (candidate == MaterialCatalog.UNASSIGNED || candidate.transmission() > 0
                                || (material != null && !material.equals(candidate))) ambiguous = true;
                        material = candidate;
                        sprite = quad.materialInfo().sprite().contents();
                    }
                }
            }
            if (ambiguous || count != 1 || material == null) continue;
            int at = offset + face * 16;
            output.putFloat(at, material.roughness());
            output.putFloat(at + 4, material.metalness());
            output.putFloat(at + 8, material.emission());
            output.putFloat(at + 12, 1);
            if (material.emission() > 0) {
                float[] color = colors.computeIfAbsent(sprite, NearbyMaterials::emitterColor);
                red += color[0] * material.emission();
                green += color[1] * material.emission();
                blue += color[2] * material.emission();
                emitFaces++;
            }
        }
        if (emitFaces > 0) {
            output.putFloat(offset + 96, red / emitFaces);
            output.putFloat(offset + 100, green / emitFaces);
            output.putFloat(offset + 104, blue / emitFaces);
        }
    }

    private static float[] emitterColor(SpriteContents sprite) {
        var image = ((SpriteImageAccessor) sprite).metallum$sourceImage();
        float[] rgb = new float[3];
        float weight = 0;
        // shortcut: sample the first animation frame; dynamic per-frame emissive maps need a GPU material attachment.
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            int pixel = image.getPixel(Math.min(sprite.width() - 1, x * sprite.width() / 8),
                    Math.min(sprite.height() - 1, y * sprite.height() / 8));
            float alpha = ((pixel >>> 24) & 255) / 255f;
            rgb[0] += (float) Math.pow(((pixel >>> 16) & 255) / 255f, 2.2) * alpha;
            rgb[1] += (float) Math.pow(((pixel >>> 8) & 255) / 255f, 2.2) * alpha;
            rgb[2] += (float) Math.pow((pixel & 255) / 255f, 2.2) * alpha;
            weight += alpha;
        }
        if (weight > 0) for (int i = 0; i < 3; i++) rgb[i] /= weight;
        return rgb;
    }

    private static boolean fullFace(BakedQuad quad, int face) {
        int corners = 0;
        for (int i = 0; i < 4; i++) {
            var p = quad.position(i);
            if (!MaterialGrid.corner(p.x(), p.y(), p.z(), face)) return false;
            int bit = face < 2 ? (int) (p.x() + p.z() * 2)
                    : face < 4 ? (int) (p.x() + p.y() * 2) : (int) (p.z() + p.y() * 2);
            corners |= 1 << bit;
        }
        return corners == 15;
    }

    @Override public void close() { MemoryUtil.memFree(data); MemoryUtil.memFree(building); }
}
