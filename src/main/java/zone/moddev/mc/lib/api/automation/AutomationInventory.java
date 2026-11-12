package zone.moddev.mc.lib.api.automation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.items.IItemHandler;

import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.ItemInventories;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.api.capabilities.SideConfig;

/**
 * Persistent group side modes and four addon/filter pairs per group. Selection
 * belongs to menus.
 */
public final class AutomationInventory {
    public static final int COLUMNS = 4;
    public static final int SLOTS_PER_GROUP = 8;
    public static final int MAX_GROUPS = 256;
    private final BlockEntity owner;
    private final List<ResourceGroup<?>> groups;
    private final Map<String, Integer> ids = new LinkedHashMap<>();
    private final Map<ResourceType<?>, Int2ObjectOpenHashMap<ResourceGroup<?>>> members = new IdentityHashMap<>();
    private final BitSet dirtyGroups = new BitSet();
    private long viewRevision, refund;
    private ServerLevel cacheLevel;
    private final Map<BlockCapability<?, Direction>, EnumMap<Direction, BlockCapabilityCache<?, Direction>>> neighbours = new IdentityHashMap<>();
    private final long[] generation;
    private final long[] revision;
    private final List<ItemStack> recovery = new ArrayList<>();
    private CompoundTag unknown = new CompoundTag();
    private CompoundTag transfers = new CompoundTag();
    private boolean loading;
    private final ItemInventories slots;

    public AutomationInventory(BlockEntity owner,
        List<ResourceGroup<?>> groups) {
        if (groups.isEmpty() || groups.size() > MAX_GROUPS)
            throw new IllegalArgumentException("Group count");
        this.owner = Objects.requireNonNull(owner);
        this.groups = List.copyOf(groups);
        generation = new long[groups.size() * COLUMNS];
        revision = new long[generation.length];
        var membership = new HashSet<String>();
        var ambiguous = new HashSet<String>();
        for (int i = 0; i < groups.size(); i++) {
            var group = groups.get(i);
            if (ids.putIfAbsent(group.id(), i) != null)
                throw new IllegalArgumentException("Duplicate group ID");
            for (int member : group.members())
                if (!membership.add(
                    group.type().id() + "/" + group.inventory() + "/" + member))
                    throw new IllegalArgumentException(
                        "Overlapping resource membership: " + group.id());
            var indexed = members.computeIfAbsent(group.type(),
                ignored -> new Int2ObjectOpenHashMap<>());
            for (int member : group.members()) {
                String key = group.type().id() + "/" + member;
                if (indexed.containsKey(member)) {
                    indexed.remove(member);
                    ambiguous.add(key);
                } else if (!ambiguous.contains(key))
                    indexed.put(member, group);
            }
            group.sides().onChange(() -> {
                viewChanged();
                if (owner.getLevel() != null)
                    owner.getLevel()
                        .invalidateCapabilities(owner.getBlockPos());
            });
        }
        var inventories = new LinkedHashMap<String, ItemStorage>();
        for (int g = 0; g < groups.size(); g++) {
            final int groupIndex = g;
            inventories.put(groups.get(g).id(),
                new ItemStorage(SLOTS_PER_GROUP) {
                    @Override
                    public int getSlotLimit(int slot) {
                        return 1;
                    }

                    @Override
                    public boolean isItemValid(int slot, ItemStack stack) {
                        return accepts(groupIndex * SLOTS_PER_GROUP + slot,
                            stack);
                    }

                    @Override
                    public void setStackInSlot(int slot, ItemStack stack) {
                        if (stack.getCount() > 1)
                            throw new IllegalArgumentException(
                                "One addon/filter per slot");
                        if (!loading && !stack.isEmpty()
                            && !accepts(groupIndex * SLOTS_PER_GROUP + slot,
                                stack))
                            throw new IllegalArgumentException(
                                "Incompatible or duplicate addon/filter role");
                        // Even replacing an identical stack is a new attachment
                        // generation.
                        if (!loading && slot % SLOTS_PER_GROUP < COLUMNS)
                            detach(groupIndex, slot % COLUMNS);
                        super.setStackInSlot(slot, stack);
                    }

                    @Override
                    protected void onContentsChanged(int slot) {
                        if (loading)
                            return;
                        int group = groupIndex, column = slot % COLUMNS;
                        dirtyGroups.set(group);
                        int key = group * COLUMNS + column;
                        if (slot % SLOTS_PER_GROUP < COLUMNS) {
                            generation[key]++;
                            // extractItem mutates without calling
                            // setStackInSlot.
                            var addon = addon(group, column);
                            var filter = AutomationInventory.this.filter(group,
                                column);
                            if (!filter.isEmpty() && addon.isEmpty())
                                detach(group, column);
                        } else {
                            generation[key]++;
                            revision[key]++;
                        }
                        viewChanged();
                    }
                });
        }
        slots = new ItemInventories(inventories);
    }

