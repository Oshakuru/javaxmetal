package com.metallum.benchmark;

import com.google.gson.GsonBuilder;
import com.metallum.DepthDiagnostic;
import com.metallum.Metallum;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Stationary baseline: start-to-start render-loop intervals, including pacing/stalls. */
public final class BenchmarkRecorder {
    private static final long WARMUP_NS = 30_000_000_000L;
    private static final long MEASUREMENT_NS = 60_000_000_000L;
    private static final int RUNS = 3;
    private static final int MAX_SAMPLES = 500_000;
    private static Session session;

    private BenchmarkRecorder() {}

    public static void toggle(Minecraft client) {
        if (session != null) {
            finish(client, "CANCELLED: F9 pressed");
        } else if (client.level != null && client.player != null && client.gui.screen() == null
                && client.gui.overlay() == null && !DepthDiagnostic.enabled
                && !(com.metallum.visual.VisualState.enabled && com.metallum.visual.VisualState.materials) && client.isWindowActive()) {
            if (client.options.inactivityFpsLimit().get() == net.minecraft.client.InactivityFpsLimit.AFK) {
                message(client, "Disable the AFK FPS cap in settings before starting a stationary benchmark.");
                return;
            }
            session = new Session(client, System.nanoTime());
            message(client, "Benchmark started: 3 × (30s warm-up + 60s recording). Keep still. F9 cancels.");
        } else {
            message(client, "Benchmark requires focused gameplay with F8 diagnostics OFF and menus closed.");
        }
    }

    public static void frame(Minecraft client) {
        Session current = session;
        if (current == null) return;
        long now = System.nanoTime();
        String interruption = current.interruption(client);
        if (interruption != null) {
            finish(client, "INVALID: " + interruption);
            return;
        }
        // Validate the effective limiter without changing the user's idle timer or preferences.
        var limiter = client.getFramerateLimitTracker();
        if (limiter.getThrottleReason() != com.mojang.blaze3d.platform.FramerateLimitTracker.FramerateThrottleReason.NONE
                || limiter.getFramerateLimit() != client.options.framerateLimit().get()) {
            finish(client, "INVALID: unexpected frame limiter or throttle active");
            return;
        }
        if (!current.recording) {
            if (now - current.phaseStart >= WARMUP_NS) {
                current.recording = true;
                current.phaseStart = now;
                // Seed on the next frame so phase notifications are excluded.
                current.previousFrame = 0;
                Metallum.LOGGER.info("Benchmark {}/3: recording 60 seconds", current.run + 1);
            }
            return;
        }
        if (current.previousFrame == 0) {
            current.previousFrame = now;
            current.phaseStart = now;
            return;
        }
        int count = current.counts[current.run];
        if (count == MAX_SAMPLES) {
            finish(client, "INVALID: sample capacity exceeded");
            return;
        }
        current.samples[current.run][count] = now - current.previousFrame;
        current.counts[current.run]++;
        current.previousFrame = now;
        if (now - current.phaseStart >= MEASUREMENT_NS) {
            current.run++;
            if (current.run == RUNS) {
                finish(client, "COMPLETE");
            } else {
                current.recording = false;
                current.phaseStart = now;
                message(client, "Benchmark " + current.run + "/3 finished; warming up next run.");
            }
        }
    }

