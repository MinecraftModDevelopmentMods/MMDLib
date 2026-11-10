package zone.moddev.mc.lib.gui.model.elements;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.gui.ElementAction;
import zone.moddev.mc.lib.gui.ElementActions;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.load.ResourceValidationContext;
import zone.moddev.mc.lib.gui.load.ScreenValidationContext;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.ElementGroup;
import zone.moddev.mc.lib.gui.model.InventoryScope;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.SlotKind;
import zone.moddev.mc.lib.gui.model.SlotSize;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.runtime.slot.MenuSlots;
import zone.moddev.mc.lib.gui.runtime.slot.SlotAdapter;
import zone.moddev.mc.lib.gui.runtime.slot.SlotTransferContext;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;

/** Shared slot geometry and presentation, including backgrounds, empty icons and ghost items. */
public interface SlotElement extends Element {
    /** Whether this slot addresses the viewing player's inventory instead of machine storage. */
    default boolean playerInventory() {
        return false;
    }

    /**
     * Native transfer role; player-backed implementations may override equipment/storage routing.
     */
    default zone.moddev.mc.lib.gui.runtime.slot.MenuSlots.Role role() {
        return playerInventory()
            ? zone.moddev.mc.lib.gui.runtime.slot.MenuSlots.Role.PLAYER_STORAGE
            : zone.moddev.mc.lib.gui.runtime.slot.MenuSlots.Role.MACHINE;
    }

    /** Reservation synchronization policy; hiding an overlay must not change inventory rules. */
    default boolean reservationSync(MenuSlots.Entry entry) {
        return entry.role() == MenuSlots.Role.MACHINE;
    }

    /** Reservation drawing policy, independent of the native transfer role when overridden. */
    default boolean reservationOverlay(MenuSlots.Entry entry) {
        return entry.role() == MenuSlots.Role.MACHINE && entry.slot() instanceof SlotAdapter;
    }

    /**
     * Ordered shift-click candidates. The menu enforces membership, visibility, acceptance and
     * identity uniqueness; existing stacks are merged before empty slots in the supplied order.
     * Return an empty list to refuse routing. Treat context/native slots as read-only.
     */
    default List<Slot> quickMoveTargets(SlotTransferContext context) {
        return defaultQuickMoveTargets(context);
    }

    /** Legacy policy, also used for native contributions without an element. */
    static List<Slot> defaultQuickMoveTargets(SlotTransferContext context) {
        if (context.source().role() == MenuSlots.Role.PLAYER_STORAGE)
            return context.machineDestinations();
        return context.entries().reversed().stream()
            .filter(entry -> entry.role() == MenuSlots.Role.PLAYER_STORAGE)
            .map(MenuSlots.Entry::slot)
            .toList();
    }

    /** Stable native ordering among player-backed slots; ties retain element traversal order. */
    default int playerSlotOrder() {
        return index();
    }

    /** Player-backed native adapter; defaults to vanilla player inventory/equipment behavior. */
    default net.minecraft.world.inventory.Slot createPlayerSlot(
        net.minecraft.world.entity.player.Inventory inventory,
        Rect bounds,
        java.util.function.BooleanSupplier visible) {
        var slot = new PlayerSlotElement(common(), index(), appearance())
            .createPlayerSlot(inventory, bounds, visible);
        var position = itemPosition(bounds);
        slot.x = position[0];
        slot.y = position[1];
        return slot;
    }

    /** Unanchored item groups fall back to this slot's inventory-scoped member address. */
    @Override
    default List<String> associatedGroups(Screen screen, List<ElementGroup> groups) {
        var explicit = new HashSet<>(Element.super.associatedGroups(screen, groups));
        var owner = InventoryScope.owner(screen, this);
        return groups.stream()
            .filter(
                group -> explicit.contains(group.id())
                    || (!playerInventory()
                        && group.anchors().isEmpty()
                        && group
                            .members()
                            .filter(
                                members -> members.type().equals(ResourceType.ITEMS.id())
                                    && owner
                                        .map(
                                            inventory -> inventory
                                                .inventory()
                                                .equals(members.inventory()))
                                        .orElse(true)
                                    && (owner.isPresent()
                                        ? members.indices()
                                        : members.legacyIndices())
                                        .contains(index()))
                            .isPresent()))
            .map(ElementGroup::id)
            .toList();
    }

