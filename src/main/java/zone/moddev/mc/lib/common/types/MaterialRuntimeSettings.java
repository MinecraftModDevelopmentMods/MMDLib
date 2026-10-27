package zone.moddev.mc.lib.common.types;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;

/** Registry references used by runtime implementations, supplied by material data. */
public record MaterialRuntimeSettings(Optional<ResourceLocation> repairItem, ResourceLocation incorrectBlocks,
        ResourceLocation baseBlock, ResourceLocation blockSetType, ResourceLocation armorTexture,
        Optional<String> blockSound, Optional<String> mapColor, boolean tintedArmor) {
    public static final MaterialRuntimeSettings DEFAULT = new MaterialRuntimeSettings(Optional.empty(),
            ResourceLocation.parse("minecraft:incorrect_for_iron_tool"), ResourceLocation.parse("minecraft:iron_block"),
            ResourceLocation.parse("minecraft:iron"), ResourceLocation.parse("minecraft:iron"), Optional.empty(), Optional.empty(), false);
    public static final Codec<MaterialRuntimeSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("repair_item").forGetter(MaterialRuntimeSettings::repairItem),
            ResourceLocation.CODEC.optionalFieldOf("incorrect_blocks", DEFAULT.incorrectBlocks).forGetter(MaterialRuntimeSettings::incorrectBlocks),
            ResourceLocation.CODEC.optionalFieldOf("base_block", DEFAULT.baseBlock).forGetter(MaterialRuntimeSettings::baseBlock),
            ResourceLocation.CODEC.optionalFieldOf("block_set_type", DEFAULT.blockSetType).forGetter(MaterialRuntimeSettings::blockSetType),
            ResourceLocation.CODEC.optionalFieldOf("armor_texture", DEFAULT.armorTexture).forGetter(MaterialRuntimeSettings::armorTexture),
            Codec.STRING.optionalFieldOf("block_sound").forGetter(MaterialRuntimeSettings::blockSound),
            Codec.STRING.optionalFieldOf("map_color").forGetter(MaterialRuntimeSettings::mapColor),
            Codec.BOOL.optionalFieldOf("tinted_armor", false).forGetter(MaterialRuntimeSettings::tintedArmor)
    ).apply(i, MaterialRuntimeSettings::new));
}
