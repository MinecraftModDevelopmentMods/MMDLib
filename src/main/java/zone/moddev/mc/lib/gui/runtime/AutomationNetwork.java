package zone.moddev.mc.lib.gui.runtime;

import static zone.moddev.mc.lib.api.automation.AutomationInventory.COLUMNS;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.api.automation.AutomationInventory;
import zone.moddev.mc.lib.gui.runtime.component.BuiltinMenuComponents;
import zone.moddev.mc.lib.gui.runtime.network.NetworkRegistry;

/** Dedicated menu-bound protocol for the opt-in drawer and item-provided editor. */
public final class AutomationNetwork {
  public static final String VERSION = "16";

  private AutomationNetwork() {}

  public record Action(
      int menu,
      long session,
      int request,
      String kind,
      int group,
      int column,
      long generation,
      long revision,
      String control,
      int entry,
      String value)
      implements CustomPacketPayload {
    public static final Type<Action> TYPE =
        new Type<>(ResourceLocation.parse("mmdlib:automation_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeVarInt(p.menu);
              b.writeLong(p.session);
              b.writeVarInt(p.request);
              b.writeUtf(p.kind, 16);
              b.writeVarInt(p.group);
              b.writeVarInt(p.column);
              b.writeLong(p.generation);
              b.writeLong(p.revision);
              b.writeUtf(p.control, 64);
              b.writeVarInt(p.entry);
              b.writeUtf(p.value, 256);
            },
            b ->
                new Action(
                    b.readVarInt(),
                    b.readLong(),
                    b.readVarInt(),
                    b.readUtf(16),
                    b.readVarInt(),
                    b.readVarInt(),
                    b.readLong(),
                    b.readLong(),
                    b.readUtf(64),
                    b.readVarInt(),
                    b.readUtf(256)));

    public Type<Action> type() {
      return TYPE;
    }
  }

  public record State(
      int menu,
      long session,
      int request,
      boolean accepted,
      int selected,
      boolean open,
      int editor,
      String[] groups,
      int[] modes,
      long[] generations,
      long[] revisions)
      implements CustomPacketPayload {
    public static final Type<State> TYPE =
        new Type<>(ResourceLocation.parse("mmdlib:automation_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeVarInt(p.menu);
              b.writeLong(p.session);
              b.writeVarInt(p.request);
              b.writeBoolean(p.accepted);
              b.writeVarInt(p.selected);
              b.writeBoolean(p.open);
              b.writeVarInt(p.editor);
              b.writeVarInt(p.groups.length);
              for (String id : p.groups) b.writeUtf(id, 128);
              b.writeVarIntArray(p.modes);
              for (long v : p.generations) b.writeLong(v);
              for (long v : p.revisions) b.writeLong(v);
            },
            b -> {
              int menu = b.readVarInt();
              long session = b.readLong();
              int request = b.readVarInt();
              boolean accepted = b.readBoolean();
              int selected = b.readVarInt();
              boolean open = b.readBoolean();
              int editor = b.readVarInt(), count = b.readVarInt();
              if (count < 1 || count > AutomationInventory.MAX_GROUPS)
                throw new IllegalArgumentException("Groups");
              String[] groups = new String[count];
              for (int i = 0; i < count; i++) groups[i] = b.readUtf(128);
              int[] modes = b.readVarIntArray(count * 6);
              if (modes.length != count * 6) throw new IllegalArgumentException("Face count");
              for (int mode : modes)
                if (mode < 0 || mode > 3) throw new IllegalArgumentException("Face mode");
              long[] generations = new long[count * COLUMNS], revisions = new long[count * COLUMNS];
              for (int i = 0; i < generations.length; i++) generations[i] = b.readLong();
              for (int i = 0; i < revisions.length; i++) revisions[i] = b.readLong();
              if (selected < 0 || selected >= count || editor < -1 || editor >= COLUMNS)
                throw new IllegalArgumentException("Drawer selection");
              return new State(
                  menu,
                  session,
                  request,
                  accepted,
                  selected,
                  open,
                  editor,
                  groups,
                  modes,
                  generations,
                  revisions);
            });

    public Type<State> type() {
      return TYPE;
    }
  }

  public static void register(NetworkRegistry.Registrar registrar) {
    registrar.playToServer(
        Action.TYPE,
        Action.CODEC,
        (p, c) -> {
          if (c.player().containerMenu instanceof MachineMenu menu
              && menu.components().get(BuiltinMenuComponents.AUTOMATION) != null)
            menu.components().get(BuiltinMenuComponents.AUTOMATION).action(c.player(), p);
        });
    registrar.playToClient(
        State.TYPE,
        State.CODEC,
        (p, c) -> {
          if (c.player().containerMenu instanceof MachineMenu menu
              && menu.components().get(BuiltinMenuComponents.AUTOMATION) != null)
            menu.components().get(BuiltinMenuComponents.AUTOMATION).receive(p);
        });
  }
}
