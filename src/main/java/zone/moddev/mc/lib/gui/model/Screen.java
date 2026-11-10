package zone.moddev.mc.lib.gui.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * @param origins original XML files by element attributes; diagnostic only, excluded from the
 *     fingerprint
 * @param implicitElements stable automatically supplied player sections, included in traversal
 */
public record Screen(
    ResourceLocation id,
    String source,
    Text title,
    String blockEntity,
    boolean playerInventory,
    List<Element> body,
    List<Element> tabs,
    Map<Common, String> origins,
    List<Element> implicitElements) {
  /** Copies the lists. */
  public Screen {
    body = List.copyOf(body);
    tabs = List.copyOf(tabs);
    implicitElements = List.copyOf(implicitElements);
    origins = java.util.Collections.unmodifiableMap(new java.util.IdentityHashMap<>(origins));
  }

  public Screen(
      ResourceLocation id,
      String source,
      Text title,
      String blockEntity,
      boolean playerInventory,
      List<Element> body,
      List<Element> tabs,
      Map<Common, String> origins) {
    this(
        id,
        source,
        title,
        blockEntity,
        playerInventory,
        body,
        tabs,
        origins,
        playerInventory
            ? zone.moddev.mc.lib.gui.model.containers.PlayerInventoryElement.defaults(body, tabs)
            : List.of());
  }

  public Screen(
      ResourceLocation id,
      String source,
      Text title,
      String blockEntity,
      boolean playerInventory,
      List<Element> body,
      List<Element> tabs) {
    this(id, source, title, blockEntity, playerInventory, body, tabs, Map.of());
  }

  /** Original XML file of an element, including elements inherited from a base screen. */
  public String source(Common common) {
    return origins.getOrDefault(common, source);
  }

  /**
   * Visits the body, tabs and implicit sections depth first, preserving stable element identities.
   */
  public void forEach(Consumer<Element> visitor) {
    body.forEach(element -> visit(element, visitor));
    tabs.forEach(element -> visit(element, visitor));
    implicitElements.forEach(element -> visit(element, visitor));
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

  /** Stable model fingerprint used to reject differing client/server screen definitions. */
  public String fingerprint() {
    String model =
        // The implicit equipment slots also change menu numbering, even with identical XML.
        "equipment-v2|"
            + id
            + "|"
            + title
            + "|"
            + blockEntity
            + "|"
            + playerInventory
            + "|"
            + body
            + "|"
            + tabs
            + "|implicit:"
            + implicitElements
            + "|element-types:"
            + elements().stream().map(element -> element.getClass().getName()).toList()
            + "|state:"
            + elements().stream()
                .map(
                    element ->
                        element instanceof zone.moddev.mc.lib.gui.sync.SynchronizedElement synced
                            ? synced.synchronizedValues().stream()
                                .map(
                                    field ->
                                        field.id()
                                            + "/"
                                            + field.maxBytes()
                                            + "/"
                                            + field.affectsLayout())
                                .toList()
                                .toString()
                            : "-")
                .toList();
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(model.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private static void visit(Element element, Consumer<Element> visitor) {
    visitor.accept(element);
    element.children().forEach(child -> visit(child, visitor));
  }
}
