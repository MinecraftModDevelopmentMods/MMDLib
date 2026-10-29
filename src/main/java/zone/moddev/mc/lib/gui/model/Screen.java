package zone.moddev.mc.lib.gui.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.resources.ResourceLocation;

/**
 * A loaded screen file: its ID, title, the block-entity class its getters belong to, and its
 * elements. The ID matches the registry name of the block-entity type whose screen it is.
 *
 * @param id the screen ID, from the file's path
 * @param source the file it came from, for messages
 * @param title the screen title
 * @param blockEntity the fully qualified block-entity class name
 * @param playerInventory whether the player inventory is shown (added at the bottom unless a {@code
 *     player-inventory} element places it)
 * @param body the main panel's elements
 * @param tabs side tabs and pop-outs, in document order
 */
public record Screen(
    ResourceLocation id,
    String source,
    Text title,
    String blockEntity,
    boolean playerInventory,
    List<Element> body,
    List<Element> tabs) {
  /** Copies the lists. */
  public Screen {
    body = List.copyOf(body);
    tabs = List.copyOf(tabs);
  }

  /** Visits every element, depth first in document order: the body, then the tabs. */
  public void forEach(Consumer<Element> visitor) {
    body.forEach(element -> visit(element, visitor));
    tabs.forEach(element -> visit(element, visitor));
  }

  /** Every element, depth first in document order. */
  public List<Element> elements() {
    var all = new ArrayList<Element>();
    forEach(all::add);
    return all;
  }

  /** The element with {@code id}, if any. */
  public Optional<Element> find(String id) {
    return elements().stream()
        .filter(e -> e.common().id().filter(id::equals).isPresent())
        .findFirst();
  }

  private static void visit(Element element, Consumer<Element> visitor) {
    visitor.accept(element);
    element.children().forEach(child -> visit(child, visitor));
  }
}
