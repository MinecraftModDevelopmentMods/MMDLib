package zone.moddev.mc.lib.common.runtime;

import java.util.Locale;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import zone.moddev.mc.lib.common.types.*;

/** Applies unresolved definition properties only when Minecraft's registration suppliers run. */
public final class RuntimeProperties {
  private RuntimeProperties() {}

  /** Fresh item properties from a definition. */
  public static Item.Properties item(MMDItem definition) {
    var p = new Item.Properties().stacksTo(definition.maxStackSize());
    if (definition.maxDamage() > 0) p.durability(definition.maxDamage());
    if (definition.fireResistant()) p.fireResistant();
    if (definition.containerItem() != null) {
      if (!BuiltInRegistries.ITEM.containsKey(definition.containerItem()))
        throw new IllegalArgumentException("Missing container: " + definition.containerItem());
      p.craftRemainder(BuiltInRegistries.ITEM.get(definition.containerItem()));
    }
    return p;
  }

  /**
   * Fresh block properties from a definition and the material's runtime settings.
   *
   * @param fluid whether this is a liquid block, which keeps its definition's sound
   */
  public static BlockBehaviour.Properties block(
      MMDBlock definition, MaterialRuntimeSettings runtime, boolean fluid) {
    var d = definition.properties();
    var p =
        BlockBehaviour.Properties.of()
            .strength(d.hardness(), d.blastResistance())
            .friction(d.friction())
            .speedFactor(d.speedFactor())
            .jumpFactor(d.jumpFactor())
            .lightLevel(state -> d.lightLevel())
            .sound(
                named(SoundType.class, fluid ? d.sound() : runtime.blockSound().orElse(d.sound())))
            .mapColor(
                named(
                    MapColor.class,
                    fluid ? d.mapColor() : runtime.mapColor().orElse(d.mapColor())));
    if (d.requiresCorrectTool()) p.requiresCorrectToolForDrops();
    if (!d.collision()) p.noCollission();
    if (!d.occlusion()) p.noOcclusion();
    if (d.randomTicks()) p.randomTicks();
    if (d.replaceable()) p.replaceable();
    return p;
  }

  private static <T> T named(Class<T> type, String name) {
    try {
      return type.cast(type.getField(name.toUpperCase(Locale.ROOT)).get(null));
    } catch (ReflectiveOperationException | ClassCastException ex) {
      throw new IllegalArgumentException("Unknown " + type.getSimpleName() + ": " + name, ex);
    }
  }

  /**
   * The registry entry {@code id}.
   *
   * @throws IllegalArgumentException if it is missing
   */
  public static <T> T required(net.minecraft.core.Registry<T> registry, ResourceLocation id) {
    if (!registry.containsKey(id))
      throw new IllegalArgumentException("Missing registry entry: " + id);
    return registry.get(id);
  }
}
