package com.metallum.visual;

/** Shared CPU/GPU layout: X fastest, then Z, then Y; six float4 faces per cell. */
public final class MaterialGrid {
    public static final int SIDE = 24;
    public static final int CELLS = SIDE * SIDE * SIDE;
    public static final int STRIDE = 112; // six material faces, then RGB emission + occupancy
    public static final int GRID_BYTES = CELLS * STRIDE;
    public static final int MAX_LIGHTS = 8;
    public static final int BYTES = GRID_BYTES + MAX_LIGHTS * 32;

    private MaterialGrid() {}

    public static int cell(int x, int y, int z) {
        return x < 0 || y < 0 || z < 0 || x >= SIDE || y >= SIDE || z >= SIDE
                ? -1 : (y * SIDE + z) * SIDE + x;
    }

    public static int face(float x, float y, float z) {
        if (Math.abs(y) > 0.995f) return y > 0 ? 1 : 0;
        if (Math.abs(z) > 0.995f) return z > 0 ? 3 : 2;
        if (Math.abs(x) > 0.995f) return x > 0 ? 5 : 4;
        return -1;
    }

    public static boolean corner(float x, float y, float z, int face) {
        if (!unit(x) || !unit(y) || !unit(z)) return false;
        return switch (face) {
            case 0 -> y == 0; case 1 -> y == 1;
            case 2 -> z == 0; case 3 -> z == 1;
            case 4 -> x == 0; case 5 -> x == 1;
            default -> false;
        };
    }

    private static boolean unit(float n) { return n == 0 || n == 1; }
}