    public BlockEntity owner() {
        return owner;
    }

    public List<ResourceGroup<?>> groups() {
        return groups;
    }

    public ResourceGroup<?> group(int index) {
        return groups.get(index);
    }

    public int index(String id) {
        return ids.getOrDefault(id, -1);
    }

    public ItemInventories slots() {
        return slots;
    }

    private void viewChanged() {
        viewRevision++;
        owner.setChanged();
    }

    public long viewRevision() {
        return viewRevision;
    }

    /**
     * Call after external capability/definition changes; normal slot edits
     * invalidate themselves.
     */
    public void invalidateAddons() {
        dirtyGroups.set(0, groups.size());
        Arrays.setAll(generation, i -> generation[i] + 1);
        viewChanged();
    }

    /**
     * Persist any refund that cannot currently fit; retry before the next
     * transfer.
     */
    public void refund(EnergyBuffer power, long amount) {
        if (amount < 0)
            throw new IllegalArgumentException("Negative refund");
        if (amount == 0)
            return;
        refund = Math.addExact(refund,
            amount - (power == null ? 0 : power.refund(amount)));
        owner.setChanged();
    }

    public long pendingRefund() {
        return refund;
    }

    public void settleRefund(EnergyBuffer power) {
        if (refund == 0 || power == null)
            return;
        long restored = power.refund(refund);
        refund -= restored;
        if (restored != 0)
            owner.setChanged();
    }

    /**
     * Per-owner caches follow neighbour removal, capability invalidation and
     * chunk reloads.
     */
    @SuppressWarnings("unchecked")
    public <T> T neighbour(BlockCapability<T, Direction> capability,
        Direction side) {
        if (!(owner.getLevel() instanceof ServerLevel level)
            || owner.isRemoved())
            return null;
        if (cacheLevel != level) {
            neighbours.clear();
            cacheLevel = level;
        }
        var bySide = neighbours.computeIfAbsent(capability,
            ignored -> new EnumMap<>(Direction.class));
        var cache = (BlockCapabilityCache<T, Direction>) bySide.computeIfAbsent(
            side,
            direction -> BlockCapabilityCache.create(capability, level,
                owner.getBlockPos().relative(direction),
                direction.getOpposite(),
                () -> !owner.isRemoved() && owner.getLevel() == level, () -> {
                }));
        return cache.getCapability();
    }

    public CompoundTag existingTransferState(String group, int column) {
        String key = group + "/" + column;
        return transfers.contains(key, Tag.TAG_COMPOUND)
            ? transfers.getCompound(key)
            : null;
    }

    public CompoundTag transferState(String group, int column) {
        String key = group + "/" + column;
        if (!transfers.contains(key, Tag.TAG_COMPOUND)) {
            var state = new CompoundTag();
            int index = index(group);
            if (index >= 0)
                state.putString("resource",
                    group(index).type().id().toString());
            transfers.put(key, state);
        }
        return transfers.getCompound(key);
    }

    public static int addonSlot(int group, int column) {
        return group * SLOTS_PER_GROUP + column;
    }

    public static int filterSlot(int group, int column) {
        return addonSlot(group, column) + COLUMNS;
    }

    public ItemStack addon(int group, int column) {
        return slots.getStackInSlot(addonSlot(group, column));
    }

