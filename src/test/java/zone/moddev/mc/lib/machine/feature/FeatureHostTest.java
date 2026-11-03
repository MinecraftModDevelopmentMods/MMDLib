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

  /** A feature that syncs and counts which way it was restored. */
  private static class Synced implements MachineFeature<Integer> {
    final String key;
    int value;
    int restores;
    int clientRestores;

    Synced(String key, int value) {
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
      restores++;
      value = state;
    }

    @Override
    public boolean syncsToClient() {
      return true;
    }

    @Override
    public void restoreOnClient(Integer state) {
      clientRestores++;
      value = state;
    }
  }

  @Test
  void clientSaveHasOnlySyncingFeatures() {
    var host = new FeatureHost(() -> {}).add(new Counter("a", 3)).add(new Synced("b", 7));
    var saved = host.saveForClient(JsonOps.INSTANCE).getAsJsonObject();
    assertEquals(1, saved.size());
    assertEquals(7, saved.get("b").getAsInt());
  }

  @Test
  void loadUsesRestoreAndClientLoadUsesRestoreOnClient() {
    var data = new JsonObject();
    data.addProperty("b", 9);
    var feature = new Synced("b", 0);
    var host = new FeatureHost(() -> {}).add(feature);
    host.load(JsonOps.INSTANCE, data);
    assertEquals(1, feature.restores);
    assertEquals(0, feature.clientRestores);
    host.loadOnClient(JsonOps.INSTANCE, data);
    assertEquals(1, feature.restores);
    assertEquals(1, feature.clientRestores);
    assertEquals(9, feature.value);
  }

  @Test
  void restoreOnClientDefaultsToRestore() {
    var feature = new Counter("a", 0);
    feature.restoreOnClient(4);
    assertEquals(4, feature.value);
  }

  @Test
  void clientLoadSkipsFeaturesThatDoNotSync() {
    var quiet = new Counter("a", 1);
    var data = new JsonObject();
    data.addProperty("a", 4);
    new FeatureHost(() -> {}).add(quiet).loadOnClient(JsonOps.INSTANCE, data);
    assertEquals(1, quiet.value);
  }

  @Test
  void badClientDataKeepsState() {
    var a = new Synced("a", 5);
    var b = new Synced("b", 6);
    var data = new JsonObject();
    data.add("a", new JsonPrimitive("nine"));
    data.addProperty("b", 8);
    var host = new FeatureHost(() -> {}).add(a).add(b);
    host.loadOnClient(JsonOps.INSTANCE, data);
    assertEquals(5, a.value);
    assertEquals(8, b.value);
    host.loadOnClient(JsonOps.INSTANCE, JsonNull.INSTANCE);
    assertEquals(8, b.value);
  }

  @Test
  void clientSaveChangesNothing() {
    int[] counts = new int[2];
    var host = new FeatureHost(() -> counts[0]++, () -> counts[1]++).add(new Synced("a", 1));
    host.saveForClient(JsonOps.INSTANCE);
    var data = new JsonObject();
    data.addProperty("a", 2);
    host.loadOnClient(JsonOps.INSTANCE, data);
    assertEquals(0, counts[0]);
    assertEquals(0, counts[1]);
  }

  @Test
  void clientSaveUsesClientStateAndSaveUsesState() {
    var feature =
        new Synced("a", 1) {
          @Override
          public Integer clientState() {
            return 9;
          }
        };
    var host = new FeatureHost(() -> {}).add(feature);
    assertEquals(9, host.saveForClient(JsonOps.INSTANCE).getAsJsonObject().get("a").getAsInt());
    assertEquals(1, host.save(JsonOps.INSTANCE).getAsJsonObject().get("a").getAsInt());
  }
}
