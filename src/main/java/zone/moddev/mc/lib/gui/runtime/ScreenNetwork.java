package zone.moddev.mc.lib.gui.runtime;

import java.util.*;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Versioned menu protocol. Every mutation is checked against the sender's current menu. */
public final class ScreenNetwork {
  private ScreenNetwork() {}

  private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String name) {
    return new CustomPacketPayload.Type<>(ResourceLocation.parse("mmdlib:screen_" + name));
  }

  public record Values(int menu, Map<Integer, Object> delta) implements CustomPacketPayload {
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
              for (int i = 0; i < count; i++) values.put(b.readInt(), ScreenValues.read(b));
              return new Values(menu, values);
            });

    public Type<Values> type() {
      return TYPE;
    }
  }

  /** kind: handler, navigation button, context back, or side face. */
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

  public record Ghost(int menu, int element, ItemStack stack) implements CustomPacketPayload {
    public static final Type<Ghost> TYPE = ScreenNetwork.type("ghost");
    public static final StreamCodec<RegistryFriendlyByteBuf, Ghost> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeVarInt(p.menu);
              b.writeVarInt(p.element);
              ItemStack.OPTIONAL_STREAM_CODEC.encode(b, p.stack);
            },
            b ->
                new Ghost(
                    b.readVarInt(), b.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(b)));

    public Type<Ghost> type() {
      return TYPE;
    }
  }

  public static void register(RegisterPayloadHandlersEvent event) {
    var r = event.registrar("1");
    r.playToClient(
        Values.TYPE,
        Values.CODEC,
        (p, c) -> {
          if (c.player().containerMenu instanceof MachineMenu menu && menu.containerId == p.menu())
            menu.apply(p.delta());
        });
    r.playToServer(
        Ghost.TYPE,
        Ghost.CODEC,
        (p, c) -> {
          if (c.player().containerMenu instanceof MachineMenu menu && menu.containerId == p.menu())
            menu.ghost(c.player(), p.element(), p.stack());
        });
  }
}