    public ItemStack filter(int group, int column) {
        return slots.getStackInSlot(filterSlot(group, column));
    }

    public long generation(int group, int column) {
        return generation[group * COLUMNS + column];
    }

    public long revision(int group, int column) {
        return revision[group * COLUMNS + column];
    }

    public boolean filterSupported(int group, int column) {
        var stack = addon(group, column);
        var capability = stack.getCapability(AddonCapabilities.ADDON);
        return capability != null
            && capability.specification(stack).accepts(group(group))
            && capability.specification(stack).filters();
    }

    public boolean accepts(int slot, ItemStack stack) {
        if (slot < 0 || slot >= groups.size() * SLOTS_PER_GROUP
            || stack.isEmpty())
            return false;
        int group = slot / SLOTS_PER_GROUP, sub = slot % SLOTS_PER_GROUP;
        if (sub < COLUMNS)
            return acceptsAddon(group(group), slots, slot, stack);
        return filterSupported(group, sub - COLUMNS)
            && acceptsFilter(addon(group, sub - COLUMNS), stack);
    }

    /**
     * Shared by authoritative storage and the menu's separate client-side slot
     * snapshot.
     */
    public static boolean acceptsAddon(ResourceGroup<?> group,
        IItemHandler contents, int slot, ItemStack stack) {
        if (slot < 0 || slot >= contents.getSlots()
            || slot % SLOTS_PER_GROUP >= COLUMNS || stack.isEmpty())
            return false;
        var capability = stack.getCapability(AddonCapabilities.ADDON);
        if (capability == null)
            return false;
        var spec = capability.specification(stack);
        if (!spec.accepts(group))
            return false;
        int base = slot - slot % SLOTS_PER_GROUP;
        for (int c = 0; c < COLUMNS; c++) {
            if (base + c == slot)
                continue;
            var other = contents.getStackInSlot(base + c);
            var provider = other.getCapability(AddonCapabilities.ADDON);
            if (provider != null
                && spec.conflictsWith(provider.specification(other)))
                return false;
        }
        return true;
    }

    private boolean acceptsFilter(ItemStack addon, ItemStack filter) {
        var capability = addon.getCapability(AddonCapabilities.ADDON);
        return capability != null && capability.acceptsFilter(addon, filter);
    }

    private void detach(int group, int column) {
        var filter = AutomationInventory.this.filter(group, column);
        if (!filter.isEmpty()) {
            recovery.add(filter.copy());
            slots.setStackInSlot(filterSlot(group, column), ItemStack.EMPTY);
        }
        generation[group * COLUMNS + column]++;
    }

    /**
     * At the end of the authoritative menu interaction, after the source slot
     * has been freed.
     */
    public void recover(Player player) {
        if (owner.getLevel() == null || owner.getLevel().isClientSide)
            return;
        for (var stack : recovery) {
            if (player != null)
                stack = net.neoforged.neoforge.items.ItemHandlerHelper
                    .insertItemStacked(
                        new net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper(
                            player.getInventory()),
                        stack, false);
            if (!stack.isEmpty())
                Containers.dropItemStack(owner.getLevel(),
                    owner.getBlockPos().getX() + .5,
                    owner.getBlockPos().getY() + .5,
                    owner.getBlockPos().getZ() + .5, stack);
        }
        if (!recovery.isEmpty())
            owner.setChanged();
        recovery.clear();
    }

    /**
     * Preserve incompatible items; recover duplicate roles and orphaned filters
     * without loss.
     */
    public void revalidate() {
        for (int g = dirtyGroups.nextSetBit(0); g >= 0; g = dirtyGroups
            .nextSetBit(g + 1)) {
            var claimed = new ArrayList<AddonSpec>();
            for (int c = 0; c < COLUMNS; c++) {
                var stack = addon(g, c);
                var provider = stack.getCapability(AddonCapabilities.ADDON);
                if (provider != null) {
                    var spec = provider.specification(stack);
                    if (claimed.stream().anyMatch(spec::conflictsWith)) {
                        // Keep the first role after reload; recover duplicates
                        // and their attached
                        // filters.
                        recovery.add(stack.copy());
                        slots.setStackInSlot(addonSlot(g, c), ItemStack.EMPTY);
                    } else
                        claimed.add(spec);
                }
                if (!filter(g, c).isEmpty() && addon(g, c).isEmpty())
                    detach(g, c);
            }
            dirtyGroups.clear(g);
        }
    }

