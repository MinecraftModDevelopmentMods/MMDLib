package zone.moddev.mc.lib.gui.runtime;

import java.util.*;
import java.util.function.Consumer;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.items.*;
import net.neoforged.neoforge.network.PacketDistributor;

import zone.moddev.mc.lib.api.automation.*;
import zone.moddev.mc.lib.api.capabilities.*;
import zone.moddev.mc.lib.gui.layout.AutomationLayout;

/**
 * Per-viewer selection/editor state. Every group keeps fixed real slot indices for the menu
 * lifetime.
 */
public final class AutomationMenu {
  public final AutomationInventory inventory;
  private final MachineMenu menu;
  private final Player owner;
  private final boolean client;
  private final boolean addons;
  private final ItemStackHandler items;
  private final List<Slot> slots = new ArrayList<>();
  private int selected, editor = -1, start, lastRequest, nextRequest, pendingTicks;
  private boolean open, pending, processingAction;
  private long editorGeneration, lastTick = -1;
  private net.minecraft.resources.ResourceLocation editorType;
  private int tickActions;
  private long[] generations, revisions;
  private int[] modes;
  private long lastInventoryRevision = -1;
  private int lastSelected = -1, lastEditor = -2;
  private boolean lastOpen;

  public AutomationMenu(
      MachineMenu menu, AutomationInventory inventory, Player owner, boolean client) {
    this.menu = menu;
    this.inventory = inventory;
    this.owner = owner;
    this.client = client;
    addons = !(menu.blockEntity instanceof ScreenHost host) || host.screenAutomationAddons();
    modes = new int[inventory.groups().size() * 6];
    generations = new long[inventory.groups().size() * 4];
    revisions = new long[generations.length];
    items =
        client
            ? new ItemStackHandler(inventory.slots().getSlots()) {
              @Override
              public int getSlotLimit(int slot) {
                return 1;
              }

              @Override
              public boolean isItemValid(int slot, ItemStack stack) {
                int g = slot / 8, sub = slot % 8;
                if (sub < 4)
                  return AutomationInventory.acceptsAddon(inventory.group(g), this, slot, stack);
                var parent = getStackInSlot(g * 8 + sub - 4);
                var addon = parent.getCapability(AddonCapabilities.ADDON);
                return addon != null
                    && addon.specification(parent).accepts(inventory.group(g))
                    && addon.acceptsFilter(parent, stack);
              }
            }
            : inventory.slots();
  }

  public int slotCount() {
    return addons ? items.getSlots() : 0;
  }

  public boolean addonsEnabled() {
    return addons;
  }

  public int selected() {
    return selected;
  }

  public void closeServer() {
    open = false;
    editor = -1;
  }

  public boolean open() {
    return open;
  }

  public int editor() {
    return editor;
  }

  public boolean pending() {
    return pending;
  }

  public int slotStart() {
    return start;
  }

  public ItemStack addon(int g, int c) {
    return addons ? items.getStackInSlot(g * 8 + c) : ItemStack.EMPTY;
  }

  public ItemStack filter(int g, int c) {
    return addons ? items.getStackInSlot(g * 8 + 4 + c) : ItemStack.EMPTY;
  }

  public long generation(int g, int c) {
    return client ? generations[g * 4 + c] : inventory.generation(g, c);
  }

  public long revision(int g, int c) {
    return client ? revisions[g * 4 + c] : inventory.revision(g, c);
  }

  public IoMode mode(int g, int face) {
    return client
        ? IoMode.values()[modes[g * 6 + face]]
        : inventory.group(g).sides().get(RelativeFace.values()[face]);
  }

  public boolean supportsFilter(int g, int c) {
    var parent = addon(g, c);
    var provider = parent.getCapability(AddonCapabilities.ADDON);
    return provider != null
        && provider.specification(parent).accepts(inventory.group(g))
        && provider.specification(parent).filters();
  }

  public void addSlots(Consumer<Slot> add, int start, int bodyX) {
    this.start = start;
    for (int i = 0; i < slotCount(); i++) {
      final int index = i;
      var bounds = AutomationLayout.item(i % 8).offset(bodyX, AutomationLayout.TOP);
      var slot =
          new SlotItemHandler(items, index, bounds.x(), bounds.y()) {
            @Override
            public boolean isActive() {
              return open
                  && index / 8 == selected
                  && (index % 8 < 4
                      || supportsFilter(index / 8, index % 4)
                      || !filter(index / 8, index % 4).isEmpty())
                  && (!client || !pending);
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
              return isActive() && super.mayPlace(stack);
            }

            @Override
            public boolean mayPickup(Player player) {
              return isActive() && super.mayPickup(player);
            }

            @Override
            public int getMaxStackSize() {
              return 1;
            }

            @Override
            public void setChanged() {
              super.setChanged();
              if (!client) inventory.owner().setChanged();
            }
          };
      slots.add(slot);
      add.accept(slot);
    }
  }

  public void afterInteraction() {
    if (!client) {
      inventory.revalidate();
      inventory.recover(owner);
    }
  }

  public void beforeBroadcast() {
    if (client) return;
    inventory.revalidate();
    if (editor >= 0
        && (!supportsFilter(selected, editor)
            || filter(selected, editor).isEmpty()
            || inventory.generation(selected, editor) != editorGeneration)) editor = -1;
    if (editor >= 0) {
      var filter = filter(selected, editor);
      var provider = filter.getCapability(AddonCapabilities.FILTER);
      if (provider == null || !provider.editorType(filter).equals(editorType)) editor = -1;
    }
  }

