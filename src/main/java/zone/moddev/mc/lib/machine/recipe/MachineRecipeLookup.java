package zone.moddev.mc.lib.machine.recipe;

import java.util.Comparator;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import zone.moddev.mc.lib.machine.inventory.MachineInventory;

/**
 * Finds the recipe of one type that a machine can run now. It tries the machine's last recipe
 * first, by ID, so a {@code /reload} that changes or removes it is picked up. When several recipes
 * match, which one wins is unspecified; keep a type's recipes from overlapping. {@link #explain}
 * says why nothing ran.
 */
public final class MachineRecipeLookup {
  /** A recipe and how it does in the machine now. */
  public record Attempt(RecipeHolder<MachineRecipeEntry> recipe, MatchResult result) {}

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

  /**
   * The recipe closest to running, for a screen or debugging: one that can run, then one with its
   * inputs but no room for its products, then one missing an input while the machine holds another
   * of its item or fluid inputs. Between equals it picks the machine's last recipe, then the lowest
   * ID.
   *
   * <p>Empty when the machine holds nothing any recipe uses, so a screen can say so rather than
   * name a missing input of a recipe nobody asked for. If the machine suits none of the recipes, it
   * returns one of those, as that's a setup problem worth seeing. Also empty when the type has no
   * recipes. It checks every recipe, so call it when the answer will be shown rather than every
   * tick. Changes nothing.
   */
  public Optional<Attempt> explain(Level level, MachineInventory inventory) {
    var attempts =
        type.recipes(level.getRecipeManager()).stream()
            .map(
                holder ->
                    new Attempt(holder, RecipeMatcher.check(holder.value().recipe(), inventory)))
            .toList();
    var closest =
        attempts.stream().filter(attempt -> isClose(attempt, inventory)).min(closestFirst());
    if (closest.isPresent() || attempts.stream().anyMatch(attempt -> !isUnsuited(attempt)))
      return closest;
    return attempts.stream().min(closestFirst());
  }

  private Comparator<Attempt> closestFirst() {
    return Comparator.comparingInt((Attempt attempt) -> rank(attempt.result().reason()))
        .thenComparing(attempt -> !attempt.recipe().id().equals(last))
        .thenComparing(attempt -> attempt.recipe().id());
  }

  private static boolean isClose(Attempt attempt, MachineInventory inventory) {
    return switch (attempt.result().reason()) {
      case OK, NO_ROOM -> true;
      case MISSING_INPUT ->
          RecipeMatcher.holdsSomeInput(attempt.recipe().value().recipe(), inventory);
      case WRONG_KIND, NO_GROUP -> false;
    };
  }

  private static boolean isUnsuited(Attempt attempt) {
    var reason = attempt.result().reason();
    return reason == MatchResult.Reason.WRONG_KIND || reason == MatchResult.Reason.NO_GROUP;
  }

  private static int rank(MatchResult.Reason reason) {
    return switch (reason) {
      case OK -> 0;
      case NO_ROOM -> 1;
      case MISSING_INPUT -> 2;
      case WRONG_KIND, NO_GROUP -> 3;
    };
  }
}