    public boolean edit(int group, int column, long expectedGeneration,
        long expectedRevision, String control, int entry, String value,
        ItemStack cursor, HolderLookup.Provider registries) {
        if (group < 0 || group >= groups.size() || column < 0
            || column >= COLUMNS
            || generation(group, column) != expectedGeneration
            || revision(group, column) != expectedRevision
            || !filterSupported(group, column))
            return false;
        var original = filter(group, column);
        var capability = original.getCapability(AddonCapabilities.FILTER);
        if (capability == null
            || !acceptsFilter(addon(group, column), original))
            return false;
        var candidate = original.copy();
        if (!capability.edit(candidate, control, entry, value, cursor.copy(),
            registries))
            return false;
        // A config edit is not an identity replacement.
        loading = true;
        try {
            slots.setStackInSlot(filterSlot(group, column), candidate);
        } finally {
            loading = false;
        }
        revision[group * COLUMNS + column]++;
        dirtyGroups.set(group);
        viewChanged();
        return true;
    }

    public IoMode mode(ResourceType<?> type, int member,
        net.minecraft.core.Direction side) {
        var indexed = members.get(type);
        var group = indexed == null ? null : indexed.get(member);
        return group == null ? IoMode.DISABLED : group.mode(side);
    }

    /**
     * Use a named inventory when several handlers have the same local slot
     * numbers.
     */
    public IoMode mode(ResourceType<?> type, String inventory, int member,
        net.minecraft.core.Direction side) {
        return groups.stream()
            .filter(group -> group.type() == type
                && group.inventory().equals(inventory)
                && group.contains(member))
            .findFirst().map(group -> group.mode(side)).orElse(IoMode.DISABLED);
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        var data = unknown.copy();
        for (int i = 0; i < groups.size(); i++) {
            var group = groups.get(i);
            var saved = new CompoundTag();
            var modes = new CompoundTag();
            for (var face : RelativeFace.values())
                modes.putString(face.getSerializedName(),
                    group.sides().get(face).getSerializedName());
            saved.put("sides", modes);
            var contents = new ListTag();
            for (int s = 0; s < SLOTS_PER_GROUP; s++)
                contents.add(slots.getStackInSlot(i * SLOTS_PER_GROUP + s)
                    .saveOptional(registries));
            saved.put("contents", contents);
            data.put(group.id(), saved);
        }
        var result = new CompoundTag();
        result.putInt("version", 1);
        result.put("groups", data);
        result.put("transfers", transfers.copy());
        result.putLong("refund", refund);
        var pending = new ListTag();
        for (var item : recovery)
            pending.add(item.saveOptional(registries));
        result.put("recovery", pending);
        return result;
    }

    public void load(CompoundTag data, HolderLookup.Provider registries) {
        loading = true;
        try {
            recovery.clear();
            transfers = data.getCompound("transfers").copy();
            refund = Math.max(0, data.getLong("refund"));
            for (int i = 0; i < slots.getSlots(); i++)
                slots.setStackInSlot(i, ItemStack.EMPTY);
            unknown = data.getCompound("groups").copy();
            for (int i = 0; i < groups.size(); i++) {
                var group = groups.get(i);
                if (!unknown.contains(group.id(), Tag.TAG_COMPOUND))
                    continue;
                var saved = unknown.getCompound(group.id());
                var modes = saved.getCompound("sides");
                var loaded = new EnumMap<RelativeFace, IoMode>(
                    RelativeFace.class);
                for (var face : RelativeFace.values()) {
                    IoMode mode = group.sides().get(face);
                    for (var candidate : IoMode.values())
                        if (candidate.getSerializedName()
                            .equals(modes.getString(face.getSerializedName())))
                            mode = candidate;
                    loaded.put(face,
                        ResourceGroup.intersect(mode, group.supported()));
                }
                group.sides().copyFrom(new SideConfig(loaded));
                var contents = saved.getList("contents", Tag.TAG_COMPOUND);
                for (int s = 0; s < SLOTS_PER_GROUP; s++) {
                    var stack = ItemStack.parseOptional(registries,
                        contents.getCompound(s));
                    if (stack.getCount() > 1) {
                        recovery.add(stack.copyWithCount(stack.getCount() - 1));
                        stack.setCount(1);
                    }
                    slots.setStackInSlot(i * SLOTS_PER_GROUP + s, stack);
                }
                unknown.remove(group.id());
            }
            for (var tag : data.getList("recovery", Tag.TAG_COMPOUND))
                recovery.add(
                    ItemStack.parseOptional(registries, (CompoundTag) tag));
        } finally {
            loading = false;
        }
        invalidateAddons();
        revalidate();
    }

