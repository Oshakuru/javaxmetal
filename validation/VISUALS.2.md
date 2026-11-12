# visuals.2: one combined acceptance session

Candidate: `metallum-0.0.25-visuals.2.jar`. Checksum, source hashes and local results: `visuals.2-build.json`.

Replace the previous Metallum JAR in the existing duplicate test instance's `mods` directory with this candidate. Keep exactly one Metallum JAR, Minecraft 26.3, compatible Fabric/Sodium, and Iris disabled. No installer has modified Prism or a world.

Controls: F6 effects ON/OFF (OFF on startup), F10 material diagnostic, F7 atlas audit, F8 depth/directions, F9 benchmark. F6/F10 now show their state in chat. No new key or quality setting is needed.

## Visuals and lifecycle, one short recording

Use representative blocks together: stone/cobblestone, planks, iron/gold, sea lantern/glowstone/shroomlight. The 48 catalog mappings are unchanged. Armor, items, diamond blocks, ores and unmapped/complex geometry do not gain new material coverage.

1. Hold one camera pose; show F6 OFF, then ON after a second. Show metal reflections and rough surfaces together. Briefly show F10, then turn it OFF.
2. In shade, view a mapped emitter beside stone/planks. With F6 ON, turn so the source leaves the screen while the receiver stays visible: nearby light should remain. Put an opaque blocker between source and receiver if convenient; its local-light contribution should stop. Native tests establish this algorithmically, but actual in-game appearance is still pending.
3. Place/break a nearby block and check its material/light updates. Move across the cache boundary; inspect glass/water foreground and thin edges for spill or flicker. Effects can fall back while a new cache fills, and conservative voxel blockers can over-occlude partial geometry.
4. Toggle OFF, resize, reload resources, and leave/rejoin. Confirm ordinary hand/HUD and original rendering with effects OFF. Record failures and send the game log if any occur; logs can confirm the version and mode changes.

## Performance, same session

F8/F10 OFF; AFK limiting disabled manually. Keep the same accepted 1440×900 baseline pose, render/simulation distance eight, and graphics settings. Run F9 once with F6 OFF, then once with F6 ON. Each existing F9 run takes about 4.5 minutes (three rounds of 30 seconds warmup + 60 seconds measurement). Do not toggle or move during a run. Send the two `metallum-benchmarks/<session>/report.json` files with their `frames.csv` files. Record any stutter during the separate movement check; stationary runs do not establish moving-scene performance.

## What is already checked locally

Java build/types, standalone math/catalog/grid/light selection checks, native Metal shader and rendering fixtures, isolated 1440×900 M1 GPU timing, diff/shell/package checks, and independent ECC review. No new Minecraft gameplay is claimed. Gradle tests are NO-SOURCE; standalone linter/coverage are not configured.

## Rollback

F6 OFF immediately restores the existing rendering path. To revert the candidate completely, close Minecraft and replace visuals.2 with your previously installed visuals.1 JAR. The source-matched rebuilt fallback is also in `validation/checkpoints/metallum-0.0.25-visuals.1.jar`; its checksum differs from the originally tested binary and is documented in `visuals.2-build.json`. Never install both.
