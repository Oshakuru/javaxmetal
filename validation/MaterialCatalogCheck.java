import com.metallum.material.MaterialCatalog;
import java.nio.file.*;
import java.io.StringReader;

public class MaterialCatalogCheck {
    public static void main(String[] args) throws Exception {
        String json = Files.readString(Path.of(args[0]));
        MaterialCatalog catalog = MaterialCatalog.read(new StringReader(json));
        if (catalog.size() < 30) throw new AssertionError("Representative material catalog missing");
        if (catalog.get("minecraft:block/stone").roughness() <= catalog.get("minecraft:block/gold_block").roughness())
            throw new AssertionError("Stone/metal distinction missing");
        if (catalog.get("minecraft:block/gold_block").metalness() != 1
                || catalog.get("minecraft:block/glass").transmission() <= 0
                || catalog.get("minecraft:block/glowstone").emission() <= 0
                || !catalog.get("minecraft:block/oak_planks").name().equals("wood")
                || !catalog.get("minecraft:block/water_still").name().equals("water")
                || !catalog.get("minecraft:block/oak_leaves").name().equals("foliage"))
            throw new AssertionError("Representative categories missing");
        if (catalog.get("modded:block/unmapped") != MaterialCatalog.UNASSIGNED)
            throw new AssertionError("Unmapped texture guessed as known");
        reject(json.replace("\"schema\": 1", "\"schema\": 9"));
        reject(json.replace("\"schema\": 1", "\"schema\": 1.5"));
        reject(json.replace("\"schema\": 1", "\"schema\": \"1\""));
        reject(json.replace("\"roughness\": 0.9", "\"roughness\": -1"));
        reject(json.replace("\"roughness\": 0.9", "\"roughness\": 1e999"));
        reject(json.replace("\"roughness\": 0.9", "\"roughness\": \"0.9\""));
        reject(json.replace("minecraft:block/stone", "minecraft:../stone"));
        reject(json.replace("\"minecraft:block/stone\": \"stone\"", "\"minecraft:block/stone\": \"undefined\""));
        System.out.println("PASS: multi-material mapping, fallback, schema, identifiers, and invalid scalar definitions");
    }
    static void reject(String json) throws Exception {
        try { MaterialCatalog.read(new StringReader(json)); }
        catch (RuntimeException expected) { return; }
        throw new AssertionError("Invalid material catalog accepted");
    }
}
