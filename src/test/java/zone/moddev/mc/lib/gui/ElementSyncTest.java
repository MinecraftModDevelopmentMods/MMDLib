package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;

import zone.moddev.mc.lib.gui.client.*;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.*;
import zone.moddev.mc.lib.gui.runtime.ScreenNetwork;
import zone.moddev.mc.lib.gui.sync.*;

class ElementSyncTest {
  private static final ElementSync<Integer> COUNT =
      ElementSync.field(
              ResourceLocation.parse("test:count"),
              StreamCodec.of(
                  (buffer, value) -> buffer.writeVarInt(value), buffer -> buffer.readVarInt()),
              5,
              element -> 0)
          .validate((element, value) -> value >= 0 && value <= 10)
          .affectsLayout()
          .build();
  private static final ElementSync<List<Integer>> LIST =
      ElementSync.field(
              ResourceLocation.parse("test:list"),
              StreamCodec.<RegistryFriendlyByteBuf, List<Integer>>of(
                  (buffer, value) -> {
                    buffer.writeVarInt(value.size());
                    value.forEach(buffer::writeVarInt);
                  },
                  buffer -> {
                    int count = buffer.readVarInt();
                    if (count < 0 || count > 10)
                      throw new IllegalArgumentException("Too many items");
                    var values = new ArrayList<Integer>();
                    for (int i = 0; i < count; i++) values.add(buffer.readVarInt());
                    return values;
                  }),
              51,
              element -> new ArrayList<Integer>())
          .build();

  record ModElement(Common common, List<ElementSync<?>> synchronizedValues)
      implements SynchronizedElement {
    public int[] measure(TextMeasure text) {
      return new int[] {10, 10};
    }
  }

  private static ModElement element(ElementSync<?>... fields) {
    return new ModElement(Common.empty(), List.of(fields));
  }

  private static ElementStates store(List<Element> elements, Runnable layout) {
    return new ElementStates(elements, RegistryAccess.EMPTY, layout);
  }

  @Test
  void menusAndEqualElementsHaveIndependentStateAndBatchLayoutNotification() {
    var first = element(COUNT);
    var second = element(COUNT);
    assertEquals(first, second);
    var layouts = new AtomicInteger();
    var server = store(List.of(first, second), () -> {});
    var client = store(List.of(first, second), layouts::incrementAndGet);
    var otherViewer = store(List.of(first, second), () -> {});
    server.set(first, COUNT, 3);
    server.set(second, COUNT, 8);
    client.apply(server.snapshot());
    assertEquals(3, client.get(first, COUNT));
    assertEquals(8, client.get(second, COUNT));
    assertEquals(0, otherViewer.get(first, COUNT));
    assertEquals(1, layouts.get());
    client.apply(server.snapshot());
    assertEquals(1, layouts.get());
  }

  @Test
  void mutableValuesAndWireArraysCannotMutateStoredState() {
    var element = element(LIST);
    var state = store(List.of(element), () -> {});
    var input = new ArrayList<>(List.of(1, 2));
    state.set(element, LIST, input);
    input.clear();
    state.get(element, LIST).clear();
    var encoded = state.snapshot().values().iterator().next();
    encoded.data()[0] = 0;
    assertEquals(List.of(1, 2), state.get(element, LIST));
    assertFalse(state.set(element, LIST, List.of(1, 2)));
  }

  @Test
  void invalidBatchIsAtomicAndRejectsTrailingAndOutOfRangeData() {
    var first = element(COUNT);
    var second = element(COUNT);
    var layouts = new AtomicInteger();
    var state = store(List.of(first, second), layouts::incrementAndGet);
    var addresses = new ArrayList<>(state.snapshot().keySet());
    var delta = new LinkedHashMap<Integer, Object>();
    delta.put(addresses.get(0), COUNT.encode(RegistryAccess.EMPTY, first, 4));
    delta.put(addresses.get(1), new ElementStateValue(new byte[] {11}));
    assertThrows(IllegalArgumentException.class, () -> state.apply(delta));
    assertEquals(0, state.get(first, COUNT));
    assertEquals(0, state.get(second, COUNT));
    assertEquals(0, layouts.get());
    assertThrows(
        IllegalArgumentException.class,
        () -> state.apply(Map.of(addresses.getFirst(), new ElementStateValue(new byte[] {1, 0}))));
    assertThrows(
        IllegalArgumentException.class, () -> state.apply(Map.of(addresses.getFirst(), 1)));
    assertThrows(IllegalArgumentException.class, () -> state.set(first, COUNT, -1));
  }

