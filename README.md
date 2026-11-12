# Java × Metal

Minecraft Java + Metal visual overhaul for Apple Silicon, using Metallum as the foundation and Vanilla RTX as the visual target.

## Repository contents

This is a snapshot of the local project workspace: renderer source in `metallum/`, workflow instructions and ECC references, development plan, validation scripts and recorded evidence, project images, and candidate JARs in `artifacts/`. The separate `minecraft-rust-review/` source snapshot is retained as research reference, not the selected renderer. Original licenses and notices are preserved.

Local Java toolchains, Gradle caches, generated build directories, nested Git metadata, and JVM crash dumps are excluded. Build scripts expect Java 25 in `.tooling/jdk25/Contents/Home` and cached dependencies in `.gradle-local`; provision these locally before running the offline verification script.

Read `AGENTS.md` and `metallum/AGENTS.md` before renderer work. `PROTOTYPE.md` separates local verification from target acceptance and retains historical decisions superseded by later entries. Candidate binaries and their checksums are in `artifacts/`; only `materials.1` is the current candidate.

## Workflow

Use ponytail in full mode: implement the smallest complete change. Read the relevant code and project instructions first. For substantial renderer changes, run `sh validation/verify.sh`, inspect tracked and untracked changes, and request one independent correctness review using the ECC references. Record local verification separately from in-game acceptance in `PROTOTYPE.md`. Gradle NO-SOURCE tasks are not an automated test suite.

Deliver versioned candidates with checksums, toggles, exact replacement instructions, and one consolidated target checklist. Keep `.tooling/`, `.gradle-local/`, credentials, and local runtime artifacts out of Git. Do not modify the user's Prism installation or worlds during routine development.

## Current milestone

The material foundation is implemented and target-validated locally: all 48 prototype mappings were found in the loaded atlas, and a targeted sea lantern resolved to its emissive profile. The accepted stationary M1 baseline is approximately 590 FPS at 1440×900 with render/simulation distance eight, before new visual effects.

The next handoff combines material-driven shading, bounded reflections, emissive effects, and diagnostics. This integrated renderer is planned and has not been implemented. Full RTX-style path tracing remains a longer-term goal.

## Attribution

Renderer foundation: [Metallum](https://github.com/kokodio/metallum), MIT. Visual reference: [Vanilla RTX](https://github.com/Vanilla-RTX/Vanilla-RTX); no Vanilla RTX assets are included. ECC provenance and its MIT license are in `metallum/.agents/ecc/`. The vendored ponytail skill declares MIT licensing in its frontmatter. The repository's existing `LICENSE` is preserved.
