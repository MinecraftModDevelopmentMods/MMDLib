package zone.moddev.mc.lib.machine.neoforge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.machine.internals.InternalsDefinition;
import zone.moddev.mc.lib.machine.internals.MachineParts;
import zone.moddev.mc.lib.machine.internals.ResolvedPart;
import zone.moddev.mc.lib.machine.stat.MachineStats;
import zone.moddev.mc.lib.machine.stat.StatModifier;

/**
 * A machine's internals at runtime: the parts in its sockets and the stats they give. Owned by the
 * block entity, which saves it with {@link #save} and {@link #load} and passes whether the machine
 * is running to the swap methods.
 *
 * <p>{@link #sockets()} has one slot per socket position, in {@link InternalsDefinition}'s layout.
 * Each slot holds one part that resolves to that socket and matches its {@code accepts}; automation
 * can't reach them ({@link Role#MANUAL}). Items already installed stay installed when part files
 * change; stats only count those that still resolve to their socket.
 */
public final class MachineInternals {
  private static final String SOCKETS = "sockets";
  private static final String ORPHANS = "orphans";

  private final InternalsDefinition definition;
  private final Supplier<MachineParts> parts;
  private final ItemStorage sockets;
  private final List<ItemStack> orphans = new ArrayList<>();
  private Runnable onChange = () -> {};
  private MachineStats stats;
  private MachineParts statsParts;
  private int statsGeneration;

  /**
   * Empty internals for {@code definition}.
   *
   * @param parts the part files to read, normally {@code MachineParts::current}
   */
  public MachineInternals(InternalsDefinition definition, Supplier<MachineParts> parts) {
    this.definition = definition;
    this.parts = parts;
    sockets = new ItemStorage(definition.slotCount());
    for (int slot = 0; slot < sockets.getSlots(); slot++) {
      int position = slot;
      sockets.role(Role.MANUAL, slot).limit(1, slot).filter(stack -> fits(position, stack), slot);
    }
    sockets.onChange(this::changed);
  }

  /** Runs after every change, typically the block entity's {@code setChanged}. */
  public MachineInternals onChange(Runnable onChange) {
    this.onChange = onChange;
    return this;
  }

  /** The socket slots, for a menu; the block entity works through the methods below instead. */
  public ItemStorage sockets() {
    return sockets;
  }

  /** The definition these internals were built for. */
  public InternalsDefinition definition() {
    return definition;
  }

  /** The stats from the installed parts; recomputed after a change or a part-file reload. */
  public MachineStats stats() {
    var current = parts.get();
    if (stats == null || statsParts != current || statsGeneration != current.generation()) {
      stats = MachineStats.compute(definition.stats(), modifiers(current));
      statsParts = current;
      statsGeneration = current.generation();
    }
    return stats;
  }

  /** The stats from the installed parts plus {@code extra}, such as upgrade cards. */
  public MachineStats stats(Collection<StatModifier> extra) {
    var all = modifiers(parts.get());
    all.addAll(extra);
    return MachineStats.compute(definition.stats(), all);
  }

  /** The first required socket, in slot order, with an empty position; empty when ready. */
  public Optional<String> missing() {
    for (var entry : definition.sockets().entrySet()) {
      if (entry.getValue().optional()) continue;
      for (int slot : definition.slots(entry.getKey()))
        if (sockets.getStackInSlot(slot).isEmpty()) return Optional.of(entry.getKey());
    }
    return Optional.empty();
  }

  /** Whether a player holding {@code held} may open the engine view. */
  public boolean canOpen(ItemStack held) {
    return definition.openWith().map(match -> match.test(held)).orElse(true);
  }

  /** Whether parts may be installed or removed now. */
  public boolean canSwap(boolean running) {
    return definition.swap().allows(running);
  }

  /**
   * Installs one item of {@code stack} at position {@code index} of {@code socket}. Returns what
   * was not installed: all of it if swapping isn't allowed, the position doesn't exist or is taken,
   * or the item isn't a part for this socket.
   */
  public ItemStack install(String socket, int index, ItemStack stack, boolean running) {
    int slot = slot(socket, index);
    if (slot < 0 || !canSwap(running)) return stack;
    return sockets.insertItem(slot, stack, false);
  }

  /** Takes the part out of position {@code index} of {@code socket}; empty if refused or empty. */
  public ItemStack remove(String socket, int index, boolean running) {
    int slot = slot(socket, index);
    if (slot < 0 || !canSwap(running)) return ItemStack.EMPTY;
    return sockets.extractItem(slot, 1, false);
  }

