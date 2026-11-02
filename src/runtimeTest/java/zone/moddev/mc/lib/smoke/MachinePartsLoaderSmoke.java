package zone.moddev.mc.lib.smoke;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.machine.internals.MachineParts;

/**
 * Checks that part files in a real datapack reach {@link MachineParts#current()} through the reload
 * listener. The smoke init script copies {@code src/runtimeTest/datapack} into the smoke world.
 */
final class MachinePartsLoaderSmoke {
  private MachinePartsLoaderSmoke() {}

  static void check() {
    var parts = MachineParts.current();
    var gearId = ResourceLocation.parse("mmdlib:iron_gear");
    var gear = parts.resolve(gearId);
    require(gear.isPresent(), "the datapack's gear file was loaded");
    double hardness = MMDLib.itemMaterials().get(gearId).properties().hardness();
    var part = gear.get();
    require(part.socket().equals("gears"), "socket from the file");
    require(part.modifiers().size() == 1, "one modifier");
    var modifier = part.modifiers().getFirst();
    require(
        modifier.stat().equals("speed")
            && modifier.op() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE
            && Math.abs(modifier.amount() - .5 * hardness) < 1e-9,
        "modifier worked out with the material, got " + modifier);
    require(
        part.durability().orElse(-1) == (int) Math.floor(10 * hardness),
        "durability worked out with the material, got " + part.durability());
    require(
        parts.resolve(ResourceLocation.parse("minecraft:piston")).isEmpty(),
        "a file that doesn't decode is skipped");
    var problems = parts.check(BuiltInRegistries.ITEM.keySet());
    require(
        problems.size() == 1 && problems.getFirst().contains("mmdlib_smoke:no_such_item"),
        "only the file naming a missing item is reported, got " + problems);
  }

  private static void require(boolean condition, String what) {
    if (!condition) throw new AssertionError("Machine parts loader smoke failed: " + what);
  }
}
