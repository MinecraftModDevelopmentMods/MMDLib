package zone.moddev.mc.lib.gui.runtime;

import java.util.Map;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.items.IItemHandlerModifiable;

import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.api.capabilities.SideConfig;

/**
 * Implement on a block entity to expose its inventory and side groups to a
 * data-defined menu.
 */
public interface ScreenHost {
    default boolean screenStatsDrawer() {
        return true;
    }

    /**
     * Validated physical interaction anchor; single-block hosts keep their
     * existing position.
     */
    default boolean screenInteractionAnchor(
        net.minecraft.core.BlockPos anchor) {
        return this instanceof net.minecraft.world.level.block.entity.BlockEntity entity
            && entity.getBlockPos().equals(anchor);
    }

    /**
     * A changing formation identity invalidates previously opened
     * member-anchored sessions.
     */
    default String screenInteractionIdentity(
        net.minecraft.core.BlockPos anchor) {
        return "";
    }

    /**
     * The inventory behind the screen's {@code <slot index>} elements, numbered
     * as in the XML; returning an {@code ItemStorage} keeps its output
     * restrictions and filters for players. It must work on the client too,
     * where the menu asks it what each slot accepts to predict clicks. A custom
     * handler or wrapper with reservations must also implement
     * {@code ReservedSlots}, including its plan snapshots, so both menu sides
     * enforce the same reservations.
     */
    IItemHandlerModifiable screenItems();

    /**
     * Independent inventories. The legacy combined view remains available
     * through screenItems.
     */
    default Map<String, ? extends IItemHandlerModifiable> screenInventories() {
        return screenItems() instanceof zone.moddev.mc.lib.api.capabilities.ItemInventories inventories
            ? inventories.inventories()
            : Map.of("items", screenItems());
    }

    default IItemHandlerModifiable screenInventory(String name) {
        var inventory = screenInventories().get(name);
        if (inventory == null && name.equals("items"))
            return screenItems();
        if (inventory == null)
            throw new IllegalArgumentException(
                "Unknown screen inventory: " + name);
        return inventory;
    }

    /** Reserved legacy menu numbering; named-only hosts may return zero. */
    default int screenLegacySlotCount() {
        return screenItems().getSlots();
    }

    /**
     * Logical aliases for a declared slot, also used by ghost slots that have
     * no physical binding.
     */
    default java.util.List<zone.moddev.mc.lib.api.capabilities.ItemInventories.Address> screenSlotAddresses(
        String inventory, int slot, boolean standalone) {
        return standalone
            && screenItems() instanceof zone.moddev.mc.lib.api.capabilities.ItemInventories
                ? java.util.List.of()
                : java.util.List.of(
                    new zone.moddev.mc.lib.api.capabilities.ItemInventories.Address(
                        inventory, slot));
    }

    /**
     * Resolve a real slot once for menu assembly. Override for custom wrappers
     * or logical mappings. Storage identity and local index identify duplicates
     * and shared client mirrors. Return stable handlers for the menu lifetime;
     * use -1 when no legacy index exists. Both menu sides must resolve the same
     * addresses/order. Standalone slots address the legacy view by default.
     */
    default InventoryBinding screenInventoryBinding(String inventory, int slot,
        boolean standalone) {
        var storage = standalone ? screenItems() : screenInventory(inventory);
        if (slot < 0 || slot >= storage.getSlots())
            throw new IllegalArgumentException(
                "slot index " + slot + " in inventory " + inventory
                    + " outside storage of size " + storage.getSlots());
        int legacy = standalone
            ? slot
            : screenItems() instanceof zone.moddev.mc.lib.api.capabilities.ItemInventories combined
                ? combined.index(inventory, slot)
                : -1;
        var addresses = new java.util.LinkedHashSet<>(
            screenSlotAddresses(inventory, slot, standalone));
        if (legacy >= 0)
            addresses.add(screenItemAddress(legacy));
        int local = slot;
        if (storage instanceof zone.moddev.mc.lib.api.capabilities.ItemStore store) {
            local = store.localSlot(slot);
            storage = store.storage(slot);
        }
        return new InventoryBinding(storage, local, legacy,
            java.util.List.copyOf(addresses));
    }

    /** Optional remembered item shown as a ghost when a real slot is empty. */
    default ItemStack screenSlotHint(String inventory, int slot) {
        return ItemStack.EMPTY;
    }

    default boolean setScreenGhost(Player player, String inventory, int slot,
        ItemStack filter) {
        return inventory.equals("items")
            && setScreenGhost(player, slot, filter);
    }

    default ItemStack screenGhost(String inventory, int slot) {
        return inventory.equals("items") ? screenGhost(slot) : ItemStack.EMPTY;
    }

    /**
     * The storage behind resource-bound elements; no interaction or tooltip
     * policy lives here.
     */
    default zone.moddev.mc.lib.api.capabilities.FluidStorage screenFluids() {
        return null;
    }

    /** Display metadata independent of whether a tank exposes automation. */
    default Optional<Component> screenTankName(int tank) {
        var automation = screenAutomation();
        return automation == null
            ? Optional.empty()
            : automation.groups().stream()
                .filter(group -> group.type() == ResourceType.FLUIDS
                    && group.members().contains(tank))
                .map(group -> group.name()).findFirst();
    }

