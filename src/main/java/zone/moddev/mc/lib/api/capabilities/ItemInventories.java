package zone.moddev.mc.lib.api.capabilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.items.IItemHandler;

import zone.moddev.mc.lib.machine.inventory.SlotPlan;

/**
 * Named, independent inventories with a stable combined view for capabilities and legacy callers.
 * Contents, filters and reservations belong to the individual ItemStorage instances.
 */
public final class ItemInventories implements ItemStore {
    public record Address(String inventory, int slot) {
    }

    private final Map<String, ItemStorage> inventories;
    private final List<Address> addresses;
    private final Map<Address, Integer> indexes;
    private ListTag recovery = new ListTag();

    public ItemInventories(Map<String, ItemStorage> inventories) {
        this(
            inventories,
            inventories.entrySet().stream()
                .flatMap(
                    entry -> java.util.stream.IntStream.range(0, entry.getValue().getSlots())
                        .mapToObj(slot -> new Address(entry.getKey(), slot)))
                .toList());
    }

    private ItemInventories(Map<String, ItemStorage> inventories, List<Address> addresses) {
        var copy = new LinkedHashMap<String, ItemStorage>();
        inventories.forEach(
            (name, storage) -> {
                if (name == null || !name.matches("[a-z0-9_.-]{1,64}"))
                    throw new IllegalArgumentException("Invalid inventory name: " + name);
                if (copy.containsValue(storage))
                    throw new IllegalArgumentException("Inventory already registered");
                copy.put(name, Objects.requireNonNull(storage));
            });
        this.inventories = Collections.unmodifiableMap(copy);
        this.addresses = List.copyOf(addresses);
        var indexes = new HashMap<Address, Integer>();
        for (int i = 0; i < addresses.size(); i++)
            indexes.put(addresses.get(i), i);
        this.indexes = Map.copyOf(indexes);
    }

    /** Allocate one real inventory per name, retaining the supplied legacy combined ordering. */
    public static ItemInventories partition(List<String> names) {
        var sizes = new LinkedHashMap<String, Integer>();
        var addresses = new ArrayList<Address>();
        for (String name : names) {
            int local = sizes.getOrDefault(name, 0);
            addresses.add(new Address(name, local));
            sizes.put(name, local + 1);
        }
        var stores = new LinkedHashMap<String, ItemStorage>();
        sizes.forEach((name, size) -> stores.put(name, new ItemStorage(size)));
        return new ItemInventories(stores, addresses);
    }

    public Map<String, ItemStorage> inventories() {
        return inventories;
    }

    public ItemStorage inventory(String name) {
        var storage = inventories.get(name);
        if (storage == null)
            throw new IllegalArgumentException("Unknown inventory: " + name);
        return storage;
    }

    public Address address(int slot) {
        return addresses.get(slot);
    }

    public int index(String inventory, int slot) {
        return indexes.getOrDefault(new Address(inventory, slot), -1);
    }

    public ItemStorage storage(int slot) {
        return inventory(address(slot).inventory());
    }

    public int localSlot(int slot) {
        return address(slot).slot();
    }

    public int getSlots() {
        return addresses.size();
    }

    public ItemStack getStackInSlot(int slot) {
        return storage(slot).getStackInSlot(localSlot(slot));
    }

