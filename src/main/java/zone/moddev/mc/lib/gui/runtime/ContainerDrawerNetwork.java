package zone.moddev.mc.lib.gui.runtime;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Bounded snapshots and session/revision-bound requests for generic equipped containers. */
public final class ContainerDrawerNetwork {
  private ContainerDrawerNetwork() {}

  public record Request(
      int menu,
      boolean equipment,
      long session,
      long revision,
      int request,
      int kind,
      int argument,
      int pageSize,
      ItemStack carried,
      List<Integer> slots)
      implements CustomPacketPayload {
    public Request(
        int menu,
        long session,
        long revision,
        int request,
        int kind,
        int argument,
        int pageSize,
        ItemStack carried) {
      this(menu, false, session, revision, request, kind, argument, pageSize, carried, List.of());
    }

    public Request(
        int menu,
        boolean equipment,
        long session,
        long revision,
        int request,
        int kind,
        int argument,
        int pageSize,
        ItemStack carried) {
      this(
          menu, equipment, session, revision, request, kind, argument, pageSize, carried,
          List.of());
    }

    public Request {
      slots = List.copyOf(slots);
      if (revision < 0
          || request < 1
          || kind < 0
          || kind > ContainerDrawerMenu.SWAP
          || argument < 0
          || argument >= ContainerDrawerMenu.MAX_SLOTS * 2
          || pageSize < 1
          || pageSize > ContainerDrawerMenu.MAX_PAGE
          || slots.size() > ContainerDrawerMenu.MAX_PAGE + 36
          || slots.stream().anyMatch(n -> n < 0 || n >= ContainerDrawerMenu.MAX_SLOTS + 36))
        throw new IllegalArgumentException("Invalid container drawer request");
    }

    public static final Type<Request> TYPE =
        new Type<>(ResourceLocation.parse("mmdlib:container_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeVarInt(p.menu);
              b.writeBoolean(p.equipment);
              b.writeLong(p.session);
              b.writeVarLong(p.revision);
              b.writeVarInt(p.request);
              b.writeVarInt(p.kind);
              b.writeVarInt(p.argument);
              b.writeVarInt(p.pageSize);
              ItemStack.OPTIONAL_STREAM_CODEC.encode(b, p.carried);
              b.writeVarInt(p.slots.size());
              for (int slot : p.slots) b.writeVarInt(slot);
            },
            b ->
                new Request(
                    b.readVarInt(),
                    b.readBoolean(),
                    b.readLong(),
                    b.readVarLong(),
                    b.readVarInt(),
                    b.readVarInt(),
                    b.readVarInt(),
                    b.readVarInt(),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(b),
                    readSlots(b)));

    public Type<Request> type() {
      return TYPE;
    }
  }

  private static List<Integer> readSlots(RegistryFriendlyByteBuf b) {
    int count = boundedCount(b, ContainerDrawerMenu.MAX_PAGE + 36);
    var slots = new ArrayList<Integer>(count);
    for (int i = 0; i < count; i++) slots.add(b.readVarInt());
    return slots;
  }

