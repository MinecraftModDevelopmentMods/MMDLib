package zone.moddev.mc.lib.smoke;

import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonParser;

import com.mojang.serialization.JsonOps;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.machine.internals.InternalsDefinition;
import zone.moddev.mc.lib.machine.internals.MachineParts;
import zone.moddev.mc.lib.machine.neoforge.MachineComponents;
import zone.moddev.mc.lib.machine.neoforge.MachineInternals;
import zone.moddev.mc.lib.machine.neoforge.MachinePartsLoader;

/** Checks machine internals with MMDLib's own gears on a running server. */
final class MachineInternalsSmoke {
  private static final String INTERNALS =
      """
      {
        "open_with": "#c:tools/wrench",
        "swap": "when_idle",
        "sockets": {
          "gears": { "count": 2, "accepts": "#c:gears" },
          "motor": { "count": 1 },
          "coil":  { "count": 1, "optional": true }
        },
        "stats": {
          "speed":           { "base": 1.0, "min": 0.25, "max": 8 },
          "energy_per_tick": { "base": 40, "min": 1 },
          "max_temperature": { "base": 900 }
        }
      }
      """;

  private static final String GEAR =
      """
{
  "part": "mmdlib:gear",
  "socket": "gears",
  "modifiers": [
    { "stat": "speed",           "op": "add_multiplied_base", "amount": "0.08 * ($material.hardness - 3)" },
    { "stat": "energy_per_tick", "op": "add_multiplied_base", "amount": "0.03 * $material.hardness" },
    { "stat": "max_temperature", "op": "add_value",           "amount": 50 }
  ],
  "durability": "3"
}
""";

  private static final String MOTOR =
      """
      {"item": "minecraft:piston", "socket": "motor",
       "modifiers": [{"stat": "speed", "op": "add_multiplied_total", "amount": 0.15}]}
      """;

  private MachineInternalsSmoke() {}

  static void check(ServerLevel level) {
    var previous = MachineParts.current();
    try {
      run(level);
    } finally {
      MachineParts.install(previous);
    }
  }

