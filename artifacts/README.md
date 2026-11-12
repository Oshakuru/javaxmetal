# Current material foundation candidate

`metallum-0.0.25-materials.1.jar` is the latest local candidate. Verify it against `SHA256SUMS`. The original build manifest is preserved alongside it; its pending target status reflects build time. Later target evidence in `../PROTOTYPE.md` verifies loaded-atlas lookup for all 48 mappings and sea-lantern emissive identification. Resource override/reload and broader runtime acceptance remain pending.

This is a material audit/diagnostic build, not the planned integrated visual renderer. It does not add PBR lighting, reflections, or emissive illumination.

In the duplicate test instance, with Minecraft 26.3, Fabric, compatible Sodium, and Iris disabled, remove the previous Metallum JAR from `mods` and copy this one in. Keep exactly one Metallum JAR installed. F7 exports the material audit; F8 cycles Off/Depth/Surface directions; F9 controls the benchmark. Do not use the benchmark with AFK limiting enabled. See the benchmark protocol in `../validation/BENCHMARK.md`.

Earlier experimental JARs are intentionally omitted. No rebuild or new gameplay check was performed for this upload. Renderer source is not included in this workflow snapshot; upstream attribution is in the root README.
