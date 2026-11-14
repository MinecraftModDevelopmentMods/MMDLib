package zone.moddev.mc.lib.common.runtime;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import java.nio.charset.StandardCharsets;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;

import net.neoforged.neoforge.event.AddPackFindersEvent;

import zone.moddev.mc.lib.MMDLib;

/**
 * An always-enabled data pack, {@code mmdlib_material_tags}, holding
 * {@link MaterialItemTags} for every loaded material. It sits at the bottom, so
 * datapacks can still replace any of those tags.
 */
public final class MaterialTagPack extends AbstractPackResources {
    private final Map<String, byte[]> files;

    private MaterialTagPack(PackLocationInfo location) {
        super(location);
        files = MaterialItemTags.data(MMDLib.loadedMaterials().values());
    }

    /** Adds the pack to data pack repositories. */
    public static void register(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA)
            return;
        event.addRepositorySource(output -> {
            var location = new PackLocationInfo("mmdlib_material_tags",
                Component.literal("MMDLib material tags"), PackSource.BUILT_IN,
                Optional.empty());
            var supplier = new Pack.ResourcesSupplier() {
                @Override
                public PackResources openPrimary(PackLocationInfo info) {
                    return new MaterialTagPack(info);
                }

                @Override
                public PackResources openFull(PackLocationInfo info,
                    Pack.Metadata metadata) {
                    return new MaterialTagPack(info);
                }
            };
            var pack = Pack.readMetaAndCreate(location, supplier,
                PackType.SERVER_DATA,
                new PackSelectionConfig(true, Pack.Position.BOTTOM, true));
            if (pack == null)
                throw new IllegalStateException(
                    "Could not create the material tag pack");
            output.accept(pack);
        });
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        if (!String.join("/", path).equals(PACK_META))
            return null;
        var metadata = ("{\"pack\":{\"pack_format\":"
            + SharedConstants.getCurrentVersion()
                .getPackVersion(PackType.SERVER_DATA)
            + ",\"description\":\"MMDLib material tags\"}}")
            .getBytes(StandardCharsets.UTF_8);
        return () -> new ByteArrayInputStream(metadata);
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type,
        ResourceLocation id) {
        var data = type == PackType.SERVER_DATA
            ? files.get("data/" + id.getNamespace() + "/" + id.getPath())
            : null;
        return data == null ? null : () -> new ByteArrayInputStream(data);
    }

    @Override
    public void listResources(PackType type, String namespace, String path,
        PackResources.ResourceOutput output) {
        if (type != PackType.SERVER_DATA)
            return;
        var prefix = "data/" + namespace + "/" + path + "/";
        files.forEach((file, data) -> {
            if (file.startsWith(prefix))
                output
                    .accept(
                        ResourceLocation.fromNamespaceAndPath(namespace,
                            file.substring(
                                ("data/" + namespace + "/").length())),
                        () -> new ByteArrayInputStream(data));
        });
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        if (type != PackType.SERVER_DATA)
            return Set.of();
        return files.keySet().stream().map(file -> file.split("/", 3)[1])
            .collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public void close() {
    }
}
