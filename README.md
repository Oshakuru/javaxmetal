# Java × Metal

Minecraft Java + Metal visual overhaul for Apple Silicon, using Metallum as the foundation and Vanilla RTX as the visual target.

## Current upload

This repository publishes the current development workflow, plan, and latest material-foundation JAR in `artifacts/`. The renderer source and runtime evidence remain in the local workspace and are not included in this workflow and binary upload. Build and validation scripts therefore require the existing local renderer checkout, Java 25 toolchain, and dependency cache; they cannot yet run from this repository alone.

- `AGENTS.md`: workspace rules and the vendored ponytail skill location.
- `metallum/AGENTS.md`: renderer rules, ECC verification, and independent review requirements.
- `metallum/.agents/`: ponytail, selected ECC skills, reviewer references, and ECC license/provenance.
- `PROTOTYPE.md`: development plan and evidence ledger, including historical decisions superseded by later entries.
- `build-metallum.sh` and `validation/`: existing local build, numerical checks, and benchmark protocol.

## Workflow

Use ponytail in full mode: implement the smallest complete change. Read the relevant code and project instructions first. For substantial renderer changes, run `sh validation/verify.sh`, inspect tracked and untracked changes, and request one independent correctness review using the ECC references. Record local verification separately from in-game acceptance in `PROTOTYPE.md`. Gradle NO-SOURCE tasks are not an automated test suite.

Deliver versioned candidates with checksums, toggles, exact replacement instructions, and one consolidated target checklist. Keep `.tooling/`, `.gradle-local/`, credentials, and local runtime artifacts out of Git. Do not modify the user's Prism installation or worlds during routine development.

## Current milestone

The material foundation is implemented and target-validated locally: all 48 prototype mappings were found in the loaded atlas, and a targeted sea lantern resolved to its emissive profile. The accepted stationary M1 baseline is approximately 590 FPS at 1440×900 with render/simulation distance eight, before new visual effects.

The next handoff combines material-driven shading, bounded reflections, emissive effects, and diagnostics. This integrated renderer is planned and has not been implemented. Full RTX-style path tracing remains a longer-term goal.

## Attribution

Renderer foundation: [Metallum](https://github.com/kokodio/metallum), MIT. Visual reference: [Vanilla RTX](https://github.com/Vanilla-RTX/Vanilla-RTX); no Vanilla RTX assets are included. ECC provenance and its MIT license are in `metallum/.agents/ecc/`. The vendored ponytail skill declares MIT licensing in its frontmatter. The repository's existing `LICENSE` is preserved.
