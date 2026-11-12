package com.metallum.visual;

import java.nio.ByteBuffer;

/** Selects a fixed-size, deterministic nearest-light list from the committed grid. */
public final class LocalLights {
    private final int[] cells = new int[MaterialGrid.MAX_LIGHTS];
    private final double[] distances = new double[MaterialGrid.MAX_LIGHTS];

    public int write(ByteBuffer data, double cameraX, double cameraY, double cameraZ) {
        int count = 0;
        for (int cell = 0; cell < MaterialGrid.CELLS; cell++) {
            int at = cell * MaterialGrid.STRIDE + 96;
            if (data.getFloat(at) + data.getFloat(at + 4) + data.getFloat(at + 8) <= 0) continue;
            double dx = cell % 24 + 0.5 - cameraX;
            double dz = cell / 24 % 24 + 0.5 - cameraZ;
            double dy = cell / (24 * 24) + 0.5 - cameraY;
            double distance = dx * dx + dy * dy + dz * dz;
            int place = count;
            while (place > 0 && distance < distances[place - 1]) place--;
            if (place >= MaterialGrid.MAX_LIGHTS) continue;
            for (int i = Math.min(count, MaterialGrid.MAX_LIGHTS - 1); i > place; i--) {
                cells[i] = cells[i - 1]; distances[i] = distances[i - 1];
            }
            cells[place] = cell; distances[place] = distance;
            count = Math.min(count + 1, MaterialGrid.MAX_LIGHTS);
        }
        for (int i = 0; i < MaterialGrid.MAX_LIGHTS; i++) {
            int at = MaterialGrid.GRID_BYTES + i * 32;
            for (int j = 0; j < 8; j++) data.putFloat(at + j * 4, 0);
            if (i >= count) continue;
            int cell = cells[i];
            data.putFloat(at, cell % 24 + 0.5f);
            data.putFloat(at + 4, cell / (24 * 24) + 0.5f);
            data.putFloat(at + 8, cell / 24 % 24 + 0.5f);
            data.putFloat(at + 12, 1);
            int emission = cell * MaterialGrid.STRIDE + 96;
            for (int channel = 0; channel < 3; channel++) data.putFloat(at + 16 + channel * 4, data.getFloat(emission + channel * 4));
        }
        return count;
    }
}
