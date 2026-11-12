# Metal Shaders workspace

Use the installed `ponytail` skill at `metallum/.agents/skills/ponytail/SKILL.md` for every activity in this project, including planning, research, coding, review, validation, and documentation. Read it at the start of each session and use full mode by default unless the user requests another level or turns it off. Apply its smallest-complete-solution approach alongside the project-specific ECC workflows and verification requirements below.

The renderer source is in `metallum/`. Before renderer work, read `metallum/AGENTS.md` and follow its project-specific ECC workflows. Relevant ECC skills are vendored under `metallum/.agents/skills/`; reviewer references and provenance are in `metallum/.agents/ecc/`.

Use `sh validation/verify.sh` for the local build, diff hygiene, and numerical checks. Record verification and in-game acceptance separately in `PROTOTYPE.md`. Do not treat Gradle's NO-SOURCE test task as an automated test suite.

Keep `.tooling/` and `.gradle-local/` local. Do not modify the user's Prism instance or worlds as part of routine source changes.

Product direction: Minecraft Java + native Metal renderer for Apple Silicon, based on Metallum; official project repository `ernestovegaMD/javaxmetal`. Use `NVIDIAGameWorks/rtx-remix-minecraft` for renderer/material/lighting inspiration and `Vanilla-RTX/Vanilla-RTX-App` for material tooling and eventual setup/management experience. Current post effects are intermediate prototypes, not the final renderer architecture. Preserve M1 measurements and follow the current direction/evidence ledger in `PROTOTYPE.md`.
