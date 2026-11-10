package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.client.*;
import zone.moddev.mc.lib.gui.model.*;

class TooltipCompositionTest {
  static Common authored(String text) {
    var c = Common.empty();
    return new Common(
        c.id(),
        c.value(),
        c.max(),
        Optional.of(new Text("test.tip", Optional.of(text))),
        Optional.of(new Text("test.shift", Optional.of("Detailed {value}/{max}"))),
        c.clicks(),
        c.onHover(),
        c.hoverShows(),
        c.jeiRecipes(),
        0,
        0,
        0);
  }

  record Leaf(Common common) implements Element {}

  record Parent(Common common, Element child, String name) implements Element {
    public List<Element> children() {
      return List.of(child);
    }

    public void appendChildTooltip(TooltipContext c, List<Component> rows) {
      assertSame(this, c.element());
      assertSame(child, c.child().orElseThrow());
      assertNotSame(this, c.target());
      assertEquals(12, c.mouseX());
      assertEquals(34, c.mouseY());
      rows.add(Component.literal(name));
    }
  }

  static class Access implements ElementRenderAccess {
    final Map<Element, Element> parents = new IdentityHashMap<>();
    final Set<Element> hidden = Collections.newSetFromMap(new IdentityHashMap<>());

    public Font font() {
      return null;
    }

    public zone.moddev.mc.lib.gui.layout.Rect viewport() {
      return new zone.moddev.mc.lib.gui.layout.Rect(0, 0, 100, 100);
    }

    public boolean visible(Element e) {
      return !hidden.contains(e);
    }

    public Optional<Element> parent(Element e) {
      return Optional.ofNullable(parents.get(e));
    }

    public Object value(Element e, boolean max) {
      return max ? 9 : 7;
    }

    public List<Component> serverTooltip(Element e) {
      return List.of(Component.literal("server"));
    }
  }

  @Test
  void elementThenBehaviorThenParentsContributeOnceInOrder() {
    var leaf = new Leaf(authored("Leaf {value}/{max}"));
    var parent = new Parent(authored("Never repeat this"), leaf, "parent");
    var root = new Parent(authored("Nor this"), parent, "root");
    var access = new Access();
    access.parents.put(leaf, parent);
    access.parents.put(parent, root);
    var rows =
        ElementTooltips.collect(
            leaf,
            access,
            e ->
                new ElementBehavior() {
                  public List<Component> tooltip(int x, int y) {
                    return List.of(Component.literal("behavior"));
                  }

                  public void appendChildTooltip(TooltipContext c, List<Component> rows) {
                    assertSame(leaf, c.target());
                    rows.add(Component.literal("parent behavior"));
                  }
                },
            12,
            34,
            true,
            true,
            false);
    assertEquals(
        List.of(
            "server",
            "Detailed 7/9",
            "behavior",
            "parent",
            "parent behavior",
            "root",
            "parent behavior"),
        rows.stream().map(Component::getString).toList());
  }

  @Test
  void elementCanReplaceDefaultContentAndParentsMustOptIn() {
    Element leaf =
        new Element() {
          public Common common() {
            return authored("unused");
          }

          public void appendTooltip(TooltipContext c, List<Component> rows) {
            assertTrue(c.child().isEmpty());
            rows.add(Component.literal("owned"));
          }
        };
    var parent = new Leaf(authored("not inherited"));
    var access = new Access();
    access.parents.put(leaf, parent);
    var rows =
        ElementTooltips.collect(
            leaf, access, e -> new ElementBehavior() {}, 0, 0, false, false, false);
    assertEquals(List.of("owned"), rows.stream().map(Component::getString).toList());
  }

  @Test
  void styledMultilineValueRowsRetainFormatting() {
    Element leaf =
        new Element() {
          public Common common() {
            return Common.empty();
          }

          public List<Component> valueTooltip(Object value, Object maximum) {
            return List.of(Component.literal("one\ntwo").withStyle(ChatFormatting.RED));
          }
        };
    var rows =
        ElementTooltips.collect(
            leaf, new Access(), e -> new ElementBehavior() {}, 0, 0, false, false, false);
    assertEquals(List.of("server", "one", "two"), rows.stream().map(Component::getString).toList());
    var colors = new ArrayList<Integer>();
    rows.getLast()
        .visit(
            (style, text) -> {
              if (!text.isEmpty()) colors.add(style.getColor().getValue());
              return Optional.empty();
            },
            net.minecraft.network.chat.Style.EMPTY);
    assertEquals(List.of(ChatFormatting.RED.getColor()), colors);
  }

  @Test
  void hiddenTargetsAndCyclicParentsDoNotSilentlyCompose() {
    var leaf = new Leaf(Common.empty());
    var access = new Access();
    access.hidden.add(leaf);
    assertTrue(
        ElementTooltips.collect(
                leaf,
                access,
                e -> {
                  throw new AssertionError();
                },
                0,
                0,
                false,
                false,
                false)
            .isEmpty());
    access.hidden.clear();
    access.parents.put(leaf, leaf);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ElementTooltips.collect(
                leaf, access, e -> new ElementBehavior() {}, 0, 0, false, false, false));
  }
}
