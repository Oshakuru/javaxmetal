# Candidate JARs

The current candidate is **metallum-0.0.25-visuals.2.jar**. Verify its checksum against `SHA256SUMS`; the artifact/source manifest is `visuals.2-build.json`. See `../validation/VISUALS.2.md` for exact installation, controls and one consolidated acceptance session. Keep exactly one Metallum JAR installed in the duplicate test instance, with Iris disabled.

This candidate adds bounded material shading, reflections and nearby colored lights with occlusion. Local checks and independent review passed; actual visuals.2 gameplay/runtime/performance acceptance is pending. Earlier visuals.1 recordings establish representative iron reflections and material diagnostics only.

`visuals.1.jar` here is a **source-matched rebuild** from preserved previous sources, SHA-256 5ab930b0568200981696be241930f9f939f2e13fdb110d5e6e730f382c7bf6ca. It is not the original tested binary (8a5300b0…), which was absent from the workspace when the rollback was prepared. `visuals.1-build.json` retains the original build record; see the visuals.2 manifest for rebuilt rollback provenance. The exact previous source archive is in `../validation/checkpoints/`.

Older material/normal/depth/benchmark JARs are historical experiments, including known failures and unissued builds. They are not current recommendations. The material catalog still covers 48 representative textures, not all blocks. Full path tracing, sun shadows, general indirect illumination and finished glass/water refraction are not implemented.
