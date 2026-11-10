package zone.moddev.mc.lib.api.capabilities;

import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Item storage access, including a combined view over independently owned inventories. */
public interface ItemStore extends IItemHandlerModifiable, ReservedSlots {
  /** Physical owner and local index, used for reservation and transaction identity. */
  ItemStorage storage(int slot);

  int localSlot(int slot);

  Role role(int slot);

  ItemStore restrict(Predicate<ItemStack> filter, int... slots);

  ItemStack insertResult(int slot, ItemStack stack, boolean simulate);

  void restoreStack(int slot, ItemStack stack);

  long revision();

  void markChanged();

  void clear();

  CompoundTag serializeNBT(HolderLookup.Provider registries);

  void deserializeNBT(
      HolderLookup.Provider registries, CompoundTag tag, IntUnaryOperator legacySlots);

  void dropAll(Level level, BlockPos pos, Consumer<CompoundTag> recovery);

  IItemHandler view(IntFunction<IoMode> mode);
}
