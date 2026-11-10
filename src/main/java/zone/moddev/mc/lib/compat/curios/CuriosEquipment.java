package zone.moddev.mc.lib.compat.curios;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.items.IItemHandler;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.ISlotType;

import zone.moddev.mc.lib.gui.runtime.ContainerDrawerMenu;
import zone.moddev.mc.lib.gui.runtime.EquippedContainers.SlotInfo;
import zone.moddev.mc.lib.gui.runtime.EquippedContainers;

/** Optional adapter. Every mutation goes through Curios' validating dynamic stack handlers. */
public final class CuriosEquipment {
    private CuriosEquipment() {
    }

    private record Entry(SlotInfo info, IItemHandler handler, boolean active, boolean render) {
    }

    private static net.minecraft.network.chat.Component text(String name) {
        return net.minecraft.network.chat.Component.translatable("screen.mmdlib.curios." + name);
    }

    public static EquippedContainers.Presentation presentation() {
        return new EquippedContainers.Presentation(
            text("title"),
            java.util.Optional.of(ResourceLocation.parse("curios:slot/empty_curio_slot")),
            0xa6a0ca,
            List.of(text("equipment"), text("functional.short"), text("cosmetic.short")),
            List.of(text("toggle")),
            List.of(text("store")),
            List.of(text("columns"), text("store")));
    }

    private static SlotInfo describe(
        String identifier, int index, boolean cosmetic, boolean available, ResourceLocation icon) {
        var name = net.minecraft.network.chat.Component.translatableWithFallback(
            "curios.identifier." + identifier, identifier);
        if (index > 0)
            name.append(" " + (index + 1));
        return new SlotInfo(
            identifier,
            index,
            cosmetic,
            available,
            icon,
            cosmetic ? net.minecraft.network.chat.Component.empty() : name,
            List.of(name, text(!available ? "unavailable" : cosmetic ? "cosmetic" : "functional")),
            net.minecraft.network.chat.Component.literal("-"));
    }

    public static List<EquippedContainers.Source> find(Player player, ItemStack identity) {
        return discover(player, identity).equipment();
    }

