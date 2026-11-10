package zone.moddev.mc.lib.gui.runtime;

import static zone.moddev.mc.lib.api.automation.AutomationInventory.COLUMNS;
import static zone.moddev.mc.lib.api.automation.AutomationInventory.SLOTS_PER_GROUP;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.network.PacketDistributor;

import zone.moddev.mc.lib.api.automation.AddonCapabilities;
import zone.moddev.mc.lib.api.automation.AutomationInventory;
import zone.moddev.mc.lib.api.automation.ResourceGroup;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.ItemInventories;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.gui.layout.AutomationLayout;
import zone.moddev.mc.lib.gui.layout.SideDrawerLayout;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.containers.AutomationInventoryElement;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;

/**
 * Per-viewer selection/editor state. Every group keeps fixed real slot indices for the menu
 * lifetime.
 */
public final class AutomationMenu
    implements
        zone.moddev.mc.lib.gui.runtime.component.MenuSlotContributor,
        zone.moddev.mc.lib.gui.runtime.component.DrawerSession {
    public net.minecraft.resources.ResourceLocation drawerType() {
        return zone.moddev.mc.lib.gui.drawer.BuiltinDrawers.SIDES;
    }

    public void tickClient() {
        tick();
    }

    public int transferPriority() {
        return -100;
    }

    public List<Slot> destinations(ItemStack stack) {
        return slots.stream().filter(slot -> slot.isActive() && slot.mayPlace(stack)).toList();
    }

    public void addSlots(
        zone.moddev.mc.lib.gui.runtime.slot.MenuSlots.Registrar target,
        zone.moddev.mc.lib.gui.layout.ScreenLayout layout) {
        addSlots(
            slot -> {
                var element = slotElement(target.nextIndex());
                target.add(
                    slot,
                    element,
                    element.role(),
                    inventoryView((target.nextIndex() - start) / SLOTS_PER_GROUP).inventory());
            },
            target.nextIndex(),
            layout);
    }

    public final AutomationInventory inventory;
    private final MachineMenu menu;
    private final Player owner;
    private final boolean client;
    private final boolean addons;
    private final IItemHandlerModifiable items;
    private final List<Slot> slots = new ArrayList<>();
    private final List<AutomationInventoryElement> inventoryViews = new ArrayList<>();
    private final java.util.function.Function<ResourceGroup<?>, ? extends AutomationInventoryElement> inventoryFactory;
    private int selected, editor = -1, start;
    private final MenuRequests.Client requests = new MenuRequests.Client(100);
    private final MenuRequests.Server received = new MenuRequests.Server(8);
    private boolean open, processingAction;
    private long editorGeneration;
    private net.minecraft.resources.ResourceLocation editorType;
    private long[] generations, revisions;
    private int[] modes;
    private long lastInventoryRevision = -1;
    private int lastSelected = -1, lastEditor = -2;
    private boolean lastOpen;

    public AutomationMenu(
        MachineMenu menu, AutomationInventory inventory, Player owner, boolean client) {
        this(
            menu,
            inventory,
            owner,
            client,
            group -> menu.blockEntity instanceof ScreenHost host
                ? host.screenAutomationInventory(group)
                : new AutomationInventoryElement(group.id()));
    }

    /**
     * Alternate presentation factory for custom menu contributors, with unchanged storage
     * semantics.
     */
    public AutomationMenu(
        MachineMenu menu,
        AutomationInventory inventory,
        Player owner,
        boolean client,
        java.util.function.Function<ResourceGroup<?>, ? extends AutomationInventoryElement> inventoryFactory) {
        this.inventoryFactory = Objects.requireNonNull(inventoryFactory);
        this.menu = menu;
        this.inventory = inventory;
        this.owner = owner;
        this.client = client;
        addons = !(menu.blockEntity instanceof ScreenHost host) || host.screenAutomationAddons();
        modes = new int[inventory.groups().size() * 6];
        generations = new long[inventory.groups().size() * COLUMNS];
        revisions = new long[generations.length];
        if (client) {
            var handlers = new LinkedHashMap<String, ItemStorage>();
            for (int group = 0; group < inventory.groups().size(); group++) {
                final int g = group;
                handlers.put(
                    inventory.group(g).id(),
                    new ItemStorage(SLOTS_PER_GROUP) {
                        public int getSlotLimit(int slot) {
                            return 1;
                        }

                        public boolean isItemValid(int slot, ItemStack stack) {
                            int index = g * SLOTS_PER_GROUP + slot;
                            if (slot < COLUMNS)
                                return AutomationInventory.acceptsAddon(inventory.group(g), items,
                                    index, stack);
                            var parent = items.getStackInSlot(index - COLUMNS);
                            var addon = parent.getCapability(AddonCapabilities.ADDON);
                            return addon != null
                                && addon.specification(parent).accepts(inventory.group(g))
                                && addon.acceptsFilter(parent, stack);
                        }
                    });
            }
            items = new ItemInventories(handlers);
        } else
            items = inventory.slots();
    }

    public SlotElement slotElement(int menuIndex) {
        int index = menuIndex - start;
        return index < 0 || index >= slots.size()
            ? null
            : inventoryViews.get(index / SLOTS_PER_GROUP).slots().get(index % SLOTS_PER_GROUP);
    }

    public AutomationInventoryElement inventoryView(int group) {
        return inventoryViews.get(group);
    }

    public AutomationInventoryElement.Presentation presentation(int group) {
        return new AutomationInventoryElement.Presentation() {
            public net.minecraft.network.chat.Component name() {
                return inventory.group(group).name().copy();
            }

            public ItemStack contents(int localSlot) {
                if (localSlot < 0 || localSlot >= SLOTS_PER_GROUP)
                    throw new IllegalArgumentException("Invalid automation slot");
                return addons
                    ? items.getStackInSlot(group * SLOTS_PER_GROUP + localSlot).copy()
                    : ItemStack.EMPTY;
            }

            public boolean supportsFilter(int column) {
                return AutomationMenu.this.supportsFilter(group, column);
            }
        };
    }

    /** Local display policy, independent of the current selection or pending request. */
    public boolean slotVisible(int group, int localSlot) {
        if (!addons)
            return false;
        var view = inventoryView(group);
        return view.slotVisible(view.slots().get(localSlot), presentation(group));
    }

    /** Drawer visibility shared with native activation. Pending state only affects interaction. */
    public boolean visible(int group, Element element) {
        if (!addons || !open || group != selected)
            return false;
        var view = inventoryView(group);
        if (element == view)
            return true;
        return view.slots().stream()
            .anyMatch(slot -> slot == element && slotVisible(group, slot.index()));
    }

    public Object drawerValue(int group, Element element, boolean maximum) {
        return inventoryView(group).drawerValue(element, maximum, presentation(group));
    }

    @Override
    public List<AutomationInventoryElement> presentationRoots() {
        return List.copyOf(inventoryViews);
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
        return requests.pending();
    }

    public int slotStart() {
        return start;
    }

    public ItemStack addon(int g, int c) {
        return addons ? items.getStackInSlot(g * SLOTS_PER_GROUP + c) : ItemStack.EMPTY;
    }

    public ItemStack filter(int g, int c) {
        return addons ? items.getStackInSlot(g * SLOTS_PER_GROUP + COLUMNS + c) : ItemStack.EMPTY;
    }

    public long generation(int g, int c) {
        return client ? generations[g * COLUMNS + c] : inventory.generation(g, c);
    }

    public long revision(int g, int c) {
        return client ? revisions[g * COLUMNS + c] : inventory.revision(g, c);
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

    public void addSlots(
        Consumer<Slot> add, int start, zone.moddev.mc.lib.gui.layout.ScreenLayout layout) {
        addSlots(add, start, 0);
        position(layout);
    }

    /** Slot coordinates follow the registered side drawer's geometry. */
    public void position(zone.moddev.mc.lib.gui.layout.ScreenLayout layout) {
        var definition = layout.drawers().stream()
            .filter(d -> d.type().equals(zone.moddev.mc.lib.gui.drawer.BuiltinDrawers.SIDES))
            .findFirst();
        var tab = definition
            .map(
                d -> zone.moddev.mc.lib.gui.layout.DrawerTabs.place(
                    layout.drawers(),
                    layout.panel(),
                    layout.panel().bottom(),
                    entry -> true)
                    .get(d))
            .orElse(new zone.moddev.mc.lib.gui.layout.Rect(-10000, -10000, 0, 0));
        position(
            SideDrawerLayout.bodyX(
                definition
                    .map(d -> d.placement().edge())
                    .orElse(zone.moddev.mc.lib.gui.model.Edge.RIGHT),
                tab,
                AutomationLayout.WIDTH),
            tab.y());
    }

    /** Client drawer supplies its final clipped body origin, relative to the main panel. */
    public void position(int x, int y) {
        for (int i = 0; i < slots.size(); i++) {
            var view = inventoryView(i / SLOTS_PER_GROUP);
            var element = view.slots().get(i % SLOTS_PER_GROUP);
            var position = element
                .itemPosition(view.drawerSlotBounds(i % SLOTS_PER_GROUP).offset(x, y));
            slots.get(i).x = position[0];
            slots.get(i).y = position[1];
        }
    }

    public void addSlots(Consumer<Slot> add, int start, int bodyX) {
        this.start = start;
        for (int i = 0; i < slotCount(); i++) {
            final int index = i;
            int group = index / SLOTS_PER_GROUP;
            if (index % SLOTS_PER_GROUP == 0)
                inventoryViews
                    .add(Objects.requireNonNull(inventoryFactory.apply(inventory.group(group))));
            var view = inventoryView(group);
            if (view.slots().size() != SLOTS_PER_GROUP)
                throw new IllegalArgumentException(
                    "Automation inventory must preserve its slot count");
            var element = view.slots().get(index % SLOTS_PER_GROUP);
            if (element.index() != index % SLOTS_PER_GROUP
                || element.ghost()
                || element.playerInventory())
                throw new IllegalArgumentException(
                    "Automation slots must retain real local storage indexes");
            var bounds = view.drawerSlotBounds(index % SLOTS_PER_GROUP).offset(bodyX,
                AutomationLayout.TOP);
            IItemHandlerModifiable handler = items;
            int local = index;
            if (items instanceof ItemInventories combined) {
                handler = combined.storage(index);
                local = combined.localSlot(index);
            }
            var slot = element.createSlot(
                handler,
                local,
                bounds,
                () -> visible(group, element) && (!client || !pending()),
                menu::interacting,
                () -> {
                    if (!client)
                        inventory.owner().setChanged();
                });
            slots.add(slot);
            add.accept(slot);
        }
    }

    /** Flush unattributed recoveries before this player's synchronous inventory operation. */
    public void beforeInteraction() {
        if (!client) {
            inventory.revalidate();
            inventory.recover(null);
        }
    }

    public void afterInteraction() {
        if (!client) {
            inventory.revalidate();
            inventory.recover(owner);
        }
    }

    public void beforeBroadcast() {
        if (client)
            return;
        inventory.revalidate();
        if (editor >= 0
            && (!supportsFilter(selected, editor)
                || filter(selected, editor).isEmpty()
                || inventory.generation(selected, editor) != editorGeneration))
            editor = -1;
        if (editor >= 0) {
            var filter = filter(selected, editor);
            var provider = filter.getCapability(AddonCapabilities.FILTER);
            if (provider == null || !provider.editorType(filter).equals(editorType))
                editor = -1;
        }
    }

    public void tick() {
        requests.tick();
    }

    public boolean send(String kind, int group, int column, String control, int entry,
        String value) {
        if (!client || pending())
            return false;
        long generation = column >= 0 && column < COLUMNS ? generation(group, column) : 0;
        long revision = column >= 0 && column < COLUMNS ? revision(group, column) : 0;
        int request = requests.begin();
        if (request == 0)
            return false;
        PacketDistributor.sendToServer(
            new AutomationNetwork.Action(
                menu.containerId,
                menu.sessionId(),
                request,
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
            || action.session() != menu.sessionId()
            || !received.receive(action.request()))
            return;
        beforeBroadcast();
        boolean accepted = false;
        int g = action.group(), c = action.column();
        if (received.allow(owner.level().getGameTime()) && g >= 0
            && g < inventory.groups().size()) {
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
                            accepted = inventory
                                .group(g)
                                .cycle(
                                    RelativeFace.values()[face], expected,
                                    action.value().equals("reverse"));
                        } catch (IllegalArgumentException invalid) {
                            /* Invalid request. */
                        }
                    }
                }
                case "editor" -> {
                    if (open
                        && g == selected
                        && c >= 0
                        && c < COLUMNS
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
                        && c < COLUMNS
                        && editor == c
                        && action.generation() == editorGeneration)
                        accepted = inventory.edit(
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
                default -> {
                }
            }
        }
        processingAction = true;
        try {
            if (open)
                menu.components().activate(this);
            menu.broadcastChanges();
        } finally {
            processingAction = false;
        }
        transmit(accepted);
    }

    private AutomationNetwork.State snapshot(boolean accepted) {
        int n = inventory.groups().size();
        int[] modes = new int[n * 6];
        long[] gens = new long[n * COLUMNS], revs = new long[n * COLUMNS];
        String[] ids = new String[n];
        for (int g = 0; g < n; g++) {
            ids[g] = inventory.group(g).definitionKey();
            for (int f = 0; f < 6; f++)
                modes[g * 6 + f] = inventory.group(g).sides().get(RelativeFace.values()[f])
                    .ordinal();
            for (int c = 0; c < COLUMNS; c++) {
                gens[g * COLUMNS + c] = inventory.generation(g, c);
                revs[g * COLUMNS + c] = inventory.revision(g, c);
            }
        }
        return new AutomationNetwork.State(
            menu.containerId,
            menu.sessionId(),
            received.acknowledged(),
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
        lastInventoryRevision = inventory.viewRevision();
        lastSelected = selected;
        lastOpen = open;
        lastEditor = editor;
    }

    public void broadcast() {
        if (client || processingAction)
            return;
        long revision = inventory.viewRevision();
        if (revision != lastInventoryRevision
            || selected != lastSelected
            || open != lastOpen
            || editor != lastEditor) {
            transmit(true);
        }
    }

    public void receive(AutomationNetwork.State state) {
        if (!client || state.menu() != menu.containerId || state.session() != menu.sessionId())
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
        requests.acknowledgeExact(state.request());
    }
}
