package zone.moddev.mc.lib.gui.runtime;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.network.PacketDistributor;

import zone.moddev.mc.lib.api.capabilities.ItemInventories;
import zone.moddev.mc.lib.gui.ElementAction;
import zone.moddev.mc.lib.gui.ElementActions;
import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.gui.WidgetAction;
import zone.moddev.mc.lib.gui.layout.ScreenLayout;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.load.ScreenBinder;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.containers.NavigableElement;
import zone.moddev.mc.lib.gui.model.containers.SelectiveContainer;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.gui.runtime.component.InventoryAccess;
import zone.moddev.mc.lib.gui.runtime.component.MenuComponentRegistry;
import zone.moddev.mc.lib.gui.runtime.component.MenuComponents;
import zone.moddev.mc.lib.gui.runtime.slot.MenuSlots;
import zone.moddev.mc.lib.gui.sync.ElementStates;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;

/**
 * Shared menu transport and vanilla coordination; inventory elements assemble their native slots.
 */
public class MachineMenu extends AbstractContainerMenu {
    public ItemStack slotHint(SlotElement slot) {
        return components.requireCapability(InventoryAccess.class).hint(slot);
    }

    public final BlockEntity blockEntity;
    public final ScreenBinder.Bound bound;
    public final List<Element> elements;
    private final ElementStates elementStates;
    private final Map<Element, Boolean> visibility = new IdentityHashMap<>();
    private final Map<Element, Integer> elementIndexes = new IdentityHashMap<>();
    private final Map<Integer, Object> values = new HashMap<>();
    private final ScreenHost host;
    private final net.minecraft.core.BlockPos interactionAnchor;
    private final String interactionIdentity;
    private final Player owner;
    private final MenuComponents components;
    private final MenuSlots nativeSlots = new MenuSlots();

    public MenuComponents components() {
        return components;
    }

    public MenuSlots nativeSlots() {
        return nativeSlots;
    }

    private boolean initial = true;
    private boolean interacting;

    public boolean interacting() {
        return interacting;
    }

    private final ElementActionSession elementActions;

