package zone.moddev.mc.lib.compat.CraftTweaker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import zone.moddev.mc.lib.common.types.FactoryData;
import zone.moddev.mc.lib.common.runtime.StandardItems;

final class ScriptToolSettingsTest {
  @Test
  void typedSettingsDecodeThroughRuntimeCodecAndSnapshotIndependently() {
    var settings = new ScriptToolSettings();
    settings.setMineable("minecraft:mineable/pickaxe");
    settings.setAttackDamage(4);
    settings.abilities("pickaxe").durability(0.5, 1, "").speed(0.25, 1, "custom_speed");
    var snapshot = settings.json();
    settings.setAttackDamage(9);
    var decoded = new FactoryData(snapshot).decode(StandardItems.ToolSettings.CODEC).getOrThrow();
    assertEquals(4, decoded.attackDamage());
    assertEquals(0.5, decoded.durability().toolFactor());
    assertTrue(decoded.durability().stat().isEmpty());
    assertEquals("custom_speed", decoded.speed().stat().orElseThrow());
    assertFalse(decoded.abilities().isEmpty());
    assertFalse(snapshot.has("fast_on"), "omitted keys must preserve module configuration");
  }

  @Test
  void rejectsInvalidScalesAndNonfiniteAttributesBeforeWriting() {
    var settings = new ScriptToolSettings();
    assertThrows(IllegalArgumentException.class, () -> settings.speed(-1, 0, ""));
    assertThrows(IllegalArgumentException.class, () -> settings.durability(1, 0, ""));
    assertThrows(IllegalArgumentException.class, () -> settings.setAttackSpeed(Float.NaN));
    assertThrows(IllegalArgumentException.class, () -> settings.abilities(" "));
    assertTrue(settings.json().entrySet().isEmpty());
  }
}