  public void tick() {
    if (pending && ++pendingTicks >= 100) {
      pending = false;
      pendingTicks = 0;
    }
  }

  public boolean send(String kind, int group, int column, String control, int entry, String value) {
    if (!client || pending || nextRequest == Integer.MAX_VALUE) return false;
    long generation = column >= 0 && column < 4 ? generation(group, column) : 0;
    long revision = column >= 0 && column < 4 ? revision(group, column) : 0;
    pending = true;
    pendingTicks = 0;
    PacketDistributor.sendToServer(
        new AutomationNetwork.Action(
            menu.containerId,
            menu.widgetSession(),
            ++nextRequest,
            kind,
            group,
            column,
            generation,
            revision,
            control,
            entry,
            value));
    return true;
  }

  public void action(Player player, AutomationNetwork.Action action) {
    if (client
        || player != owner
        || player.containerMenu != menu
        || !menu.stillValid(player)
        || action.menu() != menu.containerId
        || action.session() != menu.widgetSession()
        || action.request() <= lastRequest) return;
    lastRequest = action.request();
    beforeBroadcast();
    long tick = owner.level().getGameTime();
    if (lastTick != tick) {
      lastTick = tick;
      tickActions = 0;
    }
    boolean accepted = false;
    int g = action.group(), c = action.column();
    if (++tickActions <= 8 && g >= 0 && g < inventory.groups().size()) {
      switch (action.kind()) {
        case "select" -> {
          selected = g;
          open = true;
          editor = -1;
          accepted = true;
        }
        case "close" -> {
          open = false;
          editor = -1;
          accepted = true;
        }
        case "face" -> {
          int face = action.entry();
          if (open && g == selected && face >= 0 && face < 6) {
            try {
              var expected = IoMode.valueOf(action.control());
              accepted =
                  inventory
                      .group(g)
                      .cycle(
                          RelativeFace.values()[face], expected, action.value().equals("reverse"));
            } catch (IllegalArgumentException invalid) {
              /* Invalid request. */
            }
          }
        }
        case "editor" -> {
          if (open
              && g == selected
              && c >= 0
              && c < 4
              && supportsFilter(g, c)
              && !filter(g, c).isEmpty()
              && inventory.generation(g, c) == action.generation()) {
            var filter = filter(g, c);
            var provider = filter.getCapability(AddonCapabilities.FILTER);
            if (provider != null) {
              editor = editor == c ? -1 : c;
              editorGeneration = action.generation();
              editorType = provider.editorType(filter);
              accepted = true;
            }
          }
        }
        case "collapse" -> {
          editor = -1;
          accepted = true;
        }
        case "edit" -> {
          if (open
              && g == selected
              && c >= 0
              && c < 4
              && editor == c
              && action.generation() == editorGeneration)
            accepted =
                inventory.edit(
                    g,
                    c,
                    action.generation(),
                    action.revision(),
                    action.control(),
                    action.entry(),
                    action.value(),
                    menu.getCarried(),
                    owner.registryAccess());
        }
        default -> {}
      }
    }
    processingAction = true;
    try {
      if (open && menu.redstone != null) menu.redstone.closeServer();
      menu.broadcastChanges();
    } finally {
      processingAction = false;
    }
    transmit(accepted);
  }

  private AutomationNetwork.State snapshot(boolean accepted) {
    int n = inventory.groups().size();
    int[] modes = new int[n * 6];
    long[] gens = new long[n * 4], revs = new long[n * 4];
    String[] ids = new String[n];
    for (int g = 0; g < n; g++) {
      ids[g] = inventory.group(g).definitionKey();
      for (int f = 0; f < 6; f++)
        modes[g * 6 + f] = inventory.group(g).sides().get(RelativeFace.values()[f]).ordinal();
      for (int c = 0; c < 4; c++) {
        gens[g * 4 + c] = inventory.generation(g, c);
        revs[g * 4 + c] = inventory.revision(g, c);
      }
    }
    return new AutomationNetwork.State(
        menu.containerId,
        menu.widgetSession(),
        lastRequest,
        accepted,
        selected,
        open,
        editor,
        ids,
        modes,
        gens,
        revs);
  }

  private void transmit(boolean accepted) {
    if (owner instanceof net.minecraft.server.level.ServerPlayer player
        && player.connection.hasChannel(AutomationNetwork.State.TYPE))
      PacketDistributor.sendToPlayer(player, snapshot(accepted));
  }

  public void broadcast() {
    if (client || processingAction) return;
    long revision = inventory.viewRevision();
    if (revision != lastInventoryRevision
        || selected != lastSelected
        || open != lastOpen
        || editor != lastEditor) {
      lastInventoryRevision = revision;
      lastSelected = selected;
      lastOpen = open;
      lastEditor = editor;
      transmit(true);
    }
  }

  public void receive(AutomationNetwork.State state) {
    if (!client || state.menu() != menu.containerId || state.session() != menu.widgetSession())
      return;
    if (state.groups().length != inventory.groups().size())
      throw new IllegalStateException("Resource groups differ");
    for (int g = 0; g < state.groups().length; g++)
      if (!state.groups()[g].equals(inventory.group(g).definitionKey()))
        throw new IllegalStateException("Resource group definitions differ");
    selected = state.selected();
    open = state.open();
    editor = state.editor();
    modes = state.modes();
    generations = state.generations();
    revisions = state.revisions();
    if (state.request() == nextRequest) {
      pending = false;
      pendingTicks = 0;
    }
  }
}