    private String transferType(String key, CompoundTag state) {
        String type = state.getString("resource");
        if (!type.isEmpty())
            return type;
        int slash = key.lastIndexOf('/');
        int group = slash < 0 ? -1 : index(key.substring(0, slash));
        if (group >= 0)
            return group(group).type().id().toString();
        if (state.contains("fluid"))
            return ResourceType.FLUIDS.id().toString();
        if (state.contains("energy"))
            return ResourceType.ENERGY.id().toString();
        if (state.contains("item"))
            return ResourceType.ITEMS.id().toString();
        return type;
    }

    /**
     * The host must preserve non-item payloads in a dropped container before
     * this clears storage.
     */
    public void dropAll(Consumer<CompoundTag> recoverResources) {
        if (owner.getLevel() == null || owner.getLevel().isClientSide)
            return;
        Objects.requireNonNull(recoverResources);
        var payload = new CompoundTag();
        var resources = new ListTag();
        for (String key : transfers.getAllKeys()) {
            var state = transfers.getCompound(key);
            String type = transferType(key, state);
            if (type.equals(ResourceType.ITEMS.id().toString()))
                continue;
            var pending = AutomationTransfers.recovery(type, state);
            if (!pending.isEmpty()) {
                var entry = new CompoundTag();
                entry.putString("type", type);
                entry.put("data", pending);
                resources.add(entry);
            }
        }
        if (!resources.isEmpty())
            payload.put("resources", resources);
        if (refund > 0)
            payload.putLong("refund", refund);
        // The host must take ownership before anything is cleared or ordinary
        // item drops begin.
        if (!payload.isEmpty())
            recoverResources.accept(payload);
        loading = true;
        try {
            for (String id : unknown.getAllKeys())
                for (var tag : unknown.getCompound(id).getList("contents",
                    Tag.TAG_COMPOUND)) {
                    var item = ItemStack.parseOptional(
                        owner.getLevel().registryAccess(), (CompoundTag) tag);
                    if (!item.isEmpty())
                        recovery.add(item);
                }
            unknown = new CompoundTag();
            for (String key : transfers.getAllKeys()) {
                if (!transferType(key, transfers.getCompound(key))
                    .equals(ResourceType.ITEMS.id().toString()))
                    continue;
                var item = ItemStack.parseOptional(
                    owner.getLevel().registryAccess(),
                    transfers.getCompound(key).getCompound("item"));
                if (!item.isEmpty())
                    recovery.add(item);
            }
            transfers = new CompoundTag();
            refund = 0;
            for (int i = 0; i < slots.getSlots(); i++) {
                var stack = slots.getStackInSlot(i);
                if (!stack.isEmpty())
                    recovery.add(stack.copy());
                slots.setStackInSlot(i, ItemStack.EMPTY);
            }
        } finally {
            loading = false;
        }
        recover(null);
    }

    /** Explicit destructive clearing, for a host's vanilla Clearable hook. */
    public void clear() {
        loading = true;
        try {
            for (int i = 0; i < slots.getSlots(); i++)
                slots.setStackInSlot(i, ItemStack.EMPTY);
        } finally {
            loading = false;
        }
        recovery.clear();
        unknown = new CompoundTag();
        transfers = new CompoundTag();
        refund = 0;
        invalidateAddons();
    }
}