    public static EquippedContainers.Snapshot discover(Player player, ItemStack identity) {
        var containerSlots = new java.util.LinkedHashSet<>(
            zone.moddev.mc.lib.MMDLibConfig.CONTAINER_SLOTS.get());
        var containers = new java.util.HashMap<String, List<EquippedContainers.Source>>();
        var entries = new ArrayList<Entry>();
        var topology = new StringBuilder();
        CuriosApi.getCuriosInventory(player)
            .ifPresent(
                inventory -> {
                    var groups = new ArrayList<>(inventory.getCurios().entrySet());
                    groups.sort(
                        Comparator.<java.util.Map.Entry<String, top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler>>comparingInt(
                            entry -> CuriosApi.getSlot(entry.getKey(), player.level())
                                .map(ISlotType::getOrder)
                                .orElse(1000))
                            .thenComparing(java.util.Map.Entry::getKey));
                    for (var group : groups) {
                        var stacks = group.getValue();
                        if (containerSlots.contains(group.getKey())) {
                            var found = new ArrayList<EquippedContainers.Source>();
                            for (int i = 0; i < stacks.getStacks().getSlots()
                                && found.size() < ContainerDrawerMenu.MAX_CONTAINERS; i++) {
                                var stack = stacks.getStacks().getStackInSlot(i);
                                if (stack.isEmpty() || !inventory.isSlotActive(group.getKey(), i))
                                    continue;
                                var storage = stack.getCapability(
                                    net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.ITEM);
                                if (storage != null
                                    && storage.getSlots() > 0
                                    && storage.getSlots() <= ContainerDrawerMenu.MAX_SLOTS)
                                    found.add(
                                        new EquippedContainers.Source(group.getKey() + ":" + i,
                                            stack, storage));
                            }
                            containers.put(group.getKey(), found);
                        }
                        if (!stacks.isVisible())
                            continue;
                        var type = CuriosApi.getSlot(group.getKey(), player.level());
                        var icon = type.map(ISlotType::getIcon)
                            .orElse(ResourceLocation.parse("curios:slot/empty_curio_slot"));
                        for (int i = 0; i < stacks.getStacks().getSlots()
                            && entries.size() + 2 <= ContainerDrawerMenu.MAX_SLOTS; i++) {
                            boolean cosmetic = stacks.hasCosmetic()
                                && i < stacks.getCosmeticStacks().getSlots();
                            boolean active = inventory.isSlotActive(group.getKey(), i);
                            boolean render = i >= stacks.getRenders().size()
                                || stacks.getRenders().get(i);
                            entries.add(
                                new Entry(
                                    describe(group.getKey(), i, false, active, icon),
                                    stacks.getStacks(),
                                    active,
                                    render));
                            entries.add(
                                new Entry(
                                    describe(
                                        group.getKey(),
                                        i,
                                        true,
                                        cosmetic && active,
                                        ResourceLocation.parse("curios:slot/empty_cosmetic_slot")),
                                    cosmetic ? stacks.getCosmeticStacks() : null,
                                    active,
                                    true));
                            topology
                                .append(group.getKey())
                                .append(':')
                                .append(i)
                                .append(':')
                                .append(cosmetic)
                                .append(';');
                        }
                    }
                });
        var backpacks = containerSlots.stream()
            .flatMap(name -> containers.getOrDefault(name, List.of()).stream())
            .limit(ContainerDrawerMenu.MAX_CONTAINERS)
            .toList();
        if (entries.isEmpty())
            return new EquippedContainers.Snapshot(backpacks, List.of());
        var handler = new EquippedContainers.DescribedHandler() {
            private boolean valid(int slot) {
                if (slot < 0 || slot >= entries.size())
                    return false;
                var entry = entries.get(slot);
                return entry.info.available()
                    && entry.info.index() < entry.handler.getSlots()
                    && CuriosApi.getCuriosInventory(player)
                        .map(
                            inventory -> inventory.isSlotActive(entry.info.identifier(),
                                entry.info.index()))
                        .orElse(false);
            }

            public int getSlots() {
                return entries.size();
            }

            public SlotInfo describe(int slot) {
                return entries.get(slot).info;
            }

            public ItemStack getStackInSlot(int slot) {
                return valid(slot)
                    ? entries.get(slot).handler.getStackInSlot(describe(slot).index())
                    : ItemStack.EMPTY;
            }

            public int getSlotLimit(int slot) {
                return valid(slot)
                    ? entries.get(slot).handler.getSlotLimit(describe(slot).index())
                    : 0;
            }

            public boolean isItemValid(int slot, ItemStack stack) {
                return valid(slot)
                    && entries.get(slot).handler.isItemValid(describe(slot).index(), stack);
            }

            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return valid(slot)
                    ? entries.get(slot).handler.extractItem(describe(slot).index(), amount,
                        simulate)
                    : ItemStack.EMPTY;
            }

            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (!valid(slot))
                    return stack;
                var entry = entries.get(slot);
                var result = entry.handler.insertItem(entry.info.index(), stack, simulate);
                if (!simulate
                    && result.getCount() < stack.getCount()
                    && entry.active
                    && !entry.info.cosmetic()) {
                    var context = new SlotContext(
                        entry.info.identifier(), player, entry.info.index(), false, entry.render);
                    CuriosApi.getCurio(stack).ifPresent(curio -> curio.onEquipFromUse(context));
                }
                return result;
            }
        };
        return new EquippedContainers.Snapshot(
            backpacks,
            List.of(
                new EquippedContainers.Source(
                    topology.toString(),
                    identity,
                    handler,
                    presentation(),
                    "curios",
                    zone.moddev.mc.lib.gui.runtime.ContainerPolicy.EQUIPMENT)));
    }
}
