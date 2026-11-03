package zone.moddev.mc.lib.machine.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

class FeatureHostTest {
  /** A feature whose state is one number. */
  private static final class Counter implements MachineFeature<Integer> {
    private final String key;
    int value;

    Counter(String key, int value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public String key() {
      return key;
    }

    @Override
    public Codec<Integer> codec() {
      return Codec.intRange(0, 100);
    }

    @Override
    public Integer state() {
      return value;
    }

    @Override
    public void restore(Integer state) {
      value = state;
    }
  }

  @Test
  void savesAndLoadsByKey() {
    var host = new FeatureHost(() -> {}).add(new Counter("a", 3)).add(new Counter("b", 7));
    JsonElement saved = host.save(JsonOps.INSTANCE);
    assertEquals(3, saved.getAsJsonObject().get("a").getAsInt());
    assertEquals(7, saved.getAsJsonObject().get("b").getAsInt());

    var a = new Counter("a", 0);
    var b = new Counter("b", 0);
    new FeatureHost(() -> {}).add(a).add(b).load(JsonOps.INSTANCE, saved);
    assertEquals(3, a.value);
    assertEquals(7, b.value);
  }

  @Test
  void duplicateKeyIsRefused() {
    var host = new FeatureHost(() -> {}).add(new Counter("a", 0));
    assertThrows(IllegalArgumentException.class, () -> host.add(new Counter("a", 1)));
  }

  @Test
  void missingDataKeepsState() {
    var a = new Counter("a", 5);
    var b = new Counter("b", 6);
    var data = new JsonObject();
    data.addProperty("a", 9);
    new FeatureHost(() -> {}).add(a).add(b).load(JsonOps.INSTANCE, data);
    assertEquals(9, a.value);
    assertEquals(6, b.value);

    new FeatureHost(() -> {}).add(a).load(JsonOps.INSTANCE, JsonNull.INSTANCE);
    assertEquals(9, a.value);
  }

  @Test
  void badDataKeepsState() {
    var a = new Counter("a", 5);
    var b = new Counter("b", 6);
    var data = new JsonObject();
    data.add("a", new JsonPrimitive("nine"));
    data.addProperty("b", 1000);
    new FeatureHost(() -> {}).add(a).add(b).load(JsonOps.INSTANCE, data);
    assertEquals(5, a.value);
    assertEquals(6, b.value);
  }

  @Test
  void dataForAnUnknownKeyIsIgnored() {
    var a = new Counter("a", 5);
    var data = new JsonObject();
    data.addProperty("gone", 1);
    data.addProperty("a", 2);
    new FeatureHost(() -> {}).add(a).load(JsonOps.INSTANCE, data);
    assertEquals(2, a.value);
  }
}