    public void setStackInSlot(int slot, ItemStack stack) {
        storage(slot).setStackInSlot(localSlot(slot), stack);
    }

    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return storage(slot).insertItem(localSlot(slot), stack, simulate);
    }

    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return storage(slot).extractItem(localSlot(slot), amount, simulate);
    }

    public int getSlotLimit(int slot) {
        return storage(slot).getSlotLimit(localSlot(slot));
    }

    public boolean isItemValid(int slot, ItemStack stack) {
        return storage(slot).isItemValid(localSlot(slot), stack);
    }

    public Role role(int slot) {
        return storage(slot).role(localSlot(slot));
    }

    public ItemInventories role(Role role, int... slots) {
        for (int slot : slots)
            storage(slot).role(role, localSlot(slot));
        return this;
    }

    public ItemInventories limit(int limit, int... slots) {
        for (int slot : slots)
            storage(slot).limit(limit, localSlot(slot));
        return this;
    }

    public ItemInventories slotId(int slot, String id) {
        storage(slot).slotId(localSlot(slot), id);
        return this;
    }

    public ItemInventories restrict(Predicate<ItemStack> filter, int... slots) {
        for (int slot : slots)
            storage(slot).restrict(filter, localSlot(slot));
        return this;
    }

    public ItemInventories onChange(Runnable changed) {
        inventories.values().forEach(storage -> storage.onChange(changed));
        return this;
    }

    public ItemStack insertResult(int slot, ItemStack stack, boolean simulate) {
        return storage(slot).insertResult(localSlot(slot), stack, simulate);
    }

    public void restoreStack(int slot, ItemStack stack) {
        storage(slot).restoreStack(localSlot(slot), stack);
    }

    public long revision() {
        return inventories.values().stream().mapToLong(ItemStorage::revision).sum();
    }

    public void markChanged() {
        inventories.values().forEach(ItemStorage::markChanged);
    }

    public SlotPlan plan(int slot) {
        return storage(slot).plan(localSlot(slot));
    }

    public void setPlan(int slot, SlotPlan plan) {
        storage(slot).setPlan(localSlot(slot), plan);
    }

    public boolean hasPlan(int slot) {
        return slot >= 0 && slot < getSlots() && storage(slot).hasPlan(localSlot(slot));
    }

    public int freeToTake(int slot) {
        return storage(slot).freeToTake(localSlot(slot));
    }

    public int maxHeld(int slot, ItemStack stack) {
        return storage(slot).maxHeld(localSlot(slot), stack);
    }

    public void clear() {
        recovery.clear();
        inventories.values().forEach(ItemStorage::clear);
    }

    public IItemHandler view(IntFunction<IoMode> mode) {
        return new IItemHandler() {
            private boolean valid(int slot) {
                return slot >= 0 && slot < getSlots();
            }

            private IItemHandler port(int slot) {
                return storage(slot).view(mode.apply(slot));
            }

            public int getSlots() {
                return ItemInventories.this.getSlots();
            }

            public ItemStack getStackInSlot(int slot) {
                return valid(slot) ? ItemInventories.this.getStackInSlot(slot) : ItemStack.EMPTY;
            }

            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return valid(slot)
                    ? port(slot).insertItem(localSlot(slot), stack, simulate)
                    : stack;
            }

            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return valid(slot)
                    ? port(slot).extractItem(localSlot(slot), amount, simulate)
                    : ItemStack.EMPTY;
            }

            public int getSlotLimit(int slot) {
                return valid(slot) ? ItemInventories.this.getSlotLimit(slot) : 0;
            }

            public boolean isItemValid(int slot, ItemStack stack) {
                return valid(slot) && port(slot).isItemValid(localSlot(slot), stack);
            }
        };
    }

    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        assignDefaultSlotIds();
        var result = new CompoundTag();
        var named = new CompoundTag();
        inventories.forEach((name, storage) -> named.put(name, storage.serializeNBT(registries)));
        result.put("Inventories", named);
        result.put("Recovery", recovery.copy());
        return result;
    }

    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        deserializeNBT(registries, tag, IntUnaryOperator.identity());
    }

    public void deserializeNBT(
        HolderLookup.Provider registries, CompoundTag tag, IntUnaryOperator legacySlots) {
        assignDefaultSlotIds();
        // Resolve the complete old/new save before restoring any processor reservations.
        var entries = tag.getList("Items", Tag.TAG_COMPOUND).copy();
        entries.addAll(tag.getList("Recovery", Tag.TAG_COMPOUND).copy());
        // Old saves used globally unique IDs; new saves namespace each inventory's local IDs.
        for (var value : entries) {
            var entry = (CompoundTag) value;
            String id = entry.getString("SlotId");
            if (!id.isEmpty() && !id.contains("::")) {
                int found = -1;
                for (int i = 0; i < getSlots(); i++)
                    if (id.equals(storage(i).slotId(localSlot(i)))) {
                        if (found != -1) {
                            found = -2;
                            break;
                        }
                        found = i;
                    }
                if (found >= 0)
                    entry.putString("SlotId", address(found).inventory() + "::" + id);
            }
        }
        var named = tag.getCompound("Inventories");
        for (String name : named.getAllKeys()) {
            var saved = named.getCompound(name);
            var values = saved.getList("Items", Tag.TAG_COMPOUND).copy();
            values.addAll(saved.getList("Recovery", Tag.TAG_COMPOUND).copy());
            for (var value : values) {
                var entry = (CompoundTag) value;
                int local = entry.getInt("Slot");
                int current = index(name, local);
                entry.putInt("Slot", current);
                entry.putString(
                    "SlotId",
                    name
                        + "::"
                        + (entry.contains("SlotId")
                            ? entry.getString("SlotId")
                            : Integer.toString(local)));
                entries.add(entry);
            }
        }
        var migration = new ItemStorage(getSlots());
        for (int i = 0; i < getSlots(); i++) {
            String id = storage(i).slotId(localSlot(i));
            if (id != null)
                migration.slotId(i, address(i).inventory() + "::" + id);
            migration.limit(getSlotLimit(i), i);
        }
        var flat = new CompoundTag();
        flat.put("Items", entries);
        migration.deserializeNBT(
            registries, flat, named.isEmpty() ? legacySlots : IntUnaryOperator.identity());
        clear();
        for (int i = 0; i < getSlots(); i++)
            restoreStack(i, migration.getStackInSlot(i));
        recovery = migration.recoveryContents();
    }

    public ListTag recoveryContents() {
        return recovery.copy();
    }

    /** Apply fallbacks only after callers have supplied their explicit definition IDs. */
    private void assignDefaultSlotIds() {
        for (var storage : inventories.values()) {
            var used = new java.util.HashSet<String>();
            for (int slot = 0; slot < storage.getSlots(); slot++)
                if (storage.slotId(slot) != null)
                    used.add(storage.slotId(slot));
            for (int slot = 0; slot < storage.getSlots(); slot++) {
                if (storage.slotId(slot) != null)
                    continue;
                String id = Integer.toString(slot);
                while (!used.add(id))
                    id = "_" + id;
                storage.slotId(slot, id);
            }
        }
    }

    public void dropAll(Level level, BlockPos pos, Consumer<CompoundTag> receiver) {
        StorageRecovery.handoff(recovery, "mmdlib:items", "item", receiver);
        inventories.values().forEach(storage -> storage.dropAll(level, pos, receiver));
    }
}