    public MachineMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(id, inventory, readEntity(inventory, data), data.readLong(), data.readBlockPos());
    }

    public MachineMenu(int id, Inventory inventory, BlockEntity entity, long session,
        net.minecraft.core.BlockPos anchor) {
        this(id, inventory, entity, inventory.player.level().isClientSide, session, anchor);
    }

    public MachineMenu(int id, Inventory inventory, BlockEntity entity, long session) {
        this(id, inventory, entity, inventory.player.level().isClientSide, session);
    }

    /** Identity shared by this menu's action and feature sessions. */
    public long sessionId() {
        return elementActions.id();
    }

    public <T> Optional<ScreenNetwork.Widget> prepareElementAction(
        Element element, ElementAction<T> action, T value) {
        return elementActions.prepare(element, action, value);
    }

    public boolean elementPending(Element element) {
        return elementActions.pending(element);
    }

    public void receiveElementResult(ScreenNetwork.WidgetResult result) {
        elementActions.receive(result);
    }

    public ScreenNetwork.WidgetResult takeElementResult(Element element) {
        return elementActions.takeResult(element);
    }

    /** Advance pending requests even while another screen temporarily covers this menu. */
    public void tickClientActions() {
        tickElementActions();
        components.tickClient();
    }

    public void tickElementActions() {
        elementActions.tickClient();
    }

    public ScreenNetwork.WidgetResult elementAction(
        net.minecraft.server.level.ServerPlayer player, ScreenNetwork.Widget packet) {
        return elementActions.handle(player, packet);
    }

    boolean authorizesElementAction(net.minecraft.server.level.ServerPlayer player) {
        return player == owner && player.containerMenu == this && stillValid(player);
    }

    ElementActionSession.Snapshot snapshotAfterElementAction(boolean accepted) {
        if (accepted)
            blockEntity.setChanged();
        ScreenSnapshots.invalidate(blockEntity);
        return new ElementActionSession.Snapshot(
            ScreenSnapshots.read(blockEntity, bound), elementStates.sample(blockEntity, owner));
    }

    /** Compatibility alias; all element actions share the same menu session. */
    public long widgetSession() {
        return sessionId();
    }

    /** Compatibility alias for the generic element-action path. */
    public <T> Optional<ScreenNetwork.Widget> prepareWidgetAction(
        zone.moddev.mc.lib.gui.model.elements.CustomElement element,
        WidgetAction<T> action,
        T value) {
        return prepareElementAction(element, action, value);
    }

    /** Compatibility alias for the generic element-action path. */
    public boolean widgetPending(Element element) {
        return elementPending(element);
    }

    /** Compatibility alias for the generic element-action path. */
    public void receiveWidgetResult(ScreenNetwork.WidgetResult result) {
        receiveElementResult(result);
    }

    /** Compatibility alias for the generic element-action path. */
    public ScreenNetwork.WidgetResult takeWidgetResult(Element element) {
        return takeElementResult(element);
    }

    /** Compatibility alias for the generic element-action path. */
    public void tickWidgetActions() {
        tickElementActions();
    }

    /** Compatibility alias for the generic element-action path. */
    public ScreenNetwork.WidgetResult widgetAction(
        net.minecraft.server.level.ServerPlayer player, ScreenNetwork.Widget packet) {
        return elementAction(player, packet);
    }

    private static BlockEntity readEntity(Inventory inventory, RegistryFriendlyByteBuf data) {
        var entity = Objects.requireNonNull(
            inventory.player.level().getBlockEntity(data.readBlockPos()),
            "Screen block entity missing");
        String expected = data.readUtf(64);
        var screen = Screens.get(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()))
            .orElseThrow();
        if (!ScreenRuntime.fingerprint(screen.screen(), entity).equals(expected))
            throw new IllegalStateException(
                "Client/server screen or machine definitions differ: " + screen.screen().id());
        return entity;
    }

    public MachineMenu(int id, Inventory inventory, BlockEntity entity) {
        this(id, inventory, entity, inventory.player.level().isClientSide);
    }

    /**
     * For tests only.
     *
     * @param client
     *            whether the slots are the client's copy, as on a client; the menu still reads the
     *            level's side for layout, so this is only meaningful for running both against one
     *            server
     */
    protected MachineMenu(int id, Inventory inventory, BlockEntity entity, boolean client) {
        this(
            id, inventory, entity, client,
            java.util.concurrent.ThreadLocalRandom.current().nextLong());
    }

    private MachineMenu(
        int id, Inventory inventory, BlockEntity entity, boolean client, long session) {
        this(id, inventory, entity, client, session, entity.getBlockPos());
    }

    private MachineMenu(int id, Inventory inventory, BlockEntity entity, boolean client,
        long session,
        net.minecraft.core.BlockPos anchor) {
        super(ScreenRuntime.MENUS.get(), id);
        owner = inventory.player;
        blockEntity = entity;
        bound = Screens.get(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()))
            .orElseThrow();
        if (!bound.blockEntityClass().isInstance(entity))
            throw new IllegalArgumentException("Wrong screen block entity");
        elements = bound.screen().elements();
        for (int i = 0; i < elements.size(); i++)
            elementIndexes.put(elements.get(i), i);
        if (elements.size() > 4096)
            throw new IllegalArgumentException("A screen may have at most 4096 elements");
        host = entity instanceof ScreenHost h ? h : null;
        interactionAnchor = anchor.immutable();
        interactionIdentity = host == null ? "" : host.screenInteractionIdentity(anchor);
        elementStates = new ElementStates(elements, owner.registryAccess(),
            this::stateLayoutChanged);
        elementActions = new ElementActionSession(this, session, owner.registryAccess(), values,
            elementStates);
        if (host != null)
            ScreenInventoryValidation.validate(bound.screen(), host);
        layout = ScreenLayout.of(
            bound.screen(),
            owner.level().isClientSide ? ScreenRuntime.textMeasure : TextMeasure.NONE,
            host,
            elementStates);
        components = MenuComponentRegistry.GLOBAL.create(
            new MenuComponentRegistry.Context(this, inventory, host, client));
        components.installSlots(nativeSlots.registrar(this::addSlot), layout);
    }

    public ElementStates elementStates() {
        return elementStates;
    }

    /** Compatibility view of elements with named navigation; synchronization is fully typed. */
    public Map<String, String> views() {
        var selections = new HashMap<String, String>();
        for (var element : elements)
            if (element instanceof NavigableElement navigation)
                element
                    .common()
                    .id()
                    .ifPresent(id -> selections.put(id, navigation.selected(elementStates)));
        return Map.copyOf(selections);
    }

    private void stateLayoutChanged() {
        visibility.clear();
        if (layout != null)
            relayout();
    }

    private ScreenLayout layout;

    /** Current geometry, shared by the screen and its persistent menu slots. */
    public ScreenLayout layout() {
        return layout;
    }

    private void relayout() {
        layout = ScreenLayout.of(
            bound.screen(),
            owner.level().isClientSide ? ScreenRuntime.textMeasure : TextMeasure.NONE,
            host,
            elementStates);
        components.position(layout);
    }

    public List<SlotElement> playerSlotElements() {
        return nativeSlots.entries().stream()
            .map(MenuSlots.Entry::element)
            .filter(Objects::nonNull)
            .filter(SlotElement::playerInventory)
            .toList();
    }

    public int slotIndex(SlotElement element) {
        return nativeSlots.slot(element).index;
    }

    public Slot slot(SlotElement element) {
        return nativeSlots.slot(element);
    }

    public SlotElement slotElement(int index) {
        return nativeSlots.element(index);
    }

    public int menuSlot(int index) {
        return components.requireCapability(InventoryAccess.class).menuSlot(index);
    }

    public int menuSlot(ItemInventories.Address address) {
        return components.requireCapability(InventoryAccess.class).menuSlot(address);
    }

    public int index(Element element) {
        var index = elementIndexes.get(element);
        if (index == null)
            throw new IllegalArgumentException("Element is not on this screen");
        return index;
    }

    /** Optional identity lookup for externally supplied or stale element references. */
    int findIndex(Element element) {
        return elementIndexes.getOrDefault(element, -1);
    }

    public Object value(Element element, boolean maximum) {
        return values.get(index(element) * 2 + (maximum ? 1 : 0));
    }

    public Object wireValue(int key) {
        return values.get(key);
    }

    public void apply(Map<Integer, Object> delta) {
        elementStates.apply(delta);
        values.putAll(delta);
        components.apply(delta);
    }

    public boolean visible(Element element) {
        if (visibility.isEmpty()) {
            for (var root : bound.screen().body())
                cacheVisibility(root, true);
            for (var root : bound.screen().tabs())
                cacheVisibility(root, true);
            for (var root : bound.screen().implicitElements())
                cacheVisibility(
                    root,
                    root.visibilityAnchor(bound.screen())
                        .map(anchor -> Boolean.TRUE.equals(visibility.get(anchor)))
                        .orElse(true));
        }
        return Boolean.TRUE.equals(visibility.get(element));
    }

    private void cacheVisibility(Element root, boolean shown) {
        visibility.put(root, shown);
        for (var child : root.children()) {
            boolean next = shown;
            if (root instanceof SelectiveContainer container)
                next &= container.childVisible(child, elementStates);
            cacheVisibility(child, next);
        }
    }

    public void navigate(String context, String view) {
        if (bound.screen().find(context).orElse(null) instanceof NavigableElement navigation)
            navigation.select(elementStates, view);
    }

    @Override
    public boolean stillValid(Player player) {
        return !blockEntity.isRemoved()
            && player.level() == blockEntity.getLevel()
            && player.level().hasChunkAt(blockEntity.getBlockPos())
            && player.level().getBlockEntity(blockEntity.getBlockPos()) == blockEntity
            && player.level().hasChunkAt(interactionAnchor)
            && (host == null
                ? interactionAnchor.equals(blockEntity.getBlockPos())
                : host.screenInteractionAnchor(interactionAnchor)
                    && interactionIdentity
                        .equals(host.screenInteractionIdentity(interactionAnchor)))
            && player.canInteractWithBlock(interactionAnchor, 4.0);
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (slot >= 0 && slot < slots.size() && !slots.get(slot).isActive())
            return;
        boolean previous = interacting;
        if (!previous)
            components.beforeInteraction();
        interacting = true;
        try {
            super.clicked(slot, button, type, player);
        } finally {
            endInteraction(previous);
        }
    }

    private void endInteraction(boolean previous) {
        interacting = previous;
        if (!previous) {
            nativeSlots.endInteraction();
            components.afterInteraction();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        boolean previous = interacting;
        if (!previous)
            components.beforeInteraction();
        interacting = true;
        try {
            return nativeSlots.quickMove(player, index, components::destinations,
                this::moveItemStackTo);
        } finally {
            endInteraction(previous);
        }
    }

    public boolean isMachineItemSlot(int index) {
        return nativeSlots.overlay(index);
    }

    public SlotPlan reservationPlan(int index) {
        return nativeSlots.plan(index);
    }

    @Override
    public void removed(Player player) {
        // Vanilla also calls this when a recipe browser covers the client screen. The player's
        // active menu survives that transition; server removal always means actual closure.
        if (!player.level().isClientSide || player.containerMenu != this)
            elementActions.close();
        try {
            components.removed();
        } finally {
            super.removed(player);
        }
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slot.isActive() && super.canDragTo(slot);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.isActive() && slot.mayPickup(owner) && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public void broadcastChanges() {
        components.beforeBroadcast();
        super.broadcastChanges();
        components.broadcast();
        if (!(owner instanceof net.minecraft.server.level.ServerPlayer player)
            || !stillValid(owner)
            || !player.connection.hasChannel(ScreenNetwork.Values.TYPE))
            return;
        var snapshot = new HashMap<>(ScreenSnapshots.read(blockEntity, bound));
        snapshot.putAll(elementStates.sample(blockEntity, owner));

        components
            .snapshot()
            .forEach(
                (key, value) -> {
                    if (snapshot.containsKey(key))
                        throw new IllegalArgumentException(
                            "Component snapshot collides with menu value: " + key);
                    snapshot.put(key, value);
                });
        var delta = ScreenValues.changed(values, snapshot, initial);
        elementActions.appendCorrections(snapshot, delta);
        if (!delta.isEmpty()) {
            values.putAll(delta);
            PacketDistributor.sendToPlayer(player, new ScreenNetwork.Values(containerId, delta));
        }
        initial = false;
    }

    @Override
    public boolean clickMenuButton(Player player, int packed) {
        if ((packed & 0xc0000000) != 0)
            return false;
        action(player, ScreenNetwork.Action.unpack(containerId, packed));
        return true;
    }

    public void action(Player player, ScreenNetwork.Action action) {
        if (action.menu() != containerId
            || player != owner
            || player.containerMenu != this
            || !stillValid(player)
            || action.element() < 0
            || action.element() >= elements.size())
            return;
        var element = elements.get(action.element());
        if (!visible(element)
            || action.button() < 0
            || action.button() > 1
            || (action.modifiers() & ~7) != 0)
            return;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer))
            return;
        var handler = element.legacyAction(action.kind());
        if (handler.isEmpty() || element.action(handler.get().id()).orElse(null) != handler.get())
            return;
        ElementActions.Click click;
        try {
            click = new ElementActions.Click(action.button(), action.modifiers(), action.option());
        } catch (IllegalArgumentException malformed) {
            return;
        }
        if (handler.get().execute(new ElementAction.Context(serverPlayer, this, element), click)) {
            blockEntity.setChanged();
            ScreenSnapshots.invalidate(blockEntity);
            broadcastChanges();
        }
    }
}
