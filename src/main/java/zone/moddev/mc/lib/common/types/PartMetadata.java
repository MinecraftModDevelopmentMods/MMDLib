package zone.moddev.mc.lib.common.types;

import java.util.*;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.common.codecs.BlockEntityTemplate;

/**
 * Data-owned aliases, companions and naming. Behavior settings belong to the
 * selected factory's codec.
 *
 * @param aliases other names the part is selected by
 * @param entityFactory the world-entity companion's factory, if any
 * @param entityData that factory's settings
 * @param blockEntity the block-entity companion, if any
 * @param namePattern the path of the part's registry ID, with
 *            {@code {material}} for the material's name, such as
 *            {@code raw_{material}}; empty for the usual
 *            {@code <material>_<part>}
 * @param inAll whether {@code all} selects the part; when false, a material
 *            gets it only by naming it
 */
public record PartMetadata(List<String> aliases,
    Optional<ResourceLocation> entityFactory, FactoryData entityData,
    Optional<BlockEntityTemplate> blockEntity, Optional<String> namePattern,
    boolean inAll) {
    /** Stands for the material's name in a {@link #namePattern}. */
    public static final String MATERIAL = "{material}";
    public static final PartMetadata EMPTY = new PartMetadata(List.of(),
        Optional.empty(), FactoryData.EMPTY, Optional.empty());
    public static final Codec<PartMetadata> CODEC = RecordCodecBuilder
        .<PartMetadata>create(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("aliases", List.of())
                .forGetter(PartMetadata::aliases),
            ResourceLocation.CODEC.optionalFieldOf("entity_factory")
                .forGetter(PartMetadata::entityFactory),
            FactoryData.CODEC.optionalFieldOf("entity_data", FactoryData.EMPTY)
                .forGetter(PartMetadata::entityData),
            BlockEntityTemplate.CODEC.optionalFieldOf("block_entity")
                .forGetter(PartMetadata::blockEntity),
            Codec.STRING.validate(PartMetadata::validPattern)
                .optionalFieldOf("name_pattern")
                .forGetter(PartMetadata::namePattern),
            Codec.BOOL.optionalFieldOf("in_all", true)
                .forGetter(PartMetadata::inAll))
            .apply(i, PartMetadata::new))
        .validate(PartMetadata::check);

    /** Metadata named the usual way and selected by {@code all}. */
    public PartMetadata(List<String> aliases,
        Optional<ResourceLocation> entityFactory, FactoryData entityData,
        Optional<BlockEntityTemplate> blockEntity) {
        this(aliases, entityFactory, entityData, blockEntity, Optional.empty(),
            true);
    }

    /**
     * Copies the aliases; the optional components must not be null.
     *
     * @throws IllegalArgumentException if the name pattern does not contain
     *             {@code {material}} once or is not a valid ID path
     */
    public PartMetadata {
        aliases = List.copyOf(aliases);
        Objects.requireNonNull(entityFactory);
        Objects.requireNonNull(entityData);
        Objects.requireNonNull(blockEntity);
        Objects.requireNonNull(namePattern);
        namePattern.ifPresent(PartMetadata::checkPattern);
    }

    /**
     * The path of the registry ID of this part for the material named
     * {@code material} (an ID path), or empty for the usual
     * {@code <material>_<part>}.
     */
    public Optional<String> name(String material) {
        return namePattern.map(p -> p.replace(MATERIAL, material));
    }

    private static void checkPattern(String pattern) {
        var at = pattern.indexOf(MATERIAL);
        if (at < 0 || pattern.indexOf(MATERIAL, at + 1) >= 0)
            throw new IllegalArgumentException(
                "name_pattern must contain " + MATERIAL + " once: " + pattern);
        if (!ResourceLocation.isValidPath(pattern.replace(MATERIAL, "m")))
            throw new IllegalArgumentException(
                "name_pattern is not a valid ID path: " + pattern);
    }

    private static DataResult<String> validPattern(String pattern) {
        try {
            checkPattern(pattern);
            return DataResult.success(pattern);
        } catch (IllegalArgumentException ex) {
            return DataResult.error(ex::getMessage);
        }
    }

    private static DataResult<PartMetadata> check(PartMetadata value) {
        if (value.entityFactory().isEmpty()
            && !value.entityData().equals(FactoryData.EMPTY))
            return DataResult
                .error(() -> "entity_data requires entity_factory");
        if (value.namePattern().isPresent()
            && value.entityFactory().isPresent())
            return DataResult
                .error(() -> "name_pattern cannot be used with entity_factory");
        return DataResult.success(value);
    }
}