    /** The optional energy storage exposed to resource-bound elements. */
    default zone.moddev.mc.lib.api.capabilities.EnergyBuffer screenEnergy() {
        return null;
    }

    /**
     * Resource sizes for validating element bindings before constructing the
     * menu.
     */
    default Map<String, Integer> screenResourceCounts() {
        return Map.of("items", screenItems().getSlots(), "fluids",
            screenFluids() == null ? 0 : screenFluids().getTanks(), "energy",
            screenEnergy() == null ? 0 : 1);
    }

    /**
     * Extra startup-definition identity checked alongside the screen before
     * constructing a menu.
     */
    default String screenDefinitionFingerprint() {
        return "";
    }

    /** Registered machine features used by generic processor controls. */
    default zone.moddev.mc.lib.machine.feature.FeatureHost screenFeatures() {
        return null;
    }

    /**
     * Parts contributing statistics, in display order. Providers are only
     * sampled on the server.
     */
    default java.util.List<? extends zone.moddev.mc.lib.api.stats.StatsProvider> screenStats() {
        return java.util.List.of();
    }

    /**
     * Ordered statistics timescales; hosts may return their own IDs, durations
     * and labels.
     */
    default java.util.List<zone.moddev.mc.lib.api.stats.StatsTimescale> screenStatsTimescales() {
        return zone.moddev.mc.lib.api.stats.StatsTimescale.DEFAULTS;
    }

    /** Opt in to the independent redstone face drawer. */
    default zone.moddev.mc.lib.api.redstone.RedstoneControl screenRedstone() {
        return null;
    }

    /**
     * Opt in to coloured resource groups and the interactive inventory drawer.
     */
    default zone.moddev.mc.lib.api.automation.AutomationInventory screenAutomation() {
        return null;
    }

    /**
     * Per-menu automation presentation. Return matching stable definitions on
     * both sides.
     */
    default zone.moddev.mc.lib.gui.model.containers.AutomationInventoryElement screenAutomationInventory(
        zone.moddev.mc.lib.api.automation.ResourceGroup<?> group) {
        return new zone.moddev.mc.lib.gui.model.containers.AutomationInventoryElement(
            group.id());
    }

    /**
     * False exposes only resource selection and side modes, with no
     * addon/filter slots.
     */
    default boolean screenAutomationAddons() {
        return true;
    }

    /**
     * Ordered destinations for a player inventory stack; storage still decides
     * what it accepts.
     */
    default int[] screenDestinations(ItemStack stack) {
        return java.util.stream.IntStream.range(0, screenItems().getSlots())
            .toArray();
    }

    /** Recipe input slots in recipe order; outputs must not be included. */
    default int[] screenRecipeSlots(
        net.minecraft.resources.ResourceLocation category) {
        return new int[0];
    }

    /**
     * Ordered named destinations. Override this for a custom route between
     * independent inventories.
     */
    default java.util.List<zone.moddev.mc.lib.api.capabilities.ItemInventories.Address> screenItemDestinations(
        ItemStack stack) {
        if (screenItems().getSlots() > 0)
            return java.util.Arrays.stream(screenDestinations(stack))
                .mapToObj(this::screenItemAddress).toList();
        var result = new java.util.ArrayList<zone.moddev.mc.lib.api.capabilities.ItemInventories.Address>();
        screenInventories().forEach((name, storage) -> {
            for (int i = 0; i < storage.getSlots(); i++)
                result.add(
                    new zone.moddev.mc.lib.api.capabilities.ItemInventories.Address(
                        name, i));
        });
        return java.util.List.copyOf(result);
    }

    default zone.moddev.mc.lib.api.capabilities.ItemInventories.Address screenItemAddress(
        int legacy) {
        return screenItems() instanceof zone.moddev.mc.lib.api.capabilities.ItemInventories combined
            && legacy >= 0 && legacy < combined.getSlots()
                ? combined.address(legacy)
                : new zone.moddev.mc.lib.api.capabilities.ItemInventories.Address(
                    "items", legacy);
    }

    /**
     * Named recipe input slots, independent of menu order and other
     * inventories' sizes.
     */
    default java.util.List<zone.moddev.mc.lib.api.capabilities.ItemInventories.Address> screenRecipeItems(
        net.minecraft.resources.ResourceLocation category) {
        return java.util.Arrays.stream(screenRecipeSlots(category))
            .mapToObj(this::screenItemAddress).toList();
    }

    /**
     * The side configuration of each group, keyed by the element ids in
     * {@code <side-config groups>}; a group missing from the map shows no modes
     * and ignores clicks.
     */
    default Map<String, SideConfig> screenSides() {
        return Map.of();
    }

    /**
     * Ghost filters are separate from real inventory; return false to reject a
     * change.
     */
    default boolean setScreenGhost(Player player, int slot, ItemStack filter) {
        return false;
    }

    /**
     * The authoritative filter shown in ghost slot {@code slot}; empty for
     * none.
     */
    default ItemStack screenGhost(int slot) {
        return ItemStack.EMPTY;
    }
}