  public record State(
      int menu,
      boolean equipment,
      long session,
      long revision,
      int acknowledged,
      boolean open,
      int selected,
      int offset,
      int pageSize,
      List<ItemStack> icons,
      List<String> keys,
      List<Integer> sizes,
      List<ItemStack> contents,
      List<EquippedContainers.SlotInfo> descriptions)
      implements CustomPacketPayload {
    public State {
      descriptions = List.copyOf(descriptions);
      icons = List.copyOf(icons);
      keys = List.copyOf(keys);
      sizes = List.copyOf(sizes);
      contents = List.copyOf(contents);
      if ((!descriptions.isEmpty() && descriptions.size() != contents.size())
          || descriptions.size() > ContainerDrawerMenu.MAX_PAGE
          || (!equipment && !descriptions.isEmpty())
          || revision < 0
          || acknowledged < 0
          || pageSize < 1
          || pageSize > ContainerDrawerMenu.MAX_PAGE
          || icons.size() > ContainerDrawerMenu.MAX_CONTAINERS
          || icons.size() != sizes.size()
          || icons.size() != keys.size()
          || keys.stream().anyMatch(k -> k.isEmpty() || k.length() > 256)
          || selected < 0
          || selected >= Math.max(1, icons.size())
          || offset < 0
          || offset >= ContainerDrawerMenu.MAX_SLOTS
          || contents.size() > pageSize
          || sizes.stream().anyMatch(n -> n < 1 || n > ContainerDrawerMenu.MAX_SLOTS)
          || (open && icons.isEmpty()))
        throw new IllegalArgumentException("Invalid container snapshot");
    }

    public static final Type<State> TYPE =
        new Type<>(ResourceLocation.parse("mmdlib:container_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeVarInt(p.menu);
              b.writeBoolean(p.equipment);
              b.writeLong(p.session);
              b.writeVarLong(p.revision);
              b.writeVarInt(p.acknowledged);
              b.writeBoolean(p.open);
              b.writeVarInt(p.selected);
              b.writeVarInt(p.offset);
              b.writeVarInt(p.pageSize);
              b.writeVarInt(p.icons.size());
              for (int i = 0; i < p.icons.size(); i++) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(b, p.icons.get(i));
                b.writeVarInt(p.sizes.get(i));
                b.writeUtf(p.keys.get(i), 256);
              }
              b.writeVarInt(p.contents.size());
              for (var stack : p.contents) ItemStack.OPTIONAL_STREAM_CODEC.encode(b, stack);
              b.writeVarInt(p.descriptions.size());
              for (var info : p.descriptions) {
                b.writeUtf(info.identifier(), 128);
                b.writeVarInt(info.index());
                b.writeBoolean(info.cosmetic());
                b.writeBoolean(info.available());
                b.writeResourceLocation(info.icon());
              }
            },
            b -> {
              int menu = b.readVarInt();
              boolean equipment = b.readBoolean();
              long session = b.readLong(), revision = b.readVarLong();
              int ack = b.readVarInt();
              boolean open = b.readBoolean();
              int selected = b.readVarInt(), offset = b.readVarInt(), page = b.readVarInt();
              int count = boundedCount(b, ContainerDrawerMenu.MAX_CONTAINERS);
              var icons = new ArrayList<ItemStack>();
              var sizes = new ArrayList<Integer>();
              var keys = new ArrayList<String>();
              for (int i = 0; i < count; i++) {
                icons.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(b));
                sizes.add(b.readVarInt());
                keys.add(b.readUtf(256));
              }
              int length = boundedCount(b, ContainerDrawerMenu.MAX_PAGE);
              var contents = new ArrayList<ItemStack>();
              for (int i = 0; i < length; i++)
                contents.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(b));
              int descriptionCount = boundedCount(b, ContainerDrawerMenu.MAX_PAGE);
              var descriptions = new ArrayList<EquippedContainers.SlotInfo>();
              for (int i = 0; i < descriptionCount; i++)
                descriptions.add(
                    new EquippedContainers.SlotInfo(
                        b.readUtf(128),
                        b.readVarInt(),
                        b.readBoolean(),
                        b.readBoolean(),
                        b.readResourceLocation()));
              return new State(
                  menu,
                  equipment,
                  session,
                  revision,
                  ack,
                  open,
                  selected,
                  offset,
                  page,
                  icons,
                  keys,
                  sizes,
                  contents,
                  descriptions);
            });

    public Type<State> type() {
      return TYPE;
    }
  }

  private static int boundedCount(RegistryFriendlyByteBuf buffer, int max) {
    int count = buffer.readVarInt();
    if (count < 0 || count > max)
      throw new IllegalArgumentException("Container snapshot too large");
    return count;
  }

  public static void register(PayloadRegistrar registrar) {
    registrar.playToServer(
        Request.TYPE,
        Request.CODEC,
        (p, c) -> {
          if (c.player().containerMenu instanceof MachineMenu menu)
            (p.equipment() ? menu.curiosDrawer : menu.containerDrawer).action(c.player(), p);
        });
    registrar.playToClient(
        State.TYPE,
        State.CODEC,
        (p, c) -> {
          if (c.player().containerMenu instanceof MachineMenu menu)
            (p.equipment() ? menu.curiosDrawer : menu.containerDrawer).receive(p);
        });
  }
}
