package zone.moddev.mc.lib.gui.runtime;

import java.util.*;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.runtime.network.NetworkRegistry;
import zone.moddev.mc.lib.gui.sync.*;

/** Versioned menu protocol. Every mutation is checked against the sender's current menu. */
public final class ScreenNetwork {
  public static final String VERSION = "20";

  private ScreenNetwork() {}

  private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String name) {
    return new CustomPacketPayload.Type<>(ResourceLocation.parse("mmdlib:screen_" + name));
  }

  public record Values(int menu, Map<Integer, Object> delta) implements CustomPacketPayload {
    public Values {
      delta = Collections.unmodifiableMap(new HashMap<>(delta));
      var states = new HashMap<Integer, ElementStateValue>();
      delta.forEach(
          (address, value) -> {
            if (value instanceof ElementStateValue state) states.put(address, state);
          });
      ElementStates.checkBudget(states);
    }

    public static final Type<Values> TYPE = ScreenNetwork.type("values");
    public static final StreamCodec<RegistryFriendlyByteBuf, Values> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeVarInt(p.menu);
              b.writeVarInt(p.delta.size());
              p.delta.forEach(
                  (k, v) -> {
                    b.writeInt(k);
                    ScreenValues.write(b, v);
                  });
            },
            b -> {
              int menu = b.readVarInt(), count = b.readVarInt();
              if (count < 0 || count > 65536)
                throw new IllegalArgumentException("Too many GUI values");
              var values = new HashMap<Integer, Object>();
              long stateBytes = 0;
              for (int i = 0; i < count; i++) {
                int address = b.readInt();
                var value = ScreenValues.read(b);
                if (value instanceof ElementStateValue state) {
                  stateBytes += state.size() + 8L;
                  if (stateBytes > ElementStates.MAX_TOTAL_BYTES)
                    throw new IllegalArgumentException(
                        "Element states exceed the screen byte budget");
                }
                values.put(address, value);
              }
              return new Values(menu, values);
            });

    public Type<Values> type() {
      return TYPE;
    }
  }

  /** Legacy packed button actions. Elements map supported kinds to their declared typed actions. */
  public record Action(int menu, int element, int kind, int button, int modifiers, int option) {
    public int pack() {
      if (element < 0
          || element > 4095
          || kind < 0
          || kind > 3
          || button < 0
          || button > 1
          || modifiers < 0
          || modifiers > 7
          || option < 0
          || option > 4095)
        throw new IllegalArgumentException("GUI button outside protocol limits");
      return element | kind << 12 | button << 14 | modifiers << 15 | option << 18;
    }

    public static Action unpack(int menu, int packed) {
      return new Action(
          menu,
          packed & 4095,
          (packed >>> 12) & 3,
          (packed >>> 14) & 1,
          (packed >>> 15) & 7,
          (packed >>> 18) & 4095);
    }
  }

  /**
   * Session-bound typed element action. The original widget wire ID is retained for compatibility.
   */
  public record Widget(
      int menu, long session, int element, int request, ResourceLocation action, byte[] data)
      implements CustomPacketPayload {
    public Widget {
      if (element < 0
          || element > 4095
          || request < 1
          || data.length > zone.moddev.mc.lib.gui.ElementAction.MAX_BYTES)
        throw new IllegalArgumentException("Widget action outside protocol limits");
      Objects.requireNonNull(action);
      data = data.clone();
    }

    @Override
    public byte[] data() {
      return data.clone();
    }

    public static final Type<Widget> TYPE = ScreenNetwork.type("widget");
    public static final StreamCodec<RegistryFriendlyByteBuf, Widget> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeVarInt(p.menu);
              b.writeLong(p.session);
              b.writeVarInt(p.element);
              b.writeVarInt(p.request);
              b.writeResourceLocation(p.action);
              b.writeByteArray(p.data);
            },
            b ->
                new Widget(
                    b.readVarInt(),
                    b.readLong(),
                    b.readVarInt(),
                    b.readVarInt(),
                    b.readResourceLocation(),
                    b.readByteArray(zone.moddev.mc.lib.gui.ElementAction.MAX_BYTES)));

    public Type<Widget> type() {
      return TYPE;
    }
  }

  /** Acknowledges the edit with fresh authoritative values, including on rejection. */
  public record WidgetResult(
      int menu,
      long session,
      int element,
      int request,
      boolean accepted,
      Object value,
      Object maximum,
      Map<Integer, ElementStateValue> states)
      implements CustomPacketPayload {
    public WidgetResult {
      states = Map.copyOf(states);
      ElementStates.checkBudget(states);
    }

    public WidgetResult(
        int menu,
        long session,
        int element,
        int request,
        boolean accepted,
        Object value,
        Object maximum) {
      this(menu, session, element, request, accepted, value, maximum, Map.of());
    }

    public static final Type<WidgetResult> TYPE = ScreenNetwork.type("widget_result");
    public static final StreamCodec<RegistryFriendlyByteBuf, WidgetResult> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeVarInt(p.menu);
              b.writeLong(p.session);
              b.writeVarInt(p.element);
              b.writeVarInt(p.request);
              b.writeBoolean(p.accepted);
              ScreenValues.write(b, p.value);
              ScreenValues.write(b, p.maximum);
              writeStates(b, p.states);
            },
            b ->
                new WidgetResult(
                    b.readVarInt(),
                    b.readLong(),
                    b.readVarInt(),
                    b.readVarInt(),
                    b.readBoolean(),
                    ScreenValues.read(b),
                    ScreenValues.read(b),
                    readStates(b)));

    public Type<WidgetResult> type() {
      return TYPE;
    }
  }

  private static void writeStates(
      RegistryFriendlyByteBuf buffer, Map<Integer, ElementStateValue> states) {
    ElementStates.checkBudget(states);
    buffer.writeVarInt(states.size());
    states.forEach(
        (address, value) -> {
          buffer.writeInt(address);
          buffer.writeByteArray(value.data());
        });
  }

  private static Map<Integer, ElementStateValue> readStates(RegistryFriendlyByteBuf buffer) {
    int count = buffer.readVarInt();
    if (count < 0 || count > ElementStates.MAX_FIELDS)
      throw new IllegalArgumentException("Too many element states");
    var result = new LinkedHashMap<Integer, ElementStateValue>();
    long bytes = 0;
    for (int i = 0; i < count; i++) {
      int address = buffer.readInt();
      var value = new ElementStateValue(buffer.readByteArray(ElementSync.MAX_BYTES));
      bytes += value.size() + 8L;
      if (bytes > ElementStates.MAX_TOTAL_BYTES || result.putIfAbsent(address, value) != null)
        throw new IllegalArgumentException("Invalid element state envelope");
    }
    return Map.copyOf(result);
  }

  public static void register(NetworkRegistry.Registrar r) {
    r.playToServer(
        Widget.TYPE,
        Widget.CODEC,
        (p, c) -> {
          if (c.player() instanceof net.minecraft.server.level.ServerPlayer player
              && player.containerMenu instanceof MachineMenu menu) {
            var result = menu.elementAction(player, p);
            if (result != null)
              net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, result);
          }
        });
    r.playToClient(
        WidgetResult.TYPE,
        WidgetResult.CODEC,
        (p, c) -> {
          if (c.player().containerMenu instanceof MachineMenu menu) menu.receiveElementResult(p);
        });
    r.playToClient(
        Values.TYPE,
        Values.CODEC,
        (p, c) -> {
          if (c.player().containerMenu instanceof MachineMenu menu && menu.containerId == p.menu())
            menu.apply(p.delta());
        });
  }
}
