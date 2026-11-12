package com.metallum.visual;

import com.metallum.Metallum;

/** Session-only controls; the normal renderer is the startup path. */
public final class VisualState {
    public static boolean enabled;
    public static boolean materials;
    public static int revision;

    private VisualState() {}

    public static void toggle() {
        enabled = !enabled;
        revision++;
        Metallum.LOGGER.info("Mixed-material prototype {} (F6); F10 toggles material diagnostic", enabled ? "ON" : "OFF");
    }

    public static void invalidate() { revision++; }
}
