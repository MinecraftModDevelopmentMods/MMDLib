package zone.moddev.mc.lib.api.automation;

import java.util.*;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import zone.moddev.mc.lib.api.capabilities.*;

/**
 * Persistent group side modes and four addon/filter pairs per group. Selection belongs to menus.
 */
public final class AutomationInventory {
  public static final int COLUMNS = 4;
  public static final int SLOTS_PER_GROUP = 8;
  public static final int MAX_GROUPS = 256;
  private final BlockEntity owner;
  private final List<ResourceGroup<?>> groups;
  private final Map<String, Integer> ids = new LinkedHashMap<>();
  private final long[] generation;
  private final long[] revision;
  private final List<ItemStack> recovery = new ArrayList<>();
  private CompoundTag unknown = new CompoundTag();
  private CompoundTag transfers = new CompoundTag();
  private boolean loading;
  private final ItemStackHandler slots;

  public AutomationInventory(BlockEntity owner, List<ResourceGroup<?>> groups) {
    if (groups.isEmpty() || groups.size() > MAX_GROUPS)
      throw new IllegalArgumentException("Group count");
    this.owner = Objects.requireNonNull(owner);
    this.groups = List.copyOf(groups);
    generation = new long[groups.size() * COLUMNS];
    revision = new long[generation.length];
    var membership = new HashSet<String>();
    for (int i = 0; i < groups.size(); i++) {
      var group = groups.get(i);
      if (ids.putIfAbsent(group.id(), i) != null)
        throw new IllegalArgumentException("Duplicate group ID");
      for (int member : group.members())
        if (!membership.add(group.type().id() + "/" + member))
          throw new IllegalArgumentException("Overlapping resource membership: " + group.id());
      group.sides().onChange(owner::setChanged);
    }
    slots =
        new ItemStackHandler(groups.size() * SLOTS_PER_GROUP) {
          @Override
          public int getSlotLimit(int slot) {
            return 1;
          }

          @Override
          public boolean isItemValid(int slot, ItemStack stack) {
            return accepts(slot, stack);
          }

          @Override
          public void setStackInSlot(int slot, ItemStack stack) {
            if (stack.getCount() > 1)
              throw new IllegalArgumentException("One addon/filter per slot");
            if (!loading && !stack.isEmpty() && !accepts(slot, stack))
              throw new IllegalArgumentException("Incompatible or duplicate addon/filter role");
            // Even replacing an identical stack is a new attachment generation.
            if (!loading && slot % SLOTS_PER_GROUP < COLUMNS)
              detach(slot / SLOTS_PER_GROUP, slot % COLUMNS);
            super.setStackInSlot(slot, stack);
          }

          @Override
          protected void onContentsChanged(int slot) {
            if (loading) return;
            int group = slot / SLOTS_PER_GROUP, column = slot % COLUMNS;
            int key = group * COLUMNS + column;
            if (slot % SLOTS_PER_GROUP < COLUMNS) {
              generation[key]++;
              // extractItem mutates without calling setStackInSlot.
              var addon = addon(group, column);
              var filter = filter(group, column);
              if (!filter.isEmpty() && addon.isEmpty()) detach(group, column);
            } else {
              generation[key]++;
              revision[key]++;
            }
            owner.setChanged();
          }
        };
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

  public ItemStackHandler slots() {
    return slots;
  }

  public CompoundTag transferState(String group, int column) {
    String key = group + "/" + column;
    if (!transfers.contains(key, Tag.TAG_COMPOUND)) transfers.put(key, new CompoundTag());
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
    if (slot < 0 || slot >= groups.size() * SLOTS_PER_GROUP || stack.isEmpty()) return false;
    int group = slot / SLOTS_PER_GROUP, sub = slot % SLOTS_PER_GROUP;
    if (sub < COLUMNS) return acceptsAddon(group(group), slots, slot, stack);
    return filterSupported(group, sub - COLUMNS)
        && acceptsFilter(addon(group, sub - COLUMNS), stack);
  }

  /** Shared by authoritative storage and the menu's separate client-side slot snapshot. */
  public static boolean acceptsAddon(
      ResourceGroup<?> group, IItemHandler contents, int slot, ItemStack stack) {
    if (slot < 0
        || slot >= contents.getSlots()
        || slot % SLOTS_PER_GROUP >= COLUMNS
        || stack.isEmpty()) return false;
    var capability = stack.getCapability(AddonCapabilities.ADDON);
    if (capability == null) return false;
    var spec = capability.specification(stack);
    if (!spec.accepts(group)) return false;
    int base = slot - slot % SLOTS_PER_GROUP;
    for (int c = 0; c < COLUMNS; c++) {
      if (base + c == slot) continue;
      var other = contents.getStackInSlot(base + c);
      var provider = other.getCapability(AddonCapabilities.ADDON);
      if (provider != null && spec.conflictsWith(provider.specification(other))) return false;
    }
    return true;
  }

  private boolean acceptsFilter(ItemStack addon, ItemStack filter) {
    var capability = addon.getCapability(AddonCapabilities.ADDON);
    return capability != null && capability.acceptsFilter(addon, filter);
  }

  private void detach(int group, int column) {
    var filter = filter(group, column);
    if (!filter.isEmpty()) {
      recovery.add(filter.copy());
      slots.setStackInSlot(filterSlot(group, column), ItemStack.EMPTY);
    }
    generation[group * COLUMNS + column]++;
  }

  /** At the end of the authoritative menu interaction, after the source slot has been freed. */
  public void recover(Player player) {
    if (owner.getLevel() == null || owner.getLevel().isClientSide) return;
    for (var stack : recovery) {
      if (player != null)
        stack =
            net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(
                new net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper(
                    player.getInventory()),
                stack,
                false);
      if (!stack.isEmpty())
        Containers.dropItemStack(
            owner.getLevel(),
            owner.getBlockPos().getX() + .5,
            owner.getBlockPos().getY() + .5,
            owner.getBlockPos().getZ() + .5,
            stack);
    }
    if (!recovery.isEmpty()) owner.setChanged();
    recovery.clear();
  }

  /** Preserve incompatible items; recover duplicate roles and orphaned filters without loss. */
  public void revalidate() {
    for (int g = 0; g < groups.size(); g++) {
      var claimed = new ArrayList<AddonSpec>();
      for (int c = 0; c < COLUMNS; c++) {
        var stack = addon(g, c);
        var provider = stack.getCapability(AddonCapabilities.ADDON);
        if (provider != null) {
          var spec = provider.specification(stack);
          if (claimed.stream().anyMatch(spec::conflictsWith)) {
            // Keep the first role after reload; recover duplicates and their attached filters.
            recovery.add(stack.copy());
            slots.setStackInSlot(addonSlot(g, c), ItemStack.EMPTY);
          } else claimed.add(spec);
        }
        if (!filter(g, c).isEmpty() && addon(g, c).isEmpty()) detach(g, c);
      }
    }
  }

  public boolean edit(
      int group,
      int column,
      long expectedGeneration,
      long expectedRevision,
      String control,
      int entry,
      String value,
      ItemStack cursor,
      HolderLookup.Provider registries) {
    if (group < 0
        || group >= groups.size()
        || column < 0
        || column >= COLUMNS
        || generation(group, column) != expectedGeneration
        || revision(group, column) != expectedRevision
        || !filterSupported(group, column)) return false;
    var original = filter(group, column);
    var capability = original.getCapability(AddonCapabilities.FILTER);
    if (capability == null || !acceptsFilter(addon(group, column), original)) return false;
    var candidate = original.copy();
    if (!capability.edit(candidate, control, entry, value, cursor.copy(), registries)) return false;
    // A config edit is not an identity replacement.
    loading = true;
    try {
      slots.setStackInSlot(filterSlot(group, column), candidate);
    } finally {
      loading = false;
    }
    revision[group * COLUMNS + column]++;
    owner.setChanged();
    return true;
  }

  public IoMode mode(ResourceType<?> type, int member, net.minecraft.core.Direction side) {
    return groups.stream()
        .filter(g -> g.type() == type && g.contains(member))
        .findFirst()
        .map(g -> g.mode(side))
        .orElse(IoMode.DISABLED);
  }

  public CompoundTag save(HolderLookup.Provider registries) {
    var data = unknown.copy();
    for (int i = 0; i < groups.size(); i++) {
      var group = groups.get(i);
      var saved = new CompoundTag();
      var modes = new CompoundTag();
      for (var face : RelativeFace.values())
        modes.putString(face.getSerializedName(), group.sides().get(face).getSerializedName());
      saved.put("sides", modes);
      var contents = new ListTag();
      for (int s = 0; s < SLOTS_PER_GROUP; s++)
        contents.add(slots.getStackInSlot(i * SLOTS_PER_GROUP + s).saveOptional(registries));
      saved.put("contents", contents);
      data.put(group.id(), saved);
    }
    var result = new CompoundTag();
    result.putInt("version", 1);
    result.put("groups", data);
    result.put("transfers", transfers.copy());
    var pending = new ListTag();
    for (var item : recovery) pending.add(item.saveOptional(registries));
    result.put("recovery", pending);
    return result;
  }

  public void load(CompoundTag data, HolderLookup.Provider registries) {
    loading = true;
    try {
      recovery.clear();
      transfers = data.getCompound("transfers").copy();
      for (int i = 0; i < slots.getSlots(); i++) slots.setStackInSlot(i, ItemStack.EMPTY);
      unknown = data.getCompound("groups").copy();
      for (int i = 0; i < groups.size(); i++) {
        var group = groups.get(i);
        if (!unknown.contains(group.id(), Tag.TAG_COMPOUND)) continue;
        var saved = unknown.getCompound(group.id());
        var modes = saved.getCompound("sides");
        var loaded = new EnumMap<RelativeFace, IoMode>(RelativeFace.class);
        for (var face : RelativeFace.values()) {
          IoMode mode = group.sides().get(face);
          for (var candidate : IoMode.values())
            if (candidate.getSerializedName().equals(modes.getString(face.getSerializedName())))
              mode = candidate;
          loaded.put(face, ResourceGroup.intersect(mode, group.supported()));
        }
        group.sides().copyFrom(new SideConfig(loaded));
        var contents = saved.getList("contents", Tag.TAG_COMPOUND);
        for (int s = 0; s < SLOTS_PER_GROUP; s++) {
          var stack = ItemStack.parseOptional(registries, contents.getCompound(s));
          if (stack.getCount() > 1) {
            recovery.add(stack.copyWithCount(stack.getCount() - 1));
            stack.setCount(1);
          }
          slots.setStackInSlot(i * SLOTS_PER_GROUP + s, stack);
        }
        unknown.remove(group.id());
      }
      for (var tag : data.getList("recovery", Tag.TAG_COMPOUND))
        recovery.add(ItemStack.parseOptional(registries, (CompoundTag) tag));
    } finally {
      loading = false;
    }
    Arrays.setAll(generation, i -> generation[i] + 1);
    revalidate();
  }

  public void dropAll() {
    if (owner.getLevel() == null || owner.getLevel().isClientSide) return;
    loading = true;
    try {
      for (String id : unknown.getAllKeys())
        for (var tag : unknown.getCompound(id).getList("contents", Tag.TAG_COMPOUND)) {
          var item = ItemStack.parseOptional(owner.getLevel().registryAccess(), (CompoundTag) tag);
          if (!item.isEmpty()) recovery.add(item);
        }
      unknown = new CompoundTag();
      for (String key : transfers.getAllKeys()) {
        var item =
            ItemStack.parseOptional(
                owner.getLevel().registryAccess(), transfers.getCompound(key).getCompound("item"));
        if (!item.isEmpty()) recovery.add(item);
      }
      transfers = new CompoundTag();
      for (int i = 0; i < slots.getSlots(); i++) {
        var stack = slots.getStackInSlot(i);
        if (!stack.isEmpty()) recovery.add(stack.copy());
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
      for (int i = 0; i < slots.getSlots(); i++) slots.setStackInSlot(i, ItemStack.EMPTY);
    } finally {
      loading = false;
    }
    recovery.clear();
    unknown = new CompoundTag();
    transfers = new CompoundTag();
    Arrays.setAll(generation, i -> generation[i] + 1);
    owner.setChanged();
  }
}
