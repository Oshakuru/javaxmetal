# First automated Metal baseline

Use `metallum-0.0.25-benchmark.1.jar` in the duplicate Prism test instance, replacing the previous Metallum JAR. Keep Iris disabled. This is a diagnostic-OFF baseline; it adds no reflections and does not resolve the known normals-view camera-transform limitation.

## Prepare once

1. Plug the Mac into power. Keep its power mode, other applications, resource packs, mods, and game settings unchanged between comparisons. Stop screen recording. Let the Mac cool if it is already unusually hot.
2. Use 1440 × 900 actual framebuffer resolution, render distance 8, simulation distance 8, VSync off, unlimited FPS. The report records actual dimensions; do not assume window size equals framebuffer size on Retina displays. Keep FOV and graphics quality fixed.
3. Use a copy of a single-player world. For stronger control, enable commands in that test copy and set these before choosing the view:

   ```text
   /gamerule advance_time false
   /gamerule advance_weather false
   /time set noon
   /weather clear
   ```

   These change the test world. The recorder itself does not change worlds, time, or weather. If commands are unavailable, record that time/weather were uncontrolled and use the run as an initial baseline only.
4. Stand still on solid ground, looking at the same snowy shore used previously. Avoid flying, swimming, moving entities, or nearby machinery. Wait until chunks load and camera motion settles. Save one F3 screenshot to document the scene, then hide F3 before starting. Do not take screenshots during timed recording.
5. Close chat and menus. Make sure F8 diagnostics are OFF. The recorder refuses to start while a diagnostic is active.

## Run

Press **F9** (or **Fn+F9**) once. Leave the camera still and the Minecraft window focused for about **4½ minutes**. It automatically performs three rounds of 30-second warm-up followed by 60-second recording. Progress notices appear between rounds. Press F9 again to cancel.

Do not open menus, move the mouse/camera, resize, switch apps, or change settings. Those events mark the session INVALID. A cancelled/invalid report is diagnostic evidence, not a valid comparison. The recorder monitors six main settings; other visual settings, the world state, and external workload still need to remain fixed manually.

## Results

The final chat message identifies a folder under:

`<instance Minecraft folder>/metallum-benchmarks/<session>/`

It contains:

- `report.json`: status, three run summaries, settings, environment, and JAR fingerprint.
- `frames.csv`: raw nanosecond frame intervals for deeper analysis.

In Prism, use the instance's folder controls to open its Minecraft folder, then open `metallum-benchmarks`. Send `report.json` first. Retain `frames.csv`; send it if we need to investigate stutters. If F9 has no effect or the game fails, send the latest log instead. This first run also verifies the recorder's in-game hooks and export behavior.

We will compare median, p95, p99, and worst frame times across all three rounds and look for drift or outliers. These are wall-clock game-loop intervals, including pacing and stalls—not isolated GPU timings or a count of frames displayed by the 60 Hz monitor. No speedup or 60 FPS guarantee follows from compilation alone.

Keep visual recordings separate: after the performance run, a short video can test motion artifacts without contaminating these measurements.
