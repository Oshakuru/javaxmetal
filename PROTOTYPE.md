# Metallum graphics prototype: current state and development plan

Reviewed with ECC on November 12, 2026.

## Goal and scope

User-confirmed target: a Vanilla RTX-style, vanilla-faithful material and lighting overhaul for Minecraft Java on Apple Silicon through Metal, covering the full vanilla block set over time. Reference: https://github.com/Vanilla-RTX/Vanilla-RTX . Ice was an early flat-surface diagnostic fixture, not the product scope. The user explicitly corrected the excessive ice-first focus on November 12, 2026.

Two coupled deliverables are required: (1) a resource-pack/material pipeline for surface properties across block families; (2) a renderer that responds to those properties with appropriate lighting, shadows, reflection, transparency, and atmosphere. Vanilla RTX is a Bedrock PBR resource pack, not the ray-tracing renderer itself. Java/Metal needs its own compatible resource mapping and rendering implementation. Reusing its actual assets is a separate decision from matching its appearance; inspect and preserve applicable attribution/distribution requirements before doing so. No Vanilla RTX assets have been imported.

Authoritative product direction reaffirmed by the user: **a Minecraft Java renderer using native Metal on Apple Silicon**, with Metallum as the backend foundation. The official project repository is https://github.com/ernestovegaMD/javaxmetal; `metallum/` is its renderer source directory. Target Apple Silicon generally, retaining the measured M1 baseline rather than assuming newer-chip capabilities or performance.

Two explicit inspiration repositories:
- https://github.com/NVIDIAGameWorks/rtx-remix-minecraft — its README describes a Minecraft Java 1.21.1 Fabric path-tracing bridge to NVIDIA RTX Remix, scene-wide materials/lighting, LabPBR inputs, exposure/settings and reproducible camera paths. Its stated runtime requirements are Windows and GeForce RTX hardware. Study renderer/scene/material architecture and visual behavior; implement the relevant equivalents through Java + Metal rather than adopting its NVIDIA runtime as our backend.
- https://github.com/Vanilla-RTX/Vanilla-RTX-App — its README describes the Windows/Bedrock companion application's pack installation/conversion/tuning, previews, shader management and reversible setup. Use it as inspiration for material-authoring and eventual product/setup experience. It is distinct from the Vanilla RTX material-pack repository and is not the rendering backend. Companion UI follows functioning renderer/material support.

Scope of this reaffirmation: read both repository READMEs, not a complete code/license audit. No code or assets imported. Any actual reuse requires source-level suitability and applicable license/attribution review.

The existing screen-space and bounded voxel-light candidates are engineering prototypes toward this renderer, not the finished product architecture or a claim of RTX/path-tracing parity. Future larger renderer milestones must establish reliable scene geometry and per-surface material data for lighting/shadows, indirect illumination and transparent surfaces; do not let isolated post-processing effects replace that end goal. Preserve the working fallback and local-vs-target evidence separation during that transition.

Keep Java + Metallum as the implementation route and M1 as the measured baseline. The completed depth/camera diagnostics are foundational work for this whole pipeline. Future diagnostics should validate representative material families together, rather than making ice the only visible deliverable. No promise of path-tracing parity or performance without target measurements.

## Visual references supplied by the user

The user identified these as the intended visual direction on November 12, 2026:

1. [Minecraft RTX - RTX On/Off Gameplay — NVIDIA GeForce](https://www.youtube.com/watch?v=AdTxrggo8e8). Sampled imagery around 0:05 and 0:34: strong light/shadow separation, sunlight entering a shaded space, atmospheric light shafts, and recognizable Minecraft geometry/textures.
2. [Minecraft Modding Comes to RTX Remix — NVIDIA GeForce](https://www.youtube.com/watch?v=wB7eDz3qa7U). Sampled imagery around 0:02 and 0:33: low-angle sunlight and long outdoor shadows; warm localized fire lighting in a dark interior; varied surface response. Page description identifies Java 1.21.1, RTX path tracing, and DLSS.
3. [I Tried NVIDIA’s Official RTX in Minecraft Java (It’s Weird) — nkin](https://www.youtube.com/watch?v=VMTve5JacO0). Sampled gameplay around 6:29; description discusses PBR textures, stained-glass colored light, Nether/End, and practical compatibility/performance testing. Treat description claims as reported content, not independent verification of implementations or performance.

Review scope: page titles/descriptions plus sampled frames, not an exhaustive frame-by-frame viewing. No downloaded/repackaged video media. Exact priority scenes can be refined with user timestamps later.

Interpretation for development: the ultimate target is a coherent lighting/material overhaul that retains Minecraft's block identity, rather than ice reflections alone. Candidate visual pillars are directional sunlight and shadows, soft indirect illumination, warm/colored emissive light, material roughness/reflectivity, and atmospheric depth. Their final priority and feasibility remain to be established. Reflections are the first bounded engineering milestone, not the complete visual goal.

Do not equate a screen-space reflection prototype with the path-traced lighting in these references. The longer roadmap must evaluate shadows, material data, indirect/emissive lighting, exposure/tone mapping, and atmosphere separately. Preserve M1 as the measured baseline; matching full reference quality/performance on M1 is not yet established. The references guide appearance, not a commitment to NVIDIA APIs, DLSS, a specific renderer, or an untested performance claim.

## Reproducible baseline

- Source: https://github.com/kokodio/metallum
- Upstream revision: 467d312b8bc68ee3210697f01855244e8f2831a2; Metallum 0.0.25; branch prototype/depth-diagnostic.
- Build configuration: Minecraft 26.3, Fabric Loader 0.19.5, Java 25, metal-jvm 0.1.0, Sodium 0.9.2.
- User's tested environment: Apple M1, macOS 27.2.1, Prism 11.1.1, Minecraft 26.3, Fabric Loader 0.20.1, Sodium 0.9.3-beta.1, Iris disabled. Build/runtime dependency differences must be recorded until deliberately aligned and tested.
- `sh build-metallum.sh` builds; `sh validation/verify.sh` builds offline, checks whitespace, and runs the standalone numerical check.
- JDK and Gradle cache live in this workspace. JARs are in metallum/build/libs; install exactly one Metallum JAR in the duplicate test instance.

## Evidence ledger

| Artifact | Local evidence | Target evidence | Limits |
|---|---|---|---|
| Unchanged 0.0.25 build | Compilation and packaging passed | User explicitly confirmed replacement; screenshot shows Metal gameplay, 623 FPS at 1440x900 | Single scene; not a stability/performance benchmark |
| depth.1 | Compilation and packaging passed | Orange divider and black depth half | Target failure; forward-depth display and final-buffer capture were wrong |
| depth.2 | Compilation and packaging passed | User screenshot shows terrain/cloud depth and distance gradient | Depth visibility verified; lifecycle and exact synchronization not exhaustively tested |
| normals.1 | Compilation and numerical checks passed | No target screenshot yet | Known camera-transform mismatch; not accepted for reflection work |

Earlier Metal/OpenGL FPS screenshots used different camera views, and some used different pixel densities. They support feasibility but do not establish a controlled speedup or reflection budget.

## ECC workflow assessment

### What worked

- An unchanged local build established a runnable foundation before feature work.
- Opt-in diagnostics isolated depth acquisition and visualization errors.
- Unique JAR suffixes and user screenshots distinguished the tested builds.
- The second depth build preserved world depth before later clears and used reversed-Z correctly.

### What needs to change

1. Review integration points before delivery. normals.1 uses camera.projectionMatrix, while GameRenderer.renderLevel applies bobbing, hurt, and nausea transforms before uploading the actual projection. Capture the effective rendered transform with the depth snapshot. The independent reviewer confirmed this from local 26.3 bytecode.
2. Test production behavior, not only self-consistency. The standalone numerical test round-trips its own matrix; it cannot establish that the renderer supplies the correct matrix, frame, or texture orientation.
3. Bundle target checks. Perform implementation, numerical/regression checks, diff review, and one independent review before handing over a milestone JAR. One consolidated checklist per candidate replaces repeated minor installation requests.
4. Separate the effect from presentation. The existing final fullscreen pass is suitable for debug visualization; its color input includes HUD/hand content. Reflection inputs/compositing need an explicit world-render boundary before HUD/hand drawing.
5. Treat material identification as a feasibility gate. Depth-derived normals cannot identify ice. Locate a reliable block/material signal in the terrain path, including Sodium; do not infer ice from blue pixels or an upward-facing normal.
6. Use measured performance. Preserve resolution/pixel density, camera pose, render distance, world/time, mod versions, warm-up, and frame cap. Collect repeated frame-time samples and tail behavior; screenshots of instantaneous FPS are not enough.
7. Keep evidence and code reproducible. Record source revision/diff, JAR SHA-256, dependencies, and test status for each candidate; checkpoint accepted milestones in version control. All new source and workflow files must be included when checkpointing. Do not package local JDK/cache directories.

## Development workflow for each milestone

1. Define one observable outcome and its acceptance checks below.
2. Inspect the actual Minecraft/Sodium call sites and frame ordering before implementation; document assumptions.
3. Add a failing regression for a known bug where feasible. Use numeric tests for projection/UV/reconstruction, runtime Metal checks for shader compilation, and target checks for rendering/lifecycle behavior.
4. Implement a bounded vertical slice with diagnostics, resource cleanup, and an off switch.
5. Run ECC verification phases with project tools. Java build/type checks and numerical checks are available; standalone lint and coverage are not configured. Gradle test NO-SOURCE is not a test suite. Review both tracked diffs and new source files.
6. Request one independent correctness review; fix substantiated issues before delivery. Review GPU lifetime, synchronization, resize/reload, frame consistency, and disabled overhead.
7. Deliver one clearly versioned candidate, checksum, replacement instructions, and a consolidated target checklist. Record pass/fail evidence separately from local checks.
8. Only after acceptance, checkpoint the milestone and advance.

## Revised product milestones — authoritative current direction

This sequence supersedes the earlier ice-specific milestones retained below for context.

1. **Whole-scene material foundation.** Establish a Java resource-pack contract and reliable per-surface material lookup through vanilla/Sodium terrain paths. Support base color, roughness, metalness, emission, and material normal maps, with height/depth information optional by quality tier. Geometric normals already reconstructed from depth are not texture/material normal maps. Build one mixed-material test scene: stone/wood, metal blocks, emissive blocks, glass, water/ice, and foliage. Add debug modes for material identity/properties. Trace which properties are actually available and define sensible fallbacks for unassigned blocks.
2. **First coherent lighting/material slice.** Render representative rough, metallic, and emissive blocks with consistent material shading; add directional sunlight/shadows and controlled exposure/tone mapping. Validate surfaces together in daylight and a lit interior. Emission display and actual illumination of nearby blocks are separate acceptance checks; colored emissive lighting must be implemented and measured, not implied by bloom.
3. **Reflection, transparency, and atmosphere across materials.** Add material-driven reflection for metals and smooth surfaces, water/glass handling, and fog/atmospheric response. Begin with bounded techniques appropriate to M1. Keep world effects before HUD/hand composition. Define and document screen-space limits and offscreen fallbacks.
4. **Indirect lighting and coverage expansion.** Evaluate practical indirect/colored-light methods and expand the material mapping systematically across the vanilla block set, retaining artistic consistency between related materials. Track unassigned blocks/unsupported states explicitly; do not claim full coverage from a showcase scene. Evaluate ray tracing as one possible method, rather than equating the visual reference with a specific required API.
5. **Optimization and usable release.** Compare matched effect-OFF/effect-ON scenes and moving routes against the established baseline. Fix artifacts, resource lifecycle, compatibility, quality presets, and sustained frame-time behavior before expanding scope further. Companion management UI follows functioning graphics/material support.

Immediate engineering task: audit world material inputs, Java texture/resource mapping, and the pre-HUD rendering boundary; produce a concrete multi-material implementation slice. Retain the camera diagnostic candidate and recorded movement evidence, and complete additional targeted transform checks only where they affect this work. Do not request more ice-only test iterations as the main product progression.

## Earlier diagnostic milestone sequence (superseded)

## Milestone 1 — Correct scene reconstruction

Outcome: directions reconstructed from the same rendered camera and world depth remain correct during ordinary camera effects.

Implementation:
- Capture the effective world projection at its actual upload/use site, rather than the unmodified camera state. Associate the effective view rotation and inverse transform with that frame's depth snapshot.
- Extract reconstruction math and uniform packing into a focused, testable boundary instead of expanding generic MetalUtilities indefinitely.
- Keep Off/Depth/Normals modes, explicit unavailable-data indication, and startup disabled state. Avoid silent stale data and new per-frame heap allocation where practical.
- Retain the successful before-clear snapshot; verify its frame/order/resource contract when moving hooks.

Local acceptance:
- Regression distinguishes raw from bobbing/hurt/nausea-modified projection and verifies the selected effective matrix.
- Projection round-trip, signed world-normal orientation, framebuffer Y convention, and edge/invalid-depth behavior tested against known geometry.
- Build and independent review pass. New shader compilation is verified at runtime before claiming it passes; if unavailable locally, mark it pending explicitly.

One target session:
- Flat ground and vertical walls show consistent expected directions while turning, walking, taking damage, and changing FOV; nausea if practical.
- Depth stays aligned; sky/invalid samples are recognizable; switch modes, resize, leave/rejoin a world, and toggle off without errors.

## Milestone 2 — World inputs and ice mask

Outcome: a reliable debug mask marks only the intended ice surfaces, with matching world color/depth and no interface content.

Implementation:
- Establish a named world-render integration boundary before hand/HUD composition. Document pass order and texture ownership.
- Capture/sample scene color and depth at that boundary with the same camera metadata.
- Investigate terrain material/block attributes through Sodium; select an ice-only mask path. Preserve vanilla fallback when unsupported. If the required signal is unavailable, resolve the architecture before promising a reflection candidate.
- Define which ice blocks are supported first and how transparency/depth affects them. Defer full material/PBR pack compatibility.

Acceptance:
- Mixed ice, snow, dirt, water, and walls demonstrate correct inclusion/exclusion. HUD/hand absent from effect inputs.
- World changes, resize, resource reload, and mask toggle show correct lifetime and cleanup.
- Review new render targets and terrain compatibility before SSR work.

## Milestone 3 — First visible ice reflections

Outcome: opt-in reflections appear only on supported ice; the unchanged path remains usable.

Implementation:
- Implement bounded screen-space tracing using verified transforms, scene depth/color, geometric normals, and ice mask.
- Add thickness tolerance, rejection of invalid/self hits, edge/distance fading, and an explicit fallback when reflected information is offscreen.
- Begin with a fixed ray-step budget and one quality preset; improve resolution/temporal behavior only after measurements show a need.
- Composite before the hand/HUD. Keep diagnostic modes available in the same candidate.

Acceptance:
- Known objects reflect on ice while snow/dirt remain unchanged; no HUD reflections or obvious screen-edge smearing.
- Camera motion and offscreen cases fail gracefully; toggle/resize/reload pass.
- Record repeated frame-time measurements with effect off/on at 1440x900 and fixed scene/settings; assess 60 Hz playability on the M1 using measured tail behavior. No guaranteed performance target before data.

## Later decisions

Only after the first reflection milestone: optimize the measured bottleneck, broaden materials, add lighting/shadows, or investigate ray tracing. Nvidium is an architectural reference for terrain submission/culling, not a prerequisite or automatic dependency. A companion app follows a useful, stable graphics capability.

## Verification status and integration provenance

ECC 2.2.3 snapshot d2ef2680594f9a17526a6e1a2e18b3d242c5d17b supplies the vendored verification-loop and java-coding-standards skills. Generic/Java reviewer prompts and MIT license are in metallum/.agents/ecc. Project AGENTS.md files adapt the workflows to this renderer; no global hooks, MCP servers, or model configuration were changed.

Last executed checks (ECC integration): Gradle build/type checks PASS; diff whitespace PASS; numerical projection/world-normal checks PASS at three rotations; no configured linter/coverage; Gradle tests NO-SOURCE. Limited Java/build-source secret/process-execution pattern scan found no matches; it was not a dependency/security audit. This workflow review changes documentation only, so builds were not repeated.

Immediate next implementation: Milestone 1's effective camera transform capture and regression coverage. normals.1 is an experimental candidate with a known limitation, not the accepted baseline for reflections.

## Automated baseline benchmark candidate — benchmark.1

User requested a reliable test run and agreed to automatic measurement rather than screen recording. F9 starts/cancels three rounds, each with 30 seconds warmup and at least 60 seconds of frame intervals. F8 diagnostics must remain off. Samples measure renderFrame HEAD-to-HEAD wall-clock intervals (including pacing/stalls), not GPU execution time or physical presentation latency. Arrays are preallocated; statistics/output happen after recording. Camera changes, monitored graphics setting changes, resize, pause/menu/overlay, focus loss, diagnostic activation, and world changes invalidate the run and preserve partial results marked INVALID.

Report: game folder/metallum-benchmarks/<session>/report.json; raw frames.csv beside it. Records nearest-rank median/p95/p99, mean/worst time, time-derived average FPS, slow-frame counts, actual framebuffer dimensions, camera pose, six monitored settings, mod versions, device/runtime, dimension/weather, saved-options hash, and self-JAR SHA-256. A saved-options hash is not a full live settings/resource-pack snapshot; keep those fixed manually. World identity/time and thermal state are not enforced by the recorder.

Build, Java types, numeric projection checks, new frame-statistic checks, and diff whitespace passed. Narrow secret/process-execution scan had no matches. Independent review found no timing/state-machine blocker. JAR/source fingerprints and scope of verification are in validation/benchmark-build.json. In-game F9/mixin/report creation remain NOT PERFORMED until the user runs the candidate. This build does not fix the preexisting normals camera-transform issue; it measures diagnostic-OFF baseline only.

Run protocol: validation/BENCHMARK.md. No user's Prism files/worlds were modified.

## Accepted stationary baseline — November 12, 2026

Session 2026-11-12T18-30-51.621911Z-fae9fbdc, benchmark.1, exact expected JAR SHA-256 b2dc080e7c3648705373f4fceef4c16a20af0978bb490c2cf78e59ac8305739f. User disabled AFK limiting manually. Three complete rounds; each raw CSV count, mean, and p99 recomputed and matched the JSON report. All rounds measured at least 60 seconds. Same camera pose as earlier runs; Metal Apple M1, 1440x900, render/simulation distance 8, FOV 110, VSync off, fullscreen. Reported max-FPS option remains 260 (raw option value; observed throughput is higher).

| Round | Average FPS | Median ms | p95 ms | p99 ms | Worst ms | Intervals above 16.67 ms |
|---|---:|---:|---:|---:|---:|---:|
| 1 | 583.2 | 1.705 | 2.575 | 3.402 | 18.968 | 1 |
| 2 | 592.9 | 1.647 | 2.560 | 3.396 | 31.538 | 1 |
| 3 | 595.2 | 1.641 | 2.519 | 3.321 | 29.148 | 1 |

Aggregate: 106,281 intervals in 180.003 measured seconds, 590.4 time-weighted average FPS. Three intervals exceeded 16.67 ms; none exceeded 33.33 ms. Ten-second segments show no recurrence of the sustained 30 FPS cap and no progressive slowdown in this session. This is a useful stationary baseline, not an isolated GPU budget or a guarantee for future effects. Raw report, CSV, and analysis preserved under validation/results/2026-11-12T18-30-51.621911Z-fae9fbdc/.

Earlier sessions 18:13:25 and 18:17:47 are not accepted performance baselines: first was interrupted; second completed but was contaminated by AFK throttling. Their first-round ten-second windows fell from roughly 623–632 FPS to 30 FPS around 30 seconds into measurement, consistent with the 60-second inactivity threshold after the 30-second warmup.

Next development checkpoint remains effective camera projection capture and reconstruction validation. Reuse the accepted stationary scene and automatic benchmark when comparing later effect-OFF/effect-ON candidates. Do not ask for another unchanged baseline run.

## Effective camera capture candidate — normals.2

Captures the effective projection via a read-only ModifyArg observer of the unique ProjectionMatrixBuffer.getBuffer(Matrix4f) call in GameRenderer.renderLevel, after bob/hurt/nausea effects. Returns the same matrix object unchanged. Pairs it with the same CameraRenderState.viewRotationMatrix used by LevelRenderer. A render HEAD hook invalidates camera state each frame. CameraSnapshot owns its copied inverse, rejects a different world/size or nonfinite inverse, and supplies a copied transform with the preserved depth snapshot. Missing transforms remain visibly magenta in normals mode.

Replaced the per-present float[16] allocation with a native stack buffer and shared, tested column-major packing. F8 remains Off → Depth → Surface directions → Off. Benchmark recorder retained; the unissued benchmark.2 idle-timer refresh was removed to honor the user's preference. F9 now refuses to start with AFK limiting enabled and invalidates unexpected throttle states, without changing preferences or simulating activity. No rerun of the accepted baseline is requested for this visual milestone.

Local build/type, diff whitespace, existing projection/statistic checks, and new production CameraSnapshot regression passed. The new fixture exposes the old raw-camera mismatch and checks effective-projection reconstruction, upward normals, UV contract, copied matrix ownership, world/size rejection, frame invalidation, singular-matrix rejection, and Metal matrix packing. The first test harness used a heap FloatBuffer with JOML's unsafe native constructor and crashed; the harness was corrected to use a native-order direct buffer, matching the production stack buffer. Production code was not implicated by that harness failure. No standalone coverage/linter configured. Independent ECC review found no substantiated blocker and verified the exact bytecode hook/view pairing and native buffer lifetime.

Artifact fingerprint: validation/normals.2-build.json. Target Mixin application and in-game surface directions remain NOT PERFORMED. Next user check: same duplicate instance, replace the prior JAR, Iris disabled, F8 twice for normals; flat ground light green and vertical walls consistent distinct colors while turning/walking; resize and leave/rejoin world; optionally test hurt/nausea. A short video is useful for this visual-only check.

## Rust/Horizon alternative assessment — November 12, 2026

Read-only source review of tr4m0ryp/Minecraft-rust at 2381ba865fee7292e263794c827fe694d4851bdf. No build, launcher execution, account actions, or world access performed. Separate clone is minecraft-rust-review/. Original Metallum source unchanged by this assessment.

The linked project is a fork of horizon-rust/horizon, an independent Rust reimplementation of Java Edition 26.3, not a Fabric optimization mod. Its client-render Cargo manifest enables wgpu's Metal backend on macOS. Code inspection confirms native sky/material/post stages are connected to the renderer (gfx/native_pack.rs, native_material.rs, native_post.rs), with same-frame camera uniforms and world color/depth resources. gfx/flat.rs explicitly addresses Metal's emulated indirect multidraw by compute-flattening indices into page runs. These are credible graphics architecture advantages over having to introduce all equivalent hooks into a Java backend. They do not establish RTX/path tracing or a ready replacement for the full visual target.

Compatibility cost: the project's own README says Java mod JARs do not run, and its port ledger reports player-visible gaps despite passing differential harnesses. Test parity is scoped to tested scenarios. docs/api/shaders.md has stale opening claims ('no shader packs today') while later entries and source show working stages; treat code/current release evidence separately from roadmap wording. The linked fork disables REQUIRE_OWNER in launcher/src/account.rs and routes launches offline. This is a launcher/auth change, not an Apple Silicon rendering optimization. Consider canonical upstream rather than the fork for future runtime evaluation.

Published perf reports inspected under benchmarks/perf compare Horizon with vanilla and Sodium/Lithium/FerriteCore/ImmediatelyFast on Windows with Ryzen 9800X3D and RTX 5080, not Metallum on M1. No M1 head-to-head validation was executed here. Rust alone does not establish better GPU effects performance; renderer architecture, scene workload, and target measurements determine the outcome.

Decision: retain Java + Metallum as the main route for the next accepted reconstruction/ice-reflection milestone. Preserve the measured M1 baseline. Horizon is a strong candidate for a separately scoped upstream evaluation if Java/Sodium material hooks become disproportionately costly or the user prioritizes a custom native client over Java mod compatibility. A switch requires an isolated scratch-world test on this M1 with identical actual resolution/settings/scene, gameplay/visual acceptance, frame-time and memory measurements, and proof that a material-targeted world effect can be implemented more directly. Do not redirect based solely on raw FPS or headline parity claims.

Primary sources: https://github.com/tr4m0ryp/Minecraft-rust ; https://github.com/horizon-rust/horizon ; fork source paths crates/horizon-client-render/src/gfx/{native_pack,native_material,native_post,flat}.rs ; docs/benchmark.md ; benchmarks/perf/windows-9800x3d-5080-2026-10-29.md ; launcher/src/account.rs.

## Surface-direction target recording review — November 12, 2026

User supplied a 70.362-second H.264 recording (2880x1800 encoded display), in the context of the normals.2 test. Reviewed 16 evenly spaced extracted frames at 0–66 seconds plus enlarged frames at ~44 and ~66 seconds. Evidence images retained in validation/video-review/; original video remains at the user-provided temporary path, not copied into the repository. AVFoundation extraction initially failed in restricted execution; authorized use of the macOS decoder succeeded.

Observed: split diagnostic draws throughout sampled views; upward ground/ice/snow surfaces retain light green while position/view changes; vertical faces retain distinct axis colors; empty sky is black and cloud undersides have downward-facing purple. No uniform missing-transform magenta field or obvious whole-scene color drift appears in sampled frames. Purple cloud geometry is a normal-direction color, not the uniform magenta missing-data indicator. Small edge/vegetation/distant-geometry reconstruction artifacts remain visible, as expected for depth-derived normals.

Acceptance scope: ordinary movement/turning looks consistent in sampled frames, supporting the camera reconstruction milestone. This was sampled-frame review, not exhaustive temporal/flicker analysis or performance measurement. Video does not independently establish the exact installed JAR hash, hurt/nausea behavior, FOV changes, resize, or world-reload checks. Do not claim those passed. The next engineering task is reliable ice material identification and world color/depth integration before hand/HUD composition. No renderer source or JAR changed during this review.

## Vanilla RTX scope correction — November 12, 2026

Inspected the reference repository README and license. It provides vanilla-faithful PBR materials across blocks, in heightmap/normal-map variants, for Bedrock's renderer. The earlier plan overemphasized one reflection fixture. Product plan above now covers materials across block families and the lighting/rendering necessary to make them respond. Existing camera/depth/benchmark work remains useful. No new JAR or renderer code changed in this scope correction.

## Material foundation candidate — materials.1

Audited actual Minecraft 26.3 model/atlas APIs and Sodium 0.9.2 fragment inputs. BakedQuad.MaterialInfo exposes each face's TextureAtlasSprite. Loaded atlas sprites expose identifiers and UV bounds. Sodium uses u_BlockTex/v_TexCoord; its chunk_material flags control filtering/cutout, not PBR properties. Existing GPU inputs therefore do not provide roughness/metalness/emission by themselves. No inference from surface color or geometric normal should substitute for this material data.

Implemented immutable MaterialCatalog with schema1, roughness/metalness/transmission in [0,1], emission in [0,64], exact texture identifiers, explicit unassigned fallback, and bounded validated resource input. Default resource assets/metallum/materials/default.json has 48 representative texture mappings across stone/wood/metal/glass/water/ice/emissive/foliage. Values are authored prototype scalars, not imported Vanilla RTX assets or artist-verified complete vanilla coverage. Snow/dirt currently share the rough stone profile in this initial fixture; split them into dedicated categories before treating category names as finished art direction. Texture normal/height/PBR maps are not implemented.

F7 performs an on-demand CPU audit using the active resource manager: a higher-priority pack can replace metallum:materials/default.json. It exports actual atlas sprite UVs/animation flags, scalar definitions, mapped/unassigned coverage by sprite, timestamp/source-pack provenance, and targeted block-face texture candidates. Full-face quads and unculled matching-direction quads are considered; particle-material fallback is explicitly labeled. Fluids/complex models and exact per-fragment identity are not solved by this inspection. It exports to the test instance Minecraft folder/metallum-materials/latest.json and prints a summary in chat. No work per frame and no shader paths changed.

The world-only effect boundary is identified in local bytecode: GameRenderer.renderLevel calls LevelRenderer.render, then render3dHud. Future world capture/composition should integrate before render3dHud, not at final presentation. Next implementation after the material audit: GPU-readable catalog/atlas material lookup and a terrain diagnostic for multiple families, then material shading. That bridge still needs to handle Sodium, translucent geometry, reload and resource lifetimes. This candidate does not claim rendered PBR lighting or material-driven reflection.

Build/types/diff and all standalone checks passed. Catalog tests cover categories/fallback, strict schema version, identifiers, nonnumeric/nonfinite/out-of-range properties, and undefined profile references. Independent ECC review found no blocker; schema coercion and missing report provenance were corrected and rechecked. In-game accessor/override/face-audit remains pending. Fingerprint: validation/materials.1-build.json.

Consolidated target check: replace previous JAR in duplicate instance, Iris off, enter world and press F7 once while targeting a block; send metallum-materials/latest.json. Optionally inspect stone, planks, gold/iron, glass and glowstone together. No benchmark rerun or apparent lighting change expected from this data-layer candidate.

## Material atlas target verification — November 12, 2026

User supplied material report captured at 2026-11-12T19:15:19.994814Z. Catalog loaded from pack `metallum`; actual atlas 4096x2048, 1,427 sprites. All 48 catalog texture identifiers are present; every sprite UV rectangle is ordered and within [0,1]; coverage counts recomputed from sprite entries match the report. Categories: stone13, wood12, metal4, glass4, water2, ice3, emissive5, foliage5; 1,379 sprites remain unassigned. These are sprite counts, not block-coverage percentages.

Targeted sea_lantern UP face resolved directly from model quads (particleFallback=false) to minecraft:block/sea_lantern and emissive profile (roughness0.65, metalness0, emission2, transmission0). Representative stone/planks/gold/glass/glowstone/water/ice/leaves mappings match the default catalog. Animated water flagged correctly in the report. Evidence saved at validation/results/materials.1-2026-11-12/atlas-audit.json.

Acceptance: resource/catalog loading, loaded-atlas accessor and this targeted model-face resolution are VERIFIED. Resource-pack override priority/reload, arbitrary complex models, fluid exact-hit mapping and GPU fragment binding remain unverified/unimplemented. Next task: GPU-readable atlas material lookup and terrain material diagnostic across the representative families before actual PBR shading. No additional audit rerun required for the unchanged catalog.

## Integrated mixed-material prototype — original implementation scope

User explicitly requested larger milestones. Next handoff must combine actual surface-property-driven shading with supporting diagnostics, local regressions, and independent review. No separate material-color-only handoff.

Bounded implementation: opt-in pre-hand/HUD world post pass with verified camera/depth, a near-camera voxel/face material cache resolved from actual model sprites and the catalog, material-dependent rough/specular response, bounded screen-space reflections for smooth/metals, emissive appearance with a limited screen-space glow/nearby light approximation. Rough/metal/emissive families must be visually distinct in one scene. Preserve off switch, unknown-material fallback, F8 diagnostics, F9 benchmark. A bundled material-color mode can diagnose mapping inside the same candidate.

This is a hybrid prototype, not full path tracing: no claim of complete block coverage, robust transparency/refraction, shadowed indirect illumination, or offscreen reflection. Validate the nearby face/material lookup and copy/composite ordering carefully; fallback to unchanged shading outside valid cache/geometry. Cache building must be bounded on the render thread, reused on the GPU, rebuilt on world/resource changes, and released with the device. Synthetic native Metal compilation/render checks are preferred before the user's target test. F7 remains optional, not another required milestone.

## Integrated mixed-material candidate — visuals.1

Implemented the first opt-in mixed-material slice. F6 toggles the visual pass (OFF at launch); F10 toggles its material coverage view (requires F6 ON). Existing F7 audit, F8 depth/surface directions, and F9 benchmark remain. No changes to Prism or worlds; no GitHub upload performed for this milestone.

The unique GameRenderer.renderLevel pre-render3dHud call site runs after LevelRenderer.render and before hand/3D HUD and subsequent 2D HUD. Capture the effective projection and camera position together at the existing projection-upload observer. Flush pending world color/depth clears, sample the original world textures, render to scratch color, then copy back. No read/write feedback on the same color texture; the hand/HUD never enter the reflection inputs. Scratch encoding always ends in finally; world copy occurs only after successful encoding. Existing global fences order producer → visual pass → copy → HUD. The original image remains the fallback if the effect cannot initialize.

Material binding uses actual model quad sprite identifiers, not scene color guessing. A 24×24×24 camera-local grid stores six float4 faces per cell (1,327,104 bytes), built in 768-cell render-thread batches. The first complete sweep takes 18 rendered world frames; later sweeps refresh the GPU with an immutable buffer and delayed release of the previous buffer. Recenter after more than four blocks from the cache center; effects fall back while a new grid fills. Cache origin remains in integer world coordinates and shader camera offsets stay small. Shader effects fade at cache edges. Resource atlas uploads, F6 changes, and world changes invalidate material data. Resize recreates scratch color; toggling off/world exit frees cache/scene resources; device close releases pipelines. No new per-frame native buffer allocations when the effect is off.

Only solid, collision-full cubes with exactly one complete, axis-aligned, unambiguous model quad per face and a mapped opaque material are eligible. No particle-texture guessing. Missing profiles, complex/partial shapes, slopes, transparent/fluid blocks and surfaces outside the cache retain original shading. Conservative 72-step camera-to-surface grid traversal rejects material binding through nonair unsupported cells, protecting blended glass/water views where depth can describe opaque terrain behind transparency. This also guards emissive taps. Camera-inside unsupported cells falls back. This is conservative and can suppress effects beside partial geometry. Rolling cache snapshots can lag block edits by one sweep; translucent/geometry changes during that interval can temporarily escape the guard. Coplanar entities/features are not a reliable identity source; this is a bounded geometric/material approximation rather than a true terrain G-buffer. Exact terrain attachment data is the upgrade if acceptance shows that limit matters.

Shading uses catalog roughness, metalness, and emission over Minecraft's already-lit world color: bounded rough/specular response, an artistic unshadowed highlight, 24-step screen-space reflections with five crossing-refinement steps, edge/distance confidence fading and original-image fallback for misses. Emissive surfaces brighten; eight visible screen-neighbor taps provide bounded glow and nearby-light approximation. Highlight compression avoids unbounded brightening. This does not implement sun shadows, physical relighting of raw albedo, normal/height textures, visibility-tested colored GI, offscreen reflections, temporal reconstruction, or glass/water/ice refraction. Emissive appearance, screen glow and actual illumination of neighboring surfaces remain distinct target observations; the local fixture only proves the limited approximation can affect another receiver.

Material diagnostic: assigned rough surfaces are cyan/blue according to roughness, metals gold, emissives orange; unsupported/unassigned surfaces show a dimmed original image. F8 remains the original split depth/direction view. The F9 report now records visual enable/material diagnostic state and invalidates visual-mode or resource-revision changes; F8/F10 diagnostic rendering must be off for benchmarks. A shader/encoding failure that disables F6 also invalidates an active effect-ON benchmark. Do not repeat the accepted unchanged baseline; compare the same pose and settings using this candidate with F6 OFF/ON.

Local verification and in-game acceptance are recorded separately below and in validation/visuals.1-build.json. The synthetic native Metal fixture uses the production shader source and runs on the Apple M1 without starting Minecraft. It does not establish that Minecraft applies the Mixins or that Metal-JVM runtime integration is correct.

### Candidate acceptance session (NOT PERFORMED)

Use the existing duplicate test instance (Minecraft 26.3, compatible Fabric/Sodium; Iris disabled). Remove the previous Metallum JAR from its mods directory and copy only metallum/build/libs/metallum-0.0.25-visuals.1.jar. Keep exactly one Metallum JAR. No install or world edit has been performed by the agent.

1. In an existing mixed scene, keep stone/cobblestone, planks, iron/gold/copper and sea lantern/glowstone within about eight blocks of the camera. Start with F6 OFF and confirm the usual image and unchanged hand/HUD. Press F6, wait about one second, and compare stone/wood roughness, metal response/reflections and emissive surfaces together. Reflections require visible scene geometry and can vanish when information leaves the screen.
2. F10 ON: check coverage/colors against those blocks, including top and side faces. Complex shapes, glass/water and unassigned textures should not acquire the opaque material behind them. F10 OFF: view a lit interior/emissive block beside rough surfaces; distinguish direct emitter brightness, nearby screen glow and any illumination of the receiver. Do not count bloom alone as colored-light acceptance.
3. Move/turn, walk across the cache boundary, place/break a test block manually if desired, view through glass/water, switch F6/F10/F8, resize, reload resources and leave/rejoin. Check logs for Mixins/Metal errors and record any fallback gaps or flicker. F6 OFF must return to the usual rendering.
4. With F8/F10 OFF and AFK limiting disabled manually, use F9 at the same accepted baseline pose/resolution/settings for separate F6 OFF and ON reports. Keep the effect state fixed during each run. Send both report.json files and frames.csv data plus representative visual evidence. Stationary benchmark numbers do not establish moving-scene performance or stability.

Pending target checks: exact installed JAR checksum; Mixin/runtime shader/binding integration; actual in-game material identification and visual quality; hand/HUD exclusion; transparency/cache transition behavior; toggles, resize, reload and world rejoin; stationary effect OFF/ON and moving-scene frame-time behavior. No new in-game acceptance is claimed.

### visuals.1 local verification result

PASS: offline build/Java types, tracked diff whitespace, projection/statistics/camera/catalog regressions, new grid addressing/face boundary checks, native Metal shader/pipeline/GPU fixtures on Apple M1, shell syntax, and JAR resource/version/class inspection. The GPU fixtures assert unchanged unknown/sky pixels and alpha/orientation, face binding, rough shading, direct emissive response, a known SSR wall hit and offscreen/missing-geometry miss, visibility blockers/camera-inside/axis/negative/diagonal cases, and an emissive wall increasing a separate ground receiver (red 91 → 95 in the synthetic fixture). The initial SSR fixture exposed coarse-step overshoot, corrected by five-step crossing refinement; an initial fixture-only depth attribute declaration was also corrected. No Minecraft game process or Prism/world files were accessed.

Independent ECC reviewer APPROVE after resolving transparency identity rejection and always-ending the native encoder with scratch-before-copy fallback. Narrow credential/process-execution scan found no matches; not a full security/dependency audit. Standalone linter/coverage not configured; Gradle tests NO-SOURCE. Target acceptance remains NOT PERFORMED, as listed above.

Artifact: metallum-0.0.25-visuals.1.jar; SHA-256 `8a5300b001cef22042ed8ef82918a172e231b06063a4190d94eab494e64de510`. Source fingerprint `61763bd2033532d7135d0403d53843d6d63d121a9e3953220394ad3b7781e292`; exact source file hashes, controls, limitations and verification scope are in validation/visuals.1-build.json. No source checkpoint or GitHub upload has been made for this unaccepted candidate.

## visuals.1 supplied recording review — November 12, 2026

User supplied a 162.052-second recording named Screen Recording 2026-11-12 at 2.53.45 p.m. Reviewed 16 evenly spaced extracted frames, plus 16 closer-spaced frames around requested 60–74 and 110–124 seconds. AVFoundation seeking is approximate; no exhaustive temporal/audio/flicker analysis performed. Contact sheets, extracted frames and scope are in validation/visuals.1-video-review/. Original 1.1 GB recording was not copied into the project.

Target visual observation VERIFIED: iron-block top shows recognizable sugar-cane reflections in multiple views around 62–71 seconds, including an enlarged frame near 70.9 seconds. This supports actual visible reflective material response in the submitted run. Sampled gameplay includes movement, block placement and menus; held item/hand, crosshair, hotbar and subtitles remain recognizable without obvious whole-interface recoloring. This does not establish all HUD/hand exclusion cases or sustained stability.

Not verified: exact installed JAR hash or mod/runtime configuration; a confirmed matched F6 OFF/ON pair; F10 material diagnostic (not seen in sampled frames); rough stone/wood separation; emitter brightness versus vanilla appearance or new nearby illumination; resize, reload, world rejoin, sustained flicker/cache behavior; performance. Daytime sea-lantern/lava/fire views are insufficient to accept colored emissive illumination. Gold/diamond views do not by themselves prove material mapping: diamond blocks, ore blocks, armor and held items are outside assigned terrain coverage in visuals.1. Do not infer metal/PBR coverage from their material names or appearance.

This recording establishes useful partial visual evidence, not full candidate acceptance. Next targeted evidence is a brief stationary F6 OFF/ON comparison with iron/gold and stone/planks plus F10 coverage, and an emitter beside a mapped receiver in shade. A game log can confirm toggles/runtime errors without a new build; F9 matched reports are still required separately for performance. No source or JAR changed during this review, no local build repeated for documentation/evidence-only work, no Prism/world modification, and no GitHub upload performed.

## visuals.1 second recording — material diagnostic evidence, November 12, 2026

Reviewed 16 evenly spaced frames from the supplied 37.168-second Screen Recording 2026-11-12 at 3.01.13 p.m. Approximate AVFoundation seeks; no exhaustive temporal analysis. Evidence is in validation/visuals.1-video-review-2/. Original video remains at the supplied Desktop path.

VERIFIED scoped observation: the material coverage view renders representative metal cubes yellow, rough mapped faces blue/cyan, log faces cyan and emissive faces orange (including the central sea-lantern fixture). Unassigned scene color is darkened while sky stays original, matching the intentionally dim diagnostic, not evidence of a lighting/exposure regression. Normal image returns in sampled views around 11.6–16.3 and 34.8 seconds. Video alone does not distinguish F10 OFF versus F6 OFF. Held iron block, hotbar, crosshair and subtitles retain ordinary rendering during these diagnostic views, supporting the pre-hand/HUD boundary for the sampled case. Coverage is representative, not an exhaustive per-face mapping audit.

Combined with the first recording, visible iron reflection and representative material diagnostic now have target visual evidence. Orange diagnostic identifies an emitter; it does not establish new emitted light. New roughness separation, direct emission versus vanilla brightness, nearby-light approximation, exact installed JAR/runtime configuration, resize/reload/rejoin and effect OFF/ON performance remain unverified. No need to repeat material-diagnostic footage for this unchanged candidate. Next evidence priorities are matched F9 effect OFF/ON reports and, separately, shaded emitter/receiver behavior if accepting emissive illumination. No new renderer source/JAR or build during this evidence-only review, no GitHub upload, no Prism/world changes.

## Coherent material/light bundle — visuals.2

User requested larger milestones with reversible internal changes and one consolidated target handoff. This candidate combines world-local colored emitters, conservative local-light occlusion, rough/metal BRDF response, reflection filtering, cache-edit correctness and measured GPU-cost reduction. Controls and the normal-renderer fallback are retained. Work remains local; no GitHub upload, Prism modification, launcher action or world edit was performed.

### Rollback and provenance

Before editing, every renderer source/build hash matched the visuals.1 manifest. Preserved exact sources and validation scripts in validation/checkpoints/visuals.1-source.zip (SHA-256 64161420ff9b1860a2374c511b9aa06d7acdeb841f6ebdb07b15211b5ec2bf7a). The previously delivered visuals.1 binary was no longer present in the workspace build directory; no Prism lookup was performed. Rebuilt the unchanged sources and saved validation/checkpoints/metallum-0.0.25-visuals.1.jar (SHA-256 5ab930b0568200981696be241930f9f939f2e13fdb110d5e6e730f382c7bf6ca). This is a source-matched rebuild, not the original tested 8a5300b0… binary. Existing user video evidence remains scoped to the submitted runs and does not newly accept the rebuilt fallback.

### Implemented behavior

- Select up to eight nearest emissive opaque cubes from the committed nearby material grid. Obtain each light's RGB from 8×8 samples of its actual model sprite's retained original image, converted ARGB→linear RGB and weighted by catalog emission. Cache sprite colors until resource reload. First animation frame is a deliberate approximation; no dynamic per-frame emission texture or new pack schema. The assigned texture catalog remains unchanged.
- Colored local light affects mapped receivers within six blocks of a selected source, including when the source leaves the screen but remains inside the nearby cache. Conservative voxel traversal blocks opaque, unsupported and unloaded cells, excluding the emitter destination. Partial shapes and transparent blocks are treated as full blockers; no transmission or soft shadows. This is additive direct local lighting over an estimated albedo from the already-lit scene, not raw-albedo relighting, colored GI or path tracing. Screen glow is presentation only and remains separately bounded.
- Use a bounded GGX-style specular/Fresnel and rough diffuse response for local lights and the small artistic unshadowed directional highlight. Preserve material-driven SSR and refine reflection filtering in two dimensions in linear color space. Highlight compression preserves hue. Sun shadows and a physical day/night lighting model are not implemented.
- Grid layout is now six material float4 faces plus RGB emission/occupancy metadata per cell (112 bytes), with an eight-light list appended. Two CPU buffers preserve the committed snapshot while a bounded 768-cell batch constructs the next. Dirty block cells patch both snapshots before publication; large dirty queues suppress the effect until processed. Chunk load/unload invalidates intersecting cells. Resource/world/recenter resets retain the existing fallback. Recenter can still cause a short visual gap while the 18-frame sweep fills.
- Reviewed actual Minecraft bytecode for block hooks and sprite lifetime/color layout. Independent review caught ClientLevel.hasChunk returning constant true; replaced it with getChunkSource().hasChunk, which performs the non-creating FULL-chunk lookup. Normal block updates reach setBlocksDirty, so edits need not wait a full sweep. Dynamic block entities/models without block-state changes remain bounded by the rolling refresh.
- Calculate normal effects at half width/height into signed RGBA16Float corrections; composite against the original full-resolution world image before HUD/hand. Keep F10 diagnostics at full resolution. Preserve original texture detail and reject corrections across depth/block/surface boundaries. Each receiving full-resolution pixel rechecks its own material/visibility, preventing a clear representative ray from spilling opaque effects across thin transparency. Unknown surfaces and sky remain original. Conservative rejection can leave narrow unshaded edges or lose thin reflection detail; target appearance must assess this tradeoff.
- Both GPU passes always end their encoder in finally and communicate through the existing global fence; world copy only follows successful scratch rendering. Off/world exit/resize/device cleanup includes the half buffer and all pipelines. GPU grids remain immutable per generation and use delayed native release. F6/F10 now report state in chat. Benchmark mode tracking and F8/F9 controls remain intact. No new dependencies or quality menus.

### Local results and performance scope

The full-resolution implementation's isolated mixed eight-light fixture cost about 13.1 ms GPU per command at 1440×900 on Apple M1. This justified a half-resolution effect calculation. With full-resolution target-visibility rejection included, the same synthetic fixture measured mean 4.700 ms, p95 4.746 ms for the optimized path, versus mean 13.127 ms/p95 13.213 ms full resolution. Mixed scene without local lights measured 3.240 ms/p95 3.267 ms optimized. Copy-only bandwidth reference measured 0.123 ms. Each scenario used ten warmup and thirty measured commands; raw samples, device, scope and source hashes are in validation/results/visuals.2/native-gpu-benchmark.json.

These are isolated production-shader-plus-scratch-copy GPU command timings on a synthetic ground/wall scene, not Minecraft FPS, frame times, thermal stability or effect-OFF gameplay. They exclude CPU material-cache construction, terrain, HUD and other passes. Actual game OFF/ON and moving-scene performance remain pending; do not combine these numbers with the stationary 590.4 FPS baseline to claim delivered FPS.

Independent ECC review found and resolved the unloaded-chunk query and half-composite target-transparency issues, then APPROVED the final static paths. Native regressions cover all original reflection/material fallbacks plus offscreen colored light affecting a separate receiver, solid occlusion, optimized-path lighting, intentional depth-discontinuity rejection, odd-size full-resolution composition and a target-only transparency blocker whose representative sample remains clear. The initial 65×65 optimized receiver fixture lay near the synthetic horizon and correctly failed the conservative depth threshold; split the test into a smooth-depth lit receiver and an explicit horizon-fallback assertion. A failed check was not counted as a pass.

### Single target handoff — NOT PERFORMED

Candidate metallum-0.0.25-visuals.2.jar and exact artifact/source fingerprints are recorded in validation/visuals.2-build.json. Consolidated instructions: validation/VISUALS.2.md. One acceptance session combines a stable OFF/ON material view, emitter/receiver in shade with offscreen-source and blocker observations, edits/geometry boundaries, resize/reload/rejoin, and matched F9 OFF/ON reports. No intermediate candidate was installed or requested during development.

Pending: exact installed JAR, new Mixins/SpriteImageAccessor runtime application, actual colored-light appearance and occlusion, rough/metal response, half-resolution artifacts, dynamic cache/light-selection transitions, lifecycle checks and combined Minecraft performance. Existing visuals.1 videos do not accept visuals.2. F6 remains OFF at startup; replacing the JAR and enabling effects are user target actions.

### visuals.2 final local verification

PASS: sh validation/verify.sh (offline build/Java types, diff hygiene, projection/statistics/camera/catalog/grid/light-selection checks and native Metal fixtures), shell syntax, milestone untracked source whitespace, narrow credential/process scan and packaged version/shader/classes. No configured standalone linter/coverage; Gradle tests NO-SOURCE. Final shader hash matches the native performance report. Independent ECC static review APPROVE after the two documented fixes. Additional error recovery clears partially initialized pipelines on failure so a future F6 enable can retry. No new gameplay acceptance or installed-JAR confirmation.

Candidate SHA-256 `df5946d5807fd63f25dc6b5226f7a593aa720ffda419c83e5d17d11d181f2d5b`; source fingerprint `d10627918f15933360d971f4b74b4d3f1acbf2f0304376bb7d6308d8320b0e97`. Details in validation/visuals.2-build.json.

## GitHub session checkpoint — November 12, 2026

This checkpoint publishes the chat's integrated visuals.1/visuals.2 development, current visuals.2 source and JAR, source-matched visuals.1 rollback, native checks/timings, both supplied-video review records and sampled evidence, consolidated acceptance instructions, and reaffirmed Java + native Metal/Apple Silicon direction with the two inspiration repositories. Build manifests retain their original build-time status fields; this later entry records publication. Large original recordings remain at the user-provided Desktop paths; no original videos, local toolchains or Gradle caches are uploaded.

Next session: start from visuals.2 and validation/VISUALS.2.md. Existing visuals.1 evidence shows iron reflections and representative material coverage; visuals.2 gameplay/lifecycle/performance acceptance remains pending. Assess that combined candidate without interpreting synthetic GPU timings as Minecraft FPS, then progress the larger renderer architecture with its fallback preserved. No new build or gameplay was performed just for publication: uploaded source and candidate JAR hashes were checked against the already-passed final manifest. No Prism/world modifications. Future GitHub uploads still require the user's instruction.