    record Appearance(
        SlotSize size,
        Optional<Integer> tint,
        Optional<ResourceLocation> icon,
        ResourceLocation background,
        float ghostAlpha,
        Optional<ResourceLocation> iconAtlas,
        int itemSize) {
        public Appearance(
            SlotSize size,
            Optional<Integer> tint,
            Optional<ResourceLocation> icon,
            ResourceLocation background,
            float ghostAlpha) {
            this(size, tint, icon, background, ghostAlpha, Optional.empty(), 16);
        }

        public Appearance(
            SlotSize size,
            Optional<Integer> tint,
            Optional<ResourceLocation> icon,
            ResourceLocation background,
            float ghostAlpha,
            Optional<ResourceLocation> iconAtlas) {
            this(size, tint, icon, background, ghostAlpha, iconAtlas, 16);
        }

        public Appearance {
            Objects.requireNonNull(size);
            Objects.requireNonNull(tint);
            Objects.requireNonNull(icon);
            Objects.requireNonNull(background);
            Objects.requireNonNull(iconAtlas);
            if (itemSize < 4 || itemSize > 32)
                throw new IllegalArgumentException("Item size must be 4..32");
            if (!Float.isFinite(ghostAlpha) || ghostAlpha < 0 || ghostAlpha > 1)
                throw new IllegalArgumentException("Ghost alpha must be between 0 and 1");
        }

        public static Appearance standard(
            SlotSize size, Optional<Integer> tint, Optional<ResourceLocation> icon) {
            return new Appearance(size, tint, icon, ResourceLocation.parse("mmdlib:slot"), .42f);
        }
    }

    int index();

    SlotKind kind();

    Appearance appearance();

    default SlotSize size() {
        return appearance().size();
    }

    default Optional<Integer> tint() {
        return appearance().tint();
    }

    default Optional<ResourceLocation> icon() {
        return appearance().icon();
    }

    default int extent() {
        return Math.max(appearance().itemSize() + 2, size() == SlotSize.LARGE ? 26 : 0);
    }

    default int[] itemPosition(Rect bounds) {
        int size = appearance().itemSize();
        return new int[]{
            bounds.x() + (bounds.width() - size) / 2, bounds.y() + (bounds.height() - size) / 2
        };
    }

    /** Server-sampled hint. Hosts may be absent; custom slots can still supply a static hint. */
    default ItemStack hint(ScreenHost host, String inventory) {
        return host == null || playerInventory()
            ? ItemStack.EMPTY
            : host.screenSlotHint(inventory, index());
    }

    /**
     * Whether the native bridge draws vanilla contents; ghosts use the element painter by default.
     */
    default boolean renderNative() {
        return !ghost();
    }

    /** Destination of vanilla's 16x16 slot drawing, in panel coordinates. */
    default Rect nativeBounds(Rect vanillaBounds) {
        return new Rect(
            vanillaBounds.x(), vanillaBounds.y(), appearance().itemSize(), appearance().itemSize());
    }

    /** Native hover highlight geometry, independent of content and hit geometry when overridden. */
    default Rect highlightBounds(Rect vanillaBounds) {
        return nativeBounds(vanillaBounds);
    }

    /**
     * Native interaction geometry. The bridge retains vanilla's hover tolerance and active checks.
     */
    default Rect hitBounds(Rect vanillaBounds) {
        return nativeBounds(vanillaBounds);
    }

    /** Optional label for a virtual inventory row. */
    default Component cellLabel() {
        return Component.empty();
    }

    /**
     * Virtual-cell hit geometry, shared by highlighting, tooltips and gestures. Includes tolerance.
     */
    default Rect cellHitBounds(Rect frame) {
        var xy = itemPosition(frame);
        var hit = hitBounds(new Rect(xy[0], xy[1], 16, 16));
        return new Rect(hit.x() - 1, hit.y() - 1, hit.width() + 2, hit.height() + 2);
    }

    default boolean ghost() {
        return kind() == SlotKind.GHOST;
    }

    /** Mods may override the adapter while retaining shared appearance and inventory placement. */
    default net.minecraft.world.inventory.Slot createSlot(
        net.neoforged.neoforge.items.IItemHandlerModifiable storage,
        int local,
        Rect bounds,
        java.util.function.BooleanSupplier visible,
        java.util.function.BooleanSupplier interacting,
        Runnable changed) {
        var slot = ghost()
            ? new GhostSlotElement(common(), index(), appearance()).createSlot(bounds, visible)
            : new ItemSlotElement(common(), index(), kind(), appearance())
                .createSlot(storage, local, bounds, visible, interacting, changed);
        var position = itemPosition(bounds);
        slot.x = position[0];
        slot.y = position[1];
        return slot;
    }

    /**
     * Typed selection action used by clicks and ingredient drops. Empty leaves ordinary slot input.
     */
    default Optional<ElementAction<ItemStack>> selectionAction() {
        return ghost() ? Optional.of(GhostSlotElement.SELECT_GHOST) : Optional.empty();
    }

    /** Client selection eligibility. The chosen action must also enforce its own server rules. */
    default boolean acceptsSelection(int button, ItemStack carried) {
        return true;
    }

    /** Converts a detached viewer ingredient to an item selection; mods may support other types. */
    default Optional<ItemStack> selectionIngredient(ResourceLocation type, Object ingredient) {
        return type.equals(ResourceLocation.parse("mmdlib:item"))
            && ingredient instanceof ItemStack stack
                ? Optional.of(stack.copy())
                : Optional.empty();
    }

