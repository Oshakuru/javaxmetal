package com.metallum.material;

import com.google.gson.GsonBuilder;
import com.metallum.Metallum;
import com.metallum.mixin.MaterialAtlasAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import java.nio.file.Files;
import java.util.*;

/** On-demand CPU audit. It does not alter shaders or claim per-fragment material binding. */
public final class MaterialInspector {
    private static final Identifier CATALOG = Identifier.fromNamespaceAndPath("metallum", "materials/default.json");
    private MaterialInspector() {}

    public static void inspect(Minecraft client) {
        if (client.level == null || client.gui.screen() != null) return;
        var resource = client.getResourceManager().getResource(CATALOG);
        try (var reader = resource.orElseThrow(() -> new IllegalStateException("Material catalog unavailable")).openAsReader()) {
            MaterialCatalog catalog = MaterialCatalog.read(reader);
            var texture = client.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
            if (!(texture instanceof TextureAtlas atlas)) throw new IllegalStateException("Block atlas unavailable");
            Map<String, Object> report = new LinkedHashMap<>();
            Map<String, Integer> coverage = new TreeMap<>();
            var sprites = new TreeMap<String, Object>();
            for (var entry : ((MaterialAtlasAccessor) atlas).metallum$materialSprites().entrySet()) {
                String id = entry.getKey().toString();
                var material = catalog.get(id);
                coverage.merge(material.name(), 1, Integer::sum);
                TextureAtlasSprite sprite = entry.getValue();
                sprites.put(id, Map.of("material", material,
                        "uv", List.of(sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1()),
                        "animated", sprite.isAnimated()));
            }
            report.put("scope", "Scalar material catalog and actual block-atlas UVs; no PBR shading or fragment binding yet");
            report.put("schema", 1);
            report.put("capturedUtc", java.time.Instant.now().toString());
            report.put("catalogResource", CATALOG.toString());
            report.put("catalogSourcePack", resource.orElseThrow().sourcePackId());
            report.put("atlas", Map.of("width", atlas.getTexture().getWidth(0), "height", atlas.getTexture().getHeight(0)));
            report.put("coverageBySprite", coverage);
            report.put("sprites", sprites);
            var selected = selectedFace(client, catalog);
            report.put("selected", selected);
            var output = client.gameDirectory.toPath().resolve("metallum-materials");
            Files.createDirectories(output);
            Files.writeString(output.resolve("latest.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            client.gui.chatListener().handleSystemMessage(Component.literal("Material audit: " + coverage
                    + ". Saved metallum-materials/latest.json"), false);
            client.gui.chatListener().handleSystemMessage(Component.literal("Target face candidates: " + selected), false);
            Metallum.LOGGER.info("Material audit saved; {}", coverage);
        } catch (Exception exception) {
            Metallum.LOGGER.error("Material audit failed; rendering unchanged", exception);
            client.gui.chatListener().handleSystemMessage(Component.literal("Material audit failed; see the log. Rendering unchanged."), false);
        }
    }

    private static Map<String, Object> selectedFace(Minecraft client, MaterialCatalog catalog) {
        if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return Map.of("result", "no block targeted");
        var state = client.level.getBlockState(hit.getBlockPos());
        var model = client.getModelManager().getBlockStateModelSet().get(state);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(state.getSeed(hit.getBlockPos())), parts);
        Set<String> names = new TreeSet<>();
        for (var part : parts) {
            for (var quad : part.getQuads(hit.getDirection())) names.add(quad.materialInfo().sprite().contents().name().toString());
            for (var quad : part.getQuads(null)) {
                if (quad.direction() == hit.getDirection()) names.add(quad.materialInfo().sprite().contents().name().toString());
            }
        }
        boolean particleFallback = names.isEmpty();
        if (particleFallback) names.add(model.particleMaterial().sprite().contents().name().toString());
        Map<String, MaterialCatalog.Material> materials = new TreeMap<>();
        for (String name : names) materials.put(name, catalog.get(name));
        return Map.of("block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                "face", hit.getDirection().toString(), "particleFallback", particleFallback, "textures", materials);
    }
}
