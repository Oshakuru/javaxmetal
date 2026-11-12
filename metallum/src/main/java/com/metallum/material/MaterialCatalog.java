package com.metallum.material;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.TreeMap;

/** Scalar material definitions; texture maps and renderer shading are separate consumers. */
public final class MaterialCatalog {
    public record Material(String name, float roughness, float metalness, float emission, float transmission) {}
    public static final Material UNASSIGNED = new Material("unassigned", 1, 0, 0, 0);
    private final Map<String, Material> textures;

    private MaterialCatalog(Map<String, Material> textures) {
        this.textures = Map.copyOf(textures);
    }

    public static MaterialCatalog empty() { return new MaterialCatalog(Map.of()); }
    public Material get(String texture) { return textures.getOrDefault(texture, UNASSIGNED); }
    public int size() { return textures.size(); }

    public static MaterialCatalog read(Reader reader) throws IOException {
        StringBuilder input = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            if (input.length() + count > 1_048_576) throw new IllegalArgumentException("Material catalog exceeds 1 MiB");
            input.append(buffer, 0, count);
        }
        JsonObject root = JsonParser.parseString(input.toString()).getAsJsonObject();
        var schema = root.get("schema");
        if (schema == null || !schema.isJsonPrimitive() || !schema.getAsJsonPrimitive().isNumber()
                || schema.getAsBigDecimal().compareTo(java.math.BigDecimal.ONE) != 0)
            throw new IllegalArgumentException("Unsupported material schema");
        Map<String, Material> profiles = new TreeMap<>();
        for (var entry : root.getAsJsonObject("profiles").entrySet()) {
            String name = entry.getKey();
            if (!name.matches("[a-z0-9_]{1,48}") || name.equals("unassigned"))
                throw new IllegalArgumentException("Invalid material name: " + name);
            JsonObject values = entry.getValue().getAsJsonObject();
            profiles.put(name, new Material(name, number(values, "roughness", 1), number(values, "metalness", 1),
                    number(values, "emission", 64), number(values, "transmission", 1)));
        }
        Map<String, Material> textures = new TreeMap<>();
        for (var entry : root.getAsJsonObject("textures").entrySet()) {
            if (!entry.getKey().matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || entry.getKey().contains(".."))
                throw new IllegalArgumentException("Invalid texture identifier: " + entry.getKey());
            Material material = profiles.get(entry.getValue().getAsString());
            if (material == null) throw new IllegalArgumentException("Undefined material for " + entry.getKey());
            textures.put(entry.getKey(), material);
        }
        return new MaterialCatalog(textures);
    }

    private static float number(JsonObject values, String key, float max) {
        var value = values.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Missing/nonnumeric material property: " + key);
        float result = value.getAsFloat();
        if (!Float.isFinite(result) || result < 0 || result > max)
            throw new IllegalArgumentException("Material property out of range: " + key);
        return result;
    }
}
