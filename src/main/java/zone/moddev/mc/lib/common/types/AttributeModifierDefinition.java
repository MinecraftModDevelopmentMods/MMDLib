package zone.moddev.mc.lib.common.types;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** Unresolved 1.21 attribute declaration; resolve its attribute holder during item creation. */
public record AttributeModifierDefinition(ResourceLocation attribute, ResourceLocation id, double amount,
        AttributeModifier.Operation operation, EquipmentSlotGroup slot) {
    public static final Codec<AttributeModifierDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("type").forGetter(AttributeModifierDefinition::attribute),
            ResourceLocation.CODEC.fieldOf("id").forGetter(AttributeModifierDefinition::id),
            Codec.DOUBLE.fieldOf("amount").forGetter(AttributeModifierDefinition::amount),
            AttributeModifier.Operation.CODEC.fieldOf("operation").forGetter(AttributeModifierDefinition::operation),
            EquipmentSlotGroup.CODEC.optionalFieldOf("slot", EquipmentSlotGroup.ANY).forGetter(AttributeModifierDefinition::slot)
    ).apply(i, AttributeModifierDefinition::new));
}
