package zone.moddev.mc.lib.compat.CraftTweaker;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.base.Suppliers;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import zone.moddev.mc.lib.MMDLib;

/**
 * Adds two always-enabled packs, for both resources and data:
 *
 * <ol>
 * <li>{@code mmdlib/script_materials}: what {@link ScriptMaterialResources}
 * generates for the script materials.
 * <li>{@code mmdlib/mmdlibdata}: the {@code scripts/mmdlibdata} folder, a
 * normal pack root ({@code assets/}, {@code data/}, optional
 * {@code pack.mcmeta}) placed above the generated pack, so any generated file
 * can be replaced. Its {@code templates/} folder replaces MMDLib's blank
 * templates instead.
 * </ol>
 */
final class MaterialPacks {
    private MaterialPacks() {
    }

    /** Adds the packs whenever a pack repository is built. */
    static void register(IEventBus modBus) {
        modBus.addListener(MaterialPacks::addPacks);
    }

    /**
     * {@code scripts/mmdlibdata}: pack authors' overrides for script materials.
     */
    static Path dataDirectory() {
        return com.blamejared.crafttweaker.api.CraftTweakerAPI
            .getScriptsDirectory().resolve("mmdlibdata");
    }

    private static void addPacks(AddPackFindersEvent event) {
        var type = event.getPackType();
        var folder = dataDirectory();
        var selection = new PackSelectionConfig(true, Pack.Position.TOP, false);
        Supplier<SortedMap<String, byte[]>> contents = Suppliers.memoize(() -> {
            var materials = ScriptDefinitions.materialResults();
            var blocks = ScriptDefinitions.blockResults();
            var items = ScriptDefinitions.itemResults();
            return type == PackType.CLIENT_RESOURCES
                ? ScriptMaterialResources.assets(materials, blocks, items,
                    ScriptDefinitions.fluidResults(),
                    ScriptDefinitions.basedOn(), templates(folder), folder,
                    ScriptDefinitions.resourceOptions())
                : ScriptMaterialResources.data(materials, blocks, items,
                    ScriptDefinitions.itemTags(),
                    ScriptDefinitions.resourceOptions());
        });
        var generated = new PackLocationInfo("mmdlib/script_materials",
            Component.literal("MMDLib script materials"), PackSource.BUILT_IN,
            Optional.empty());
        event.addRepositorySource(
            add -> add.accept(Pack.readMetaAndCreate(generated,
                supplier(info -> new GeneratedPack(info, type, contents)), type,
                selection)));
        if (Files.isDirectory(folder)) {
            var user = new PackLocationInfo("mmdlib/mmdlibdata",
                Component.literal("scripts/mmdlibdata"), PackSource.BUILT_IN,
                Optional.empty());
            event.addRepositorySource(
                add -> add.accept(Pack.readMetaAndCreate(user,
                    supplier(info -> new FolderPack(info, folder, type)), type,
                    selection)));
        }
    }

    private static MaterialTemplates templates(Path folder) {
        var jar = ModList.get().getModFileById(MMDLib.MODID).getFile()
            .findResource("mmdlib_templates");
        return new MaterialTemplates(List.of(folder.resolve("templates"), jar));
    }

    private static Pack.ResourcesSupplier supplier(
        java.util.function.Function<PackLocationInfo, PackResources> open) {
        return new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo info) {
                return open.apply(info);
            }

            @Override
            public PackResources openFull(PackLocationInfo info,
                Pack.Metadata metadata) {
                return open.apply(info);
            }
        };
    }

    private static IoSupplier<InputStream> metadata(PackType type,
        String description) {
        var json = "{\"pack\":{\"description\":\"" + description
            + "\",\"pack_format\":"
            + SharedConstants.getCurrentVersion().getPackVersion(type) + "}}";
        var bytes = json.getBytes(StandardCharsets.UTF_8);
        return () -> new ByteArrayInputStream(bytes);
    }

    /** A pack whose files are generated in memory on first use. */
    private static final class GeneratedPack extends AbstractPackResources {
        private final PackType type;
        private final Supplier<SortedMap<String, byte[]>> contents;

        GeneratedPack(PackLocationInfo info, PackType type,
            Supplier<SortedMap<String, byte[]>> contents) {
            super(info);
            this.type = type;
            this.contents = contents;
        }

        @Override
        public IoSupplier<InputStream> getRootResource(String... path) {
            return String.join("/", path).equals(PACK_META)
                ? metadata(type,
                    "Generated resources for MMDLib script materials")
                : null;
        }

        @Override
        public IoSupplier<InputStream> getResource(PackType requested,
            ResourceLocation id) {
            if (requested != type)
                return null;
            var bytes = contents.get().get(type.getDirectory() + "/"
                + id.getNamespace() + "/" + id.getPath());
            return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
        }

        @Override
        public void listResources(PackType requested, String namespace,
            String path, ResourceOutput output) {
            if (requested != type)
                return;
            var root = type.getDirectory() + "/" + namespace + "/";
            var prefix = root + path + "/";
            for (var entry : contents.get().tailMap(prefix).entrySet()) {
                if (!entry.getKey().startsWith(prefix))
                    break;
                var bytes = entry.getValue();
                output.accept(
                    ResourceLocation.fromNamespaceAndPath(namespace,
                        entry.getKey().substring(root.length())),
                    () -> new ByteArrayInputStream(bytes));
            }
        }

        @Override
        public Set<String> getNamespaces(PackType requested) {
            if (requested != type)
                return Set.of();
            var root = type.getDirectory() + "/";
            return contents.get().keySet().stream()
                .filter(key -> key.startsWith(root))
                .map(key -> key.substring(root.length(),
                    key.indexOf('/', root.length())))
                .collect(Collectors.toUnmodifiableSet());
        }

        @Override
        public void close() {
        }
    }

    /**
     * The {@code scripts/mmdlibdata} folder; it works without a
     * {@code pack.mcmeta}.
     */
    private static final class FolderPack extends PathPackResources {
        private final Path root;
        private final PackType type;

        FolderPack(PackLocationInfo info, Path root, PackType type) {
            super(info, root);
            this.root = root;
            this.type = type;
        }

        @Override
        public IoSupplier<InputStream> getRootResource(String... path) {
            if (String.join("/", path).equals(PACK_META)
                && !Files.isRegularFile(root.resolve(PACK_META)))
                return metadata(type,
                    "Script material overrides from scripts/mmdlibdata");
            return super.getRootResource(path);
        }
    }
}
