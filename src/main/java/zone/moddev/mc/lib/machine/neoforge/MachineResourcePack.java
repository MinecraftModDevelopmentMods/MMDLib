package zone.moddev.mc.lib.machine.neoforge;

import java.io.*;

import java.nio.charset.StandardCharsets;

import java.util.*;

import com.google.gson.*;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraft.server.packs.resources.IoSupplier;

import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Low-priority generated assets/data. Authored resource packs and datapacks can
 * override them.
 */
public final class MachineResourcePack extends AbstractPackResources {
    private final PackType type;
    private final Map<ResourceLocation, byte[]> files;

    private MachineResourcePack(PackLocationInfo location, PackType type) {
        super(location);
        this.type = type;
        files = build(type);
    }

    public static void register(AddPackFindersEvent event) {
        var type = event.getPackType();
        event.addRepositorySource(output -> {
            var location = new PackLocationInfo("mmdlib_machine_defaults",
                Component.literal("MMDLib machine defaults"),
                PackSource.BUILT_IN, Optional.empty());
            var supplier = new Pack.ResourcesSupplier() {
                public PackResources openPrimary(PackLocationInfo info) {
                    return new MachineResourcePack(info, type);
                }

                public PackResources openFull(PackLocationInfo info,
                    Pack.Metadata metadata) {
                    return new MachineResourcePack(info, type);
                }
            };
            var pack = Pack.readMetaAndCreate(location, supplier, type,
                new PackSelectionConfig(true, Pack.Position.BOTTOM, true));
            if (pack == null)
                throw new IllegalStateException(
                    "Could not create machine resource pack");
            output.accept(pack);
        });
    }

    public static Map<ResourceLocation, byte[]> build(PackType type) {
        var result = new TreeMap<ResourceLocation, byte[]>();
        var languages = new TreeMap<String, JsonObject>();
        var tags = new TreeMap<ResourceLocation, JsonArray>();
        for (var machine : MachineContent.definitions().values()) {
            var id = machine.id();
            String model = id.getNamespace() + ":block/" + id.getPath();
            if (type == PackType.CLIENT_RESOURCES) {
                var block = new JsonObject();
                block.addProperty("parent", "minecraft:block/orientable");
                var textures = new JsonObject();
                machine.textures().forEach(textures::addProperty);
                block.add("textures", textures);
                put(result, id, "models/block/", block);
                var item = new JsonObject();
                item.addProperty("parent", model);
                put(result, id, "models/item/", item);
                var variants = new JsonObject();
                String[] directions = {"north", "east", "south", "west"};
                for (int i = 0; i < 4; i++) {
                    var variant = new JsonObject();
                    variant.addProperty("model", model);
                    variant.addProperty("y", i * 90);
                    variants.add("facing=" + directions[i], variant);
                }
                var state = new JsonObject();
                state.add("variants", variants);
                put(result, id, "blockstates/", state);
                var lang = languages.computeIfAbsent(id.getNamespace(),
                    key -> new JsonObject());
                lang.addProperty("block." + id.getNamespace() + "."
                    + id.getPath().replace('/', '.'), machine.name());
                lang.addProperty(id + ".info", machine.name());
                var recipeId = machine.recipeType().id();
                if (machine.recipeType().declare())
                    languages
                        .computeIfAbsent(recipeId.getNamespace(),
                            key -> new JsonObject())
                        .addProperty(
                            "jei." + recipeId.getNamespace() + "."
                                + recipeId.getPath(),
                            machine.recipeType().name());
            } else {
                var table = JsonParser.parseString(
                    "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\""
                        + id
                        + "\"}],\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}");
                put(result, id, "loot_table/blocks/", table);
                tags.computeIfAbsent(machine.mineable(), key -> new JsonArray())
                    .add(id.toString());
            }
        }
        if (type == PackType.CLIENT_RESOURCES) {
            languages.computeIfAbsent("mmdlib", key -> new JsonObject())
                .addProperty("item.mmdlib.machine_recovery_capsule",
                    "Machine Recovery Capsule");
            languages
                .forEach((namespace,
                    lang) -> putRaw(result, ResourceLocation
                        .fromNamespaceAndPath(namespace, "lang/en_us.json"),
                        lang));
            put(result, MachineContent.RECOVERY, "models/item/",
                JsonParser.parseString(
                    "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/bundle\"}}"));
        } else
            tags.forEach((id, values) -> {
                var tag = new JsonObject();
                tag.addProperty("replace", false);
                tag.add("values", values);
                put(result, id, "tags/block/", tag);
            });
        return Collections.unmodifiableMap(result);
    }

    private static void put(Map<ResourceLocation, byte[]> files,
        ResourceLocation id, String prefix, JsonElement json) {
        putRaw(files, ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
            prefix + id.getPath() + ".json"), json);
    }

    private static void putRaw(Map<ResourceLocation, byte[]> files,
        ResourceLocation id, JsonElement json) {
        if (files.putIfAbsent(id,
            json.toString().getBytes(StandardCharsets.UTF_8)) != null)
            throw new IllegalArgumentException(
                "Duplicate generated resource " + id);
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        if (!String.join("/", path).equals("pack.mcmeta"))
            return null;
        byte[] metadata = ("{\"pack\":{\"pack_format\":"
            + SharedConstants.getCurrentVersion().getPackVersion(type)
            + ",\"description\":\"MMDLib generated machine defaults\"}}")
            .getBytes(StandardCharsets.UTF_8);
        return () -> new ByteArrayInputStream(metadata);
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType requested,
        ResourceLocation id) {
        var data = requested == type ? files.get(id) : null;
        return data == null ? null : () -> new ByteArrayInputStream(data);
    }

    @Override
    public void listResources(PackType requested, String namespace, String path,
        PackResources.ResourceOutput output) {
        if (requested != type)
            return;
        files.forEach((id, data) -> {
            if (id.getNamespace().equals(namespace)
                && id.getPath().startsWith(path + "/"))
                output.accept(id, () -> new ByteArrayInputStream(data));
        });
    }

    @Override
    public Set<String> getNamespaces(PackType requested) {
        if (requested != type)
            return Set.of();
        var namespaces = new HashSet<String>();
        files.keySet().forEach(id -> namespaces.add(id.getNamespace()));
        return Set.copyOf(namespaces);
    }

    @Override
    public void close() {
    }
}