    /**
     * Normalizes a detached selection on the client and again during default server acceptance.
     * Overrides should be idempotent; server acceptance uses button zero with the submitted value.
     */
    default ItemStack selection(int button, ItemStack carried) {
        return button == 1 || carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1);
    }

    default ItemStack read(zone.moddev.mc.lib.gui.runtime.ScreenHost host, String inventory) {
        return host.screenGhost(inventory, index()).copy();
    }

    default boolean accept(
        zone.moddev.mc.lib.gui.runtime.ScreenHost host,
        String inventory,
        net.minecraft.world.entity.player.Player player,
        ItemStack stack) {
        return host.setScreenGhost(player, inventory, index(), selection(0, stack.copy()).copy());
    }

    @Override
    default void validateResources(ResourceValidationContext context) {
        Element.super.validateResources(context);
        if (ghost() || playerInventory())
            return;
        var owner = InventoryScope.owner(context.screen(), this);
        int count = owner
            .map(scope -> context.inventories().getOrDefault(scope.inventory(), 0))
            .orElseGet(() -> context.resources().getOrDefault("items", 0));
        if (index() < 0 || index() >= count)
            throw new IllegalArgumentException("Slot index outside machine storage: " + index());
    }

    default void validateInventory(Screen screen, zone.moddev.mc.lib.gui.runtime.ScreenHost host) {
        if (ghost() || playerInventory())
            return;
        String name = InventoryScope.name(screen, this);
        host.screenInventoryBinding(name, index(), InventoryScope.owner(screen, this).isEmpty());
    }

    @Override
    default void validateHost(
        ScreenValidationContext context, zone.moddev.mc.lib.gui.runtime.ScreenHost host) {
        Element.super.validateHost(context, host);
        validateInventory(context.screen(), host);
    }

    @Override
    default void validateScreen(ScreenValidationContext context) {
        Element.super.validateScreen(context);
        if (playerInventory()) {
            context.unique(
                ResourceLocation.parse("mmdlib:player_slot"),
                index(),
                "Player slot " + index() + " is placed twice");
            return;
        }
        context.unique(
            ResourceLocation.parse("mmdlib:inventory_slot"),
            List.of(
                InventoryScope.owner(context.screen(), this).map(InventoryScope::inventory)
                    .orElse(""),
                index()),
            "Slot " + index() + " is placed twice on this screen");
    }

    @Override
    default void validate() {
        if (index() < 0 || index() > 4095)
            throw new IllegalArgumentException("Slot index outside 0..4095");
        if (common().interaction().isPresent())
            throw new IllegalArgumentException("Slots use inventory interactions");
    }

    /** Rendering operations implemented only by the client; presentation decisions live here. */
    interface Painter {
        void background(ResourceLocation sprite, int tint);

        void icon(ResourceLocation sprite, Optional<ResourceLocation> atlas);

        void ghost(ItemStack stack, float alpha);
    }

    default void render(Painter painter, ItemStack contents, ItemStack hint) {
        painter.background(
            appearance().background(),
            tint().orElse(kind() == SlotKind.UPGRADE ? 0xf0cd82 : 0xffffff));
        if (contents.isEmpty())
            icon().ifPresent(sprite -> painter.icon(sprite, appearance().iconAtlas()));
        var ghost = ghost() ? contents : contents.isEmpty() ? hint : ItemStack.EMPTY;
        if (!ghost.isEmpty())
            painter.ghost(ghost, appearance().ghostAlpha());
    }

    /**
     * The vanilla portion of an occupied slot tooltip. Inputs are detached copies. Return
     * replacement rows or an empty list to suppress vanilla rows; element and ancestor rows are
     * appended afterward.
     */
    default List<Component> nativeTooltip(ItemStack stack, List<Component> vanilla) {
        return vanilla;
    }

    default List<Component> reservationTooltip(SlotPlan plan) {
        var lines = new ArrayList<Component>();
        if (plan.take() > 0)
            lines.add(Component.translatable("screen.mmdlib.reservation.input", plan.take()));
        var output = plan.add();
        if (!output.isEmpty())
            lines.add(
                Component.translatable(
                    "screen.mmdlib.reservation.output", output.getCount(), output.getHoverName()));
        return lines;
    }

    interface OverlayPainter {
        void ghost(ItemStack stack, float alpha);

        void quantity(String text, boolean bottom);
    }

    /** Render the plan over vanilla's actual display, including its temporary drag preview. */
    default void renderOverlay(
        OverlayPainter painter, ItemStack displayed, SlotPlan plan, String countString) {
        if (!plan.isNone()) {
            var output = plan.add();
            if (displayed.isEmpty() && !output.isEmpty())
                painter.ghost(output, appearance().ghostAlpha());
            String input = plan.take() > 0 ? "-" + plan.take() : "";
            String added = output.isEmpty() ? "" : "+" + output.getCount();
            painter.quantity(input + (!input.isEmpty() && !added.isEmpty() ? " " : "") + added,
                false);
        }
        if (!displayed.isEmpty() && (displayed.getCount() != 1 || countString != null))
            painter.quantity(
                countString == null ? Integer.toString(displayed.getCount()) : countString, true);
    }

    default int[] measure(TextMeasure text) {
        return new int[]{extent(), extent()};
    }

    default List<? extends ElementAction<?>> actions() {
        var selection = selectionAction();
        return selection.isPresent()
            ? List.of(ElementActions.CLICK, selection.get())
            : Element.super.actions();
    }
}