  private static void run(ServerLevel level) {
    var provider = level.registryAccess();
    var gearId = ResourceLocation.parse("mmdlib:iron_gear");
    require(
        MachinePartsLoader.subject(gearId)
            .orElseThrow()
            .part()
            .orElseThrow()
            .getPath()
            .equals("gear"),
        "iron gear is the mmdlib:gear part");
    double hardness = MMDLib.itemMaterials().get(gearId).properties().hardness();
    double perGear = .08 * (hardness - 3);
    installParts(GEAR);
    var definition = internals(INTERNALS);
    var changes = new int[1];
    var internals =
        new MachineInternals(definition, MachineParts::current).onChange(() -> changes[0]++);
    require(internals.sockets().getSlots() == 4, "one slot per socket position");
    require(internals.missing().equals(Optional.of("gears")), "empty gears reported");
    require(internals.stats().get("speed") == 1, "base speed");

    require(internals.install("gears", 0, gear(2), false).getCount() == 1, "one gear per position");
    require(internals.install("gears", 1, gear(1), false).isEmpty(), "second gear");
    require(changes[0] > 0, "changes reported");
    require(near(internals.stats().get("speed"), 1 + 2 * perGear), "gear speed");
    require(internals.stats().get("max_temperature") == 1000, "gear temperature");
    require(internals.missing().equals(Optional.of("motor")), "motor still missing");
    require(internals.install("gears", 0, gear(1), false).getCount() == 1, "position taken");
    var stone = new ItemStack(Items.STONE);
    require(internals.install("motor", 0, stone, false).getCount() == 1, "stone is no part");
    require(internals.install("motor", 0, gear(1), false).getCount() == 1, "gear is no motor");
    require(
        internals.install("coil", 0, new ItemStack(Items.PISTON), false).getCount() == 1,
        "motor is no coil");
    require(internals.install("motor", 0, new ItemStack(Items.PISTON), false).isEmpty(), "motor");
    require(internals.missing().isEmpty(), "every required socket filled");
    require(near(internals.stats().get("speed"), (1 + 2 * perGear) * 1.15), "motor speed");
    require(internals.install("gears", 9, gear(1), false).getCount() == 1, "no such position");

    require(internals.remove("motor", 0, true).isEmpty(), "no swap while running");
    require(!internals.canSwap(true) && internals.canSwap(false), "when_idle");
    require(internals.remove("motor", 0, false).is(Items.PISTON), "swap while idle");
    var always =
        new MachineInternals(
            internals(INTERNALS.replace("when_idle", "always")), MachineParts::current);
    require(
        always.install("motor", 0, new ItemStack(Items.PISTON), true).isEmpty(), "always swaps");
    require(!internals.canOpen(new ItemStack(Items.STICK)), "needs a wrench");
    var bare =
        new MachineInternals(
            internals(INTERNALS.replace("\"open_with\": \"#c:tools/wrench\",", "")),
            MachineParts::current);
    require(bare.canOpen(ItemStack.EMPTY), "bare hands");

    // Wear: durability 3, so the third point breaks both gears.
    require(internals.wear("gears", 1) == 0, "first wear");
    var worn = internals.sockets().getStackInSlot(definition.slots("gears").get(0));
    require(
        worn.getOrDefault(MachineComponents.PART_WEAR.get(), 0) == 1, "wear stored on the part");
    require(internals.wear("gears", 1) == 0 && internals.wear("gears", 1) == 2, "gears break");
    require(
        internals.missing().equals(Optional.of("gears")), "broken gears leave the socket empty");
    require(
        internals.install("motor", 0, new ItemStack(Items.PISTON), false).isEmpty(), "motor back");
    require(internals.wear("motor", 5) == 0, "parts without durability never wear");
    require(internals.missing().equals(Optional.of("gears")), "motor survives wear");

    // Saved by socket name; parts with no room left are kept as orphans, across saves too.
    internals.install("gears", 0, gear(1), false);
    internals.install("gears", 1, gear(1), false);
    var saved = internals.save(provider);
    var smaller = internals(INTERNALS.replace("\"count\": 2", "\"count\": 1"));
    var loaded = new MachineInternals(smaller, MachineParts::current);
    loaded.load(provider, saved);
    require(
        loaded.sockets().getStackInSlot(smaller.slots("gears").get(0)).is(gear(1).getItem()),
        "gear kept");
    require(loaded.orphans().size() == 1, "extra gear kept as an orphan");
    var reloaded = new MachineInternals(smaller, MachineParts::current);
    reloaded.load(provider, loaded.save(provider));
    require(reloaded.orphans().size() == 1, "orphans survive another save");
    var same = new MachineInternals(definition, MachineParts::current);
    same.load(provider, saved);
    require(same.orphans().isEmpty() && same.missing().isEmpty(), "same layout loads in place");
    require(near(same.stats().get("speed"), (1 + 2 * perGear) * 1.15), "loaded stats");

    // A reload changes the numbers without touching the sockets.
    installParts(GEAR.replace("0.08 *", "0.16 *"));
    require(near(same.stats().get("speed"), (1 + 4 * perGear) * 1.15), "stats follow a reload");

    // A machine that only declares speed ignores the gear's other modifiers.
    var speedOnly =
        new MachineInternals(
            internals("{\"sockets\":{\"gears\":{\"count\":1}},\"stats\":{\"speed\":{\"base\":1}}}"),
            MachineParts::current);
    speedOnly.install("gears", 0, gear(1), false);
    require(speedOnly.stats().values().size() == 1, "undeclared stats ignored");
    require(near(speedOnly.stats().get("speed"), 1 + 2 * perGear), "declared stat applied");
  }

  private static void installParts(String gear) {
    MachineParts.install(
        MachineParts.parse(
            Map.of(
                ResourceLocation.parse("smoke:gear"), JsonParser.parseString(gear),
                ResourceLocation.parse("smoke:motor"), JsonParser.parseString(MOTOR)),
            MachinePartsLoader::subject));
  }

  private static InternalsDefinition internals(String json) {
    return InternalsDefinition.CODEC
        .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
        .getOrThrow();
  }

  private static ItemStack gear(int count) {
    return new ItemStack(
        BuiltInRegistries.ITEM.get(ResourceLocation.parse("mmdlib:iron_gear")), count);
  }

  private static boolean near(double a, double b) {
    return Math.abs(a - b) < 1e-9;
  }

  private static void require(boolean condition, String what) {
    if (!condition) throw new AssertionError("Machine internals smoke failed: " + what);
  }
}
