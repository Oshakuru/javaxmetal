package com.metallum;

/** Session-only diagnostic; disabled on every launch. */
public final class DepthDiagnostic {
    public static boolean enabled;
    public static int mode;

    private DepthDiagnostic() {}

    public static void toggle() {
        mode = (mode + 1) % 3;
        enabled = mode != 0;
        Metallum.LOGGER.info("Graphics diagnostic: {} (F8)", switch (mode) {
            case 1 -> "depth";
            case 2 -> "world surface directions";
            default -> "off";
        });
    }
}
