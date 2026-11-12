package com.metallum.diagnostic;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import java.nio.FloatBuffer;

/** Render-thread-owned transform, valid only for its world, dimensions, and frame. */
public final class CameraSnapshot {
    private final Matrix4f inverse = new Matrix4f();
    private Object world;
    private int width, height;
    private boolean valid;

    public void beginFrame() {
        valid = false;
        world = null;
    }

    public void capture(Object world, int width, int height, Matrix4fc projection, Matrix4fc viewRotation) {
        beginFrame();
        if (world == null || width <= 0 || height <= 0) return;
        inverse.set(projection).mul(viewRotation).invert();
        if (!inverse.isFinite()) return;
        this.world = world;
        this.width = width;
        this.height = height;
        valid = true;
    }

    public boolean copyInverse(Object world, int width, int height, Matrix4f destination) {
        if (!valid || this.world != world || this.width != width || this.height != height) return false;
        destination.set(inverse);
        return true;
    }

    public static void putMatrix(Matrix4fc matrix, FloatBuffer buffer) {
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) buffer.put(column * 4 + row, matrix.get(column, row));
        }
    }
}
