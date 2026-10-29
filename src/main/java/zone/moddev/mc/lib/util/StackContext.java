package zone.moddev.mc.lib.util;

import javax.annotation.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * A lazily resolved item stack description: an item ID (or the item it belongs to), a count and
 * optional custom data. Used for creative-tab stacks, whose items may not be registered when the
 * definition is read.
 */
public class StackContext {
  public final ResourceLocation item;
  public CompoundTag tag = null;
  public int count = 1;

  private Item theItem;

  /** A description of one {@code item}; null means the item the definition belongs to. */
  public StackContext(@Nullable ResourceLocation item) {
    this.item = item;
  }

  /** Sets the custom data; changes and returns this context. */
  public StackContext withTag(CompoundTag tag) {
    this.tag = tag;
    return this;
  }

  /** Sets the count; changes and returns this context. */
  public StackContext withCount(int count) {
    this.count = count;
    return this;
  }

  /**
   * Builds the stack, resolving the item on first use.
   *
   * @param self the item to use when no ID was given
   * @throws RuntimeException if the item is not registered, or no item is known
   */
  public ItemStack toStack(@Nullable Item self) {
    if (theItem == null) {
      if (this.item != null) {
        theItem = BuiltInRegistries.ITEM.getOptional(this.item).orElse(null);
        if (theItem == null)
          throw new RuntimeException(String.format("The item '%s' is not registered.", this.item));
      } else {
        theItem = self;
        if (theItem == null)
          throw new RuntimeException(
              "No item name specified for a stack context that isn't linked to an item.");
      }
    }
    ItemStack stack = new ItemStack(theItem, count);
    if (tag != null) stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    return stack;
  }
}
