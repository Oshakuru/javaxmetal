package com.metallum.diagnostic;

import com.metallum.DepthDiagnostic;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

public final class DiagnosticCameraCapture {
    private static final CameraSnapshot CAMERA = new CameraSnapshot();

    private DiagnosticCameraCapture() {}

    public static void beginFrame() {
        CAMERA.beginFrame();
    }

    public static void capture(Matrix4f effectiveProjection) {
        if (!DepthDiagnostic.enabled) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        var state = client.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        if (state == null) return;
        var target = client.gameRenderer.mainRenderTarget();
        var depth = target.getDepthTexture();
        if (depth == null) return;
        CAMERA.capture(client.level, depth.getWidth(0), depth.getHeight(0), effectiveProjection, state.viewRotationMatrix);
    }

    public static boolean copyInverse(int width, int height, Matrix4f destination) {
        return CAMERA.copyInverse(Minecraft.getInstance().level, width, height, destination);
    }
}