  /**
   * Adds {@code points} of wear to every part in {@code socket} that has a durability. A part whose
   * wear reaches its durability is destroyed and its position left empty.
   *
   * @return how many parts broke
   */
  public int wear(String socket, int points) {
    int broke = 0;
    var current = parts.get();
    for (int slot : definition.slots(socket)) {
      var stack = sockets.getStackInSlot(slot);
      if (stack.isEmpty()) continue;
      var durability =
          resolve(current, stack)
              .flatMap(
                  part ->
                      part.durability().isPresent()
                          ? Optional.of(part.durability().getAsInt())
                          : Optional.empty());
      if (durability.isEmpty()) continue;
      int wear = stack.getOrDefault(MachineComponents.PART_WEAR.get(), 0) + points;
      if (wear >= durability.get()) {
        sockets.setStackInSlot(slot, ItemStack.EMPTY);
        broke++;
      } else {
        var worn = stack.copy();
        worn.set(MachineComponents.PART_WEAR.get(), wear);
        sockets.setStackInSlot(slot, worn);
      }
    }
    return broke;
  }

  /** Parts from a save that no longer have a position; kept until {@link #dropAll}. */
  public List<ItemStack> orphans() {
    return List.copyOf(orphans);
  }

  /**
   * Saves the parts by socket name ({@code {"sockets": {"gears": [part, ...]}, "orphans": [...]}}),
   * so a later change to the socket layout can't move a part into the wrong socket.
   */
  public CompoundTag save(HolderLookup.Provider provider) {
    var saved = new CompoundTag();
    for (var socket : definition.sockets().keySet()) {
      var list = new ListTag();
      for (int slot : definition.slots(socket))
        list.add(saveStack(provider, sockets.getStackInSlot(slot)));
      saved.put(socket, list);
    }
    var tag = new CompoundTag();
    tag.put(SOCKETS, saved);
    if (!orphans.isEmpty()) {
      var list = new ListTag();
      orphans.forEach(stack -> list.add(saveStack(provider, stack)));
      tag.put(ORPHANS, list);
    }
    return tag;
  }

  /**
   * Loads parts saved by {@link #save}. Parts whose socket is gone, or whose position is past the
   * socket's count, become orphans instead of being lost.
   */
  public void load(HolderLookup.Provider provider, CompoundTag tag) {
    for (int slot = 0; slot < sockets.getSlots(); slot++)
      sockets.setStackInSlot(slot, ItemStack.EMPTY);
    orphans.clear();
    var saved = tag.getCompound(SOCKETS);
    for (var socket : saved.getAllKeys()) {
      var list = saved.getList(socket, Tag.TAG_COMPOUND);
      var slots =
          definition.sockets().containsKey(socket) ? definition.slots(socket) : List.<Integer>of();
      for (int i = 0; i < list.size(); i++) {
        var stack = ItemStack.parseOptional(provider, list.getCompound(i));
        if (stack.isEmpty()) continue;
        if (i < slots.size()) sockets.setStackInSlot(slots.get(i), stack);
        else orphans.add(stack);
      }
    }
    var list = tag.getList(ORPHANS, Tag.TAG_COMPOUND);
    for (int i = 0; i < list.size(); i++) {
      var stack = ItemStack.parseOptional(provider, list.getCompound(i));
      if (!stack.isEmpty()) orphans.add(stack);
    }
    changed();
  }

  /** Drops every part and orphan at {@code pos} and empties the internals. */
  public void dropAll(Level level, BlockPos pos) {
    sockets.dropAll(level, pos);
    for (var stack : orphans)
      Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
    orphans.clear();
  }

  private boolean fits(int slot, ItemStack stack) {
    var socket = definition.socketAt(slot);
    var accepts = definition.sockets().get(socket).accepts();
    return resolve(parts.get(), stack).filter(part -> part.socket().equals(socket)).isPresent()
        && accepts.map(match -> match.test(stack)).orElse(true);
  }

  private List<StatModifier> modifiers(MachineParts current) {
    var modifiers = new ArrayList<StatModifier>();
    for (int slot = 0; slot < sockets.getSlots(); slot++) {
      var socket = definition.socketAt(slot);
      resolve(current, sockets.getStackInSlot(slot))
          .filter(part -> part.socket().equals(socket))
          .ifPresent(part -> modifiers.addAll(part.modifiers()));
    }
    return modifiers;
  }

  private static Optional<ResolvedPart> resolve(MachineParts current, ItemStack stack) {
    if (stack.isEmpty()) return Optional.empty();
    return current.resolve(BuiltInRegistries.ITEM.getKey(stack.getItem()));
  }

  private int slot(String socket, int index) {
    if (!definition.sockets().containsKey(socket)) return -1;
    var slots = definition.slots(socket);
    return index >= 0 && index < slots.size() ? slots.get(index) : -1;
  }

  private void changed() {
    stats = null;
    onChange.run();
  }

  private static Tag saveStack(HolderLookup.Provider provider, ItemStack stack) {
    return stack.isEmpty() ? new CompoundTag() : stack.save(provider);
  }
}
