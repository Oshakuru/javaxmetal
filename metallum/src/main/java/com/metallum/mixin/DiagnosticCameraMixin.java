package com.metallum.mixin;

import com.metallum.diagnostic.DiagnosticCameraCapture;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class DiagnosticCameraMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void metallum$beginDiagnosticFrame(CallbackInfo ci) {
        DiagnosticCameraCapture.beginFrame();
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;render3dHud(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/OptionsRenderState;Z)V"),
            require = 1, expect = 1, allow = 1)
    private void metallum$shadeWorldBeforeHud(CallbackInfo ci) {
        com.metallum.render.MetalWorldEffects.renderWorld();
    }

    // Observe the final projection after bobbing/hurt/nausea, without modifying it.
    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"),
            index = 0, require = 1, expect = 1, allow = 1)
    private Matrix4f metallum$captureEffectiveWorldProjection(Matrix4f projection) {
        DiagnosticCameraCapture.capture(projection);
        return projection;
    }
}
