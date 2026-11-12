# Metallum prototype workflow

Use `.agents/skills/ponytail/SKILL.md` in full mode for every project activity.

## Scope

This is a Minecraft 26.3 Fabric rendering backend using Java 25 and Metal through metal-jvm. Preserve the upstream MIT license and attribution. User instructions and host policies take precedence.

## ECC workflows

Use these local ECC skills when implementing or reviewing changes:
- `.agents/skills/ecc-verification-loop/SKILL.md`: verification after substantial changes and before delivering a JAR.
- `.agents/skills/ecc-java-coding-standards/SKILL.md`: shared Java rules only. This is neither Spring Boot nor Quarkus.

Use `.agents/ecc/code-reviewer.md` and `.agents/ecc/java-reviewer.md` as review references. After substantive renderer changes, request one independent reviewer sub-agent when available; give it read-only scope, the changed files, and these references. Do not adopt model names from copied frontmatter. Review confidence, concrete failure scenarios, GPU lifetime, synchronization, frame ordering, resize, reload, and disabled-mode overhead.

## Project adaptations

- Plan larger features in the existing `../PROTOTYPE.md`; capture decisions and verified results there.
- Follow ECC's build/type/lint/test/security/diff phases using this project's tools, not its illustrative npm commands.
- Build: `sh ../build-metallum.sh --offline` (omit offline only when new dependencies are required).
- Java compilation checks Java types. No standalone Metal compiler is installed. On macOS with Swift, verify.sh runs an isolated native Metal fixture; Minecraft/Metal-JVM shader integration remains a separate target check.
- Diff hygiene: `git diff --check`, inspect tracked diffs and untracked source files.
- There is no configured linter or coverage instrumentation. Report those as not configured; do not claim 80% coverage or an automated test pass from Gradle's NO-SOURCE tasks.
- Use meaningful numeric regression checks for reconstruction math and test-first reproductions for regressions where feasible. GPU behavior needs target checks. Do not add web/database tests or superficial coverage tests.
- Prefer immutable state where practical. Metal resource handles and frame encoders require controlled mutation; preserve thread ownership and explicit cleanup rather than copying native objects.
- Separate local verification from target acceptance. Record which exact JAR the user tested and what the screenshot/log demonstrates; never infer stability from a single screenshot.
- No global ECC installer/configuration changes or executable hooks are needed for this project.

## Graphics milestones

Keep diagnostics opt-in and disabled at startup. F8 currently cycles Off, Depth, Surface directions. Preserve a working fallback and clear logs. Avoid per-frame heap allocations in new hot paths when practical.

Validate transforms and UV conventions against SPIRV-Cross's Y flip and Minecraft's reversed zero-to-one depth. Keep camera data synchronized with the captured depth. A depth-derived geometric normal does not identify ice or replace material normal maps.

For each delivered JAR, report its version, toggles, local checks, target checks still pending, and exact replacement instructions. Do not modify the user's Prism installation or worlds without appropriate authorization.
