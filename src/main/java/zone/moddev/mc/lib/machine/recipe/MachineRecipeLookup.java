package zone.moddev.mc.lib.machine.recipe;

import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import zone.moddev.mc.lib.machine.inventory.MachineInventory;

/**
 * Finds the recipe of one type that a machine can run now. It tries the machine's last recipe
 * first, by ID, so a {@code /reload} that changes or removes it is picked up. When several recipes
 * match, which one wins is unspecified; keep a type's recipes from overlapping.
 */
public final class MachineRecipeLookup {
  private final MachineRecipeType type;
  @Nullable private ResourceLocation last;

  MachineRecipeLookup(MachineRecipeType type) {
    this.type = type;
  }

  /** A recipe whose inputs are present and whose products fit; changes nothing. */
  public Optional<RecipeHolder<MachineRecipeEntry>> find(Level level, MachineInventory inventory) {
    var found =
        level.getRecipeManager().getRecipeFor(type, new MachineRecipeInput(inventory), level, last);
    found.ifPresent(holder -> last = holder.id());
    return found;
  }
}