  @Test
  void stateDeclarationsAndBudgetsAreChecked() {
    assertThrows(
        IllegalArgumentException.class, () -> store(List.of(element(COUNT, COUNT)), () -> {}));
    var plain = element();
    var state = store(List.of(plain), () -> {});
    assertThrows(IllegalArgumentException.class, () -> state.get(plain, COUNT));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ElementStateValue(new byte[ElementSync.MAX_BYTES + 1]));
    var values = new HashMap<Integer, ElementStateValue>();
    for (int i = 0; i < 5; i++)
      values.put(i, new ElementStateValue(new byte[ElementSync.MAX_BYTES]));
    assertThrows(IllegalArgumentException.class, () -> ElementStates.checkBudget(values));
  }

  @Test
  void confirmedSnapshotRestoresPredictionIncludingBeforeFirstBroadcast() {
    var element = element(COUNT);
    var state = store(List.of(element), () -> {});
    state.set(element, COUNT, 6);
    state.apply(state.sent(Map.of()));
    assertEquals(0, state.get(element, COUNT));
    state.set(element, COUNT, 3);
    var confirmed = new HashMap<Integer, Object>(state.snapshot());
    state.set(element, COUNT, 6);
    state.apply(state.sent(confirmed));
    assertEquals(3, state.get(element, COUNT));
  }

  @Test
  void typedStateSurvivesDeltasAndActionAcknowledgments() {
    var element = element(COUNT, LIST);
    var state = store(List.of(element), () -> {});
    state.set(element, COUNT, 7);
    state.set(element, LIST, List.of(4, 5));
    var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    try {
      var ack = new ScreenNetwork.WidgetResult(1, 2, 0, 3, false, null, null, state.snapshot());
      ScreenNetwork.WidgetResult.CODEC.encode(buffer, ack);
      assertEquals(ack, ScreenNetwork.WidgetResult.CODEC.decode(buffer));
      var delta = new ScreenNetwork.Values(1, new HashMap<>(state.snapshot()));
      ScreenNetwork.Values.CODEC.encode(buffer, delta);
      assertEquals(delta, ScreenNetwork.Values.CODEC.decode(buffer));
      assertFalse(buffer.isReadable());
    } finally {
      buffer.release();
    }
  }

  @Test
  void deltaTransportEnforcesAggregateStateBudget() {
    var oversized = new HashMap<Integer, Object>();
    for (int i = 0; i < 5; i++)
      oversized.put(i, new ElementStateValue(new byte[ElementSync.MAX_BYTES]));
    assertThrows(IllegalArgumentException.class, () -> new ScreenNetwork.Values(1, oversized));
    var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    try {
      buffer.writeVarInt(1);
      buffer.writeVarInt(oversized.size());
      oversized.forEach(
          (address, value) -> {
            buffer.writeInt(address);
            zone.moddev.mc.lib.gui.runtime.ScreenValues.write(buffer, value);
          });
      assertThrows(IllegalArgumentException.class, () -> ScreenNetwork.Values.CODEC.decode(buffer));
    } finally {
      buffer.release();
    }
  }

  @Test
  void optionalCapabilitiesWorkDirectlyAndThroughAdapters() {
    class Label implements ElementBehavior, ValueLabelProvider {
      public Optional<Text> valueLabel() {
        return Optional.of(new Text("test:label", Optional.empty()));
      }
    }
    var label = new Label();
    class Adapter implements ElementBehavior, BehaviorCapabilities {
      public <T> Optional<T> capability(Class<T> type) {
        return BehaviorCapabilities.find(label, type);
      }
    }
    assertSame(label, BehaviorCapabilities.find(label, ValueLabelProvider.class).orElseThrow());
    assertSame(
        label, BehaviorCapabilities.find(new Adapter(), ValueLabelProvider.class).orElseThrow());
    assertTrue(BehaviorCapabilities.find(new Adapter(), SideConfigPresentation.class).isEmpty());
    assertTrue(BehaviorCapabilities.find(new ElementBehavior() {}, WidgetRenderer.class).isEmpty());
  }
}
