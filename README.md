# Java × Metal

Minecraft Java + Metal visual overhaul for Apple Silicon, using Metallum as the foundation and Vanilla RTX as the visual target.

## Repository contents

This is a snapshot of the local project workspace: renderer source in `metallum/`, workflow instructions and ECC references, development plan, validation scripts and recorded evidence, project images, and candidate JARs in `artifacts/`. The separate `minecraft-rust-review/` source snapshot is retained as research reference, not the selected renderer. Original licenses and notices are preserved.

Local Java toolchains, Gradle caches, generated build directories, nested Git metadata, and JVM crash dumps are excluded. Build scripts expect Java 25 in `.tooling/jdk25/Contents/Home` and cached dependencies in `.gradle-local`; provision these locally before running the offline verification script.

Read `AGENTS.md` and `metallum/AGENTS.md` before renderer work. `PROTOTYPE.md` separates local verification from target acceptance and retains historical decisions superseded by later entries. Candidate binaries and their checksums are in `artifacts/`; `visuals.2` is the current candidate; historical builds remain available with scoped evidence.

## Workflow

Use ponytail in full mode: implement the smallest complete change. Read the relevant code and project instructions first. For substantial renderer changes, run `sh validation/verify.sh`, inspect tracked and untracked changes, and request one independent correctness review using the ECC references. Record local verification separately from in-game acceptance in `PROTOTYPE.md`. Gradle NO-SOURCE tasks are not an automated test suite.

Deliver versioned candidates with checksums, toggles, exact replacement instructions, and one consolidated target checklist. Keep `.tooling/`, `.gradle-local/`, credentials, and local runtime artifacts out of Git. Do not modify the user's Prism installation or worlds during routine development.

## Current milestone

`metallum-0.0.25-visuals.2.jar` in `artifacts/` combines material-driven rough/metal shading, screen-space reflections, nearby colored emissive lights with conservative block occlusion, and half-resolution corrections composed over full-resolution world textures before the hand/HUD. F6 enables effects (OFF at launch); F10 shows material coverage. F7/F8/F9 retain atlas audit, depth/direction diagnostics and benchmarking. Read `validation/VISUALS.2.md` for the single combined acceptance session and `validation/visuals.2-build.json` for checksums, source fingerprints and verification.

Local build, numeric regressions, native Metal GPU fixtures and independent ECC review passed. Isolated 1440×900 Apple M1 GPU timing for the synthetic mixed eight-light scene is approximately 4.7 ms for the optimized shader/composition/copy, versus 13.1 ms full resolution. This excludes Minecraft and CPU cache construction; it is not game FPS. Actual visuals.2 in-game appearance, new runtime hooks, lifecycle and OFF/ON performance remain pending. Earlier supplied visuals.1 recordings show iron reflections and representative material diagnostics, not full candidate acceptance.

The accepted stationary existing-renderer M1 baseline is 590.4 FPS at 1440×900 with render/simulation distance eight, before the visual effects. The 48 prototype atlas mappings were validated; these do not establish full block coverage. Geometry/material inputs, shadows, indirect illumination and convincing transparency remain on the larger renderer roadmap.

## Project direction and next session

Build a Minecraft Java renderer using native Metal on Apple Silicon, with Metallum as the foundation. [RTX Remix Minecraft](https://github.com/NVIDIAGameWorks/rtx-remix-minecraft) guides rendering/material/lighting goals; [Vanilla RTX App](https://github.com/Vanilla-RTX/Vanilla-RTX-App) guides material tooling and eventual setup/management. Current effects are intermediate prototypes, not full path tracing or a finished renderer.

Next session: assess visuals.2 using the consolidated checklist, keep target acceptance separate from local evidence, and continue larger renderer milestones with a preserved fallback. Exact previous sources and a source-matched rebuilt visuals.1 JAR are in `validation/checkpoints/`; the rebuilt binary differs from the originally tested JAR as documented. Do not modify Prism/worlds during routine development. GitHub uploads remain user-directed.

## Attribution

Renderer foundation: [Metallum](https://github.com/kokodio/metallum), MIT. Visual reference: [Vanilla RTX](https://github.com/Vanilla-RTX/Vanilla-RTX); no Vanilla RTX assets are included. ECC provenance and its MIT license are in `metallum/.agents/ecc/`. The vendored ponytail skill declares MIT licensing in its frontmatter. The repository's existing `LICENSE` is preserved.