    private static void finish(Minecraft client, String status) {
        Session completed = session;
        session = null;
        if (completed == null) return;
        try {
            Path directory = client.gameDirectory.toPath().resolve("metallum-benchmarks").resolve(completed.id);
            Files.createDirectories(directory);
            Map<String, Object> report = new LinkedHashMap<>(completed.metadata);
            report.put("status", status);
            report.put("finishedUtc", Instant.now().toString());
            report.put("metric", "Wall-clock renderFrame HEAD-to-HEAD intervals; includes frame pacing and CPU/GPU stalls. Not GPU execution time or monitor presentation latency.");
            report.put("percentileMethod", "nearest-rank");
            var runs = new ArrayList<Map<String, Object>>();
            try (var csv = Files.newBufferedWriter(directory.resolve("frames.csv"))) {
                csv.write("run,frame,interval_ns\n");
                for (int run = 0; run < RUNS; run++) {
                    int count = completed.counts[run];
                    if (count == 0) continue;
                    runs.add(Map.of("run", run + 1, "complete", run < completed.run,
                            "statistics", FrameTimeStats.calculate(completed.samples[run], count)));
                    for (int frame = 0; frame < count; frame++) {
                        csv.write((run + 1) + "," + frame + "," + completed.samples[run][frame] + "\n");
                    }
                }
            }
            report.put("runs", runs);
            Files.writeString(directory.resolve("report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            message(client, "Benchmark " + status + ". Saved: metallum-benchmarks/" + completed.id + "/report.json");
            for (var run : runs) Metallum.LOGGER.info("Benchmark result {}", run);
        } catch (IOException | RuntimeException exception) {
            Metallum.LOGGER.error("Could not save benchmark results", exception);
            message(client, "Benchmark results could not be saved; see the log.");
        }
    }

    private static String fingerprint(Path file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (IOException | NoSuchAlgorithmException exception) {
            Metallum.LOGGER.warn("Benchmark fingerprint unavailable for {}", file, exception);
            return "unavailable";
        }
    }

    private static void message(Minecraft client, String text) {
        Metallum.LOGGER.info(text);
        if (client.player != null) client.gui.chatListener().handleSystemMessage(Component.literal(text), false);
    }

    private static final class Session {
        final String id = Instant.now().toString().replace(':', '-') + "-" + UUID.randomUUID().toString().substring(0, 8);
        final long[][] samples = new long[RUNS][MAX_SAMPLES];
        final int[] counts = new int[RUNS];
        final Map<String, Object> metadata = new LinkedHashMap<>();
        final Vec3 position;
        final float pitch, yaw;
        final int width, height;
        final Object level;
        final Settings settings;
        final boolean visuals = com.metallum.visual.VisualState.enabled;
        final boolean materialDiagnostic = com.metallum.visual.VisualState.materials;
        final int visualRevision = com.metallum.visual.VisualState.revision;
        int run;
        boolean recording;
        long phaseStart, previousFrame;

        Session(Minecraft client, long now) {
            var camera = client.gameRenderer.mainCamera();
            position = camera.position();
            pitch = camera.xRot();
            yaw = camera.yRot();
            width = client.getWindow().getWidth();
            height = client.getWindow().getHeight();
            level = client.level;
            settings = Settings.capture(client);
            phaseStart = now;
            metadata.put("visualPrototypeEnabled", visuals);
            metadata.put("materialDiagnostic", materialDiagnostic);
            metadata.put("startedUtc", Instant.now().toString());
            metadata.put("warmupSecondsPerRun", 30);
            metadata.put("measurementSecondsPerRun", 60);
            metadata.put("plannedRuns", RUNS);
            metadata.put("stationaryAfkSuppression", "None; user disables AFK cap manually, with runtime throttle validation");
            metadata.put("inactivityFpsPolicy", client.options.inactivityFpsLimit().get().toString());
            metadata.put("throttleValidation", "Each valid sampled/warmup frame requires throttle reason NONE and effective cap equal to configured cap");
            metadata.put("framebufferWidth", width);
            metadata.put("framebufferHeight", height);
            metadata.put("dimension", client.level.dimension().toString());
            metadata.put("raining", client.level.isRaining());
            metadata.put("savedOptionsSha256", fingerprint(client.gameDirectory.toPath().resolve("options.txt")));
            var self = FabricLoader.getInstance().getModContainer("metallum").orElseThrow();
            metadata.put("artifactSha256", self.getOrigin().getPaths().stream().filter(Files::isRegularFile)
                    .map(BenchmarkRecorder::fingerprint).findFirst().orElse("development directory; no JAR fingerprint"));
            metadata.put("upstreamRevision", "467d312b8bc68ee3210697f01855244e8f2831a2; local prototype changes");
            metadata.put("camera", Map.of("x", position.x, "y", position.y, "z", position.z, "pitch", pitch, "yaw", yaw));
            metadata.put("settings", settings);
            metadata.put("device", RenderSystem.getDevice().getDeviceInfo().toString());
            metadata.put("java", System.getProperty("java.version"));
            metadata.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
            metadata.put("mods", FabricLoader.getInstance().getAllMods().stream()
                    .map(mod -> mod.getMetadata().getId() + "=" + mod.getMetadata().getVersion().getFriendlyString()).sorted().toList());
        }

        String interruption(Minecraft client) {
            if (client.level != level || client.player == null) return "world changed";
            if (!client.isWindowActive()) return "window lost focus";
            if (client.isPaused() || client.gui.screen() != null || client.gui.overlay() != null) return "menu, pause, or overlay opened";
            if (DepthDiagnostic.enabled || (com.metallum.visual.VisualState.enabled && com.metallum.visual.VisualState.materials)) return "diagnostic enabled";
            if (visuals != com.metallum.visual.VisualState.enabled || materialDiagnostic != com.metallum.visual.VisualState.materials
                    || visualRevision != com.metallum.visual.VisualState.revision) return "visual mode or resources changed";
            if (client.getWindow().getWidth() != width || client.getWindow().getHeight() != height) return "framebuffer resized";
            var camera = client.gameRenderer.mainCamera();
            if (camera.position().distanceToSqr(position) > 0.0004
                    || Math.abs(camera.xRot() - pitch) > 0.05f || Math.abs(camera.yRot() - yaw) > 0.05f)
                return "camera moved";
            if (!settings.matches(client)) return "graphics settings changed";
            return null;
        }

    }

    private record Settings(int render, int simulation, int fov, int cap, boolean vsync, boolean fullscreen) {
        static Settings capture(Minecraft client) {
            return new Settings(client.options.renderDistance().get(), client.options.simulationDistance().get(),
                    client.options.fov().get(), client.options.framerateLimit().get(),
                    client.options.enableVsync().get(), client.options.fullscreen().get());
        }

        boolean matches(Minecraft client) {
            return render == client.options.renderDistance().get()
                    && simulation == client.options.simulationDistance().get()
                    && fov == client.options.fov().get() && cap == client.options.framerateLimit().get()
                    && vsync == client.options.enableVsync().get() && fullscreen == client.options.fullscreen().get();
        }
    }
}
