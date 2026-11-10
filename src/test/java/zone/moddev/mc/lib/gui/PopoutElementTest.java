package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.function.Function;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.client.ElementRenderers;
import zone.moddev.mc.lib.gui.model.*;
import zone.moddev.mc.lib.gui.model.elements.PopoutElement;

class PopoutElementTest {
  private final Function<Text, Component> text =
      t -> Component.literal(t.fallback().orElse(t.key()));

  private PopoutElement element(PopoutKind kind, String fixed) {
    return new PopoutElement(
        Common.empty(),
        kind,
        Optional.empty(),
        Edge.LEFT,
        Optional.ofNullable(fixed).map(value -> new Text("test", Optional.of(value))));
  }

  @Test
  void defaultColoursAndEmptyContentRemainDefinedByTheElement() {
    assertEquals(0xf1d462, element(PopoutKind.INFO, null).colour());
    assertEquals(0xe7ac50, element(PopoutKind.WARNING, null).colour());
    assertEquals(0xd85c59, element(PopoutKind.ERROR, null).colour());
    assertTrue(element(PopoutKind.WARNING, null).content(null, text).isEmpty());
    assertTrue(element(PopoutKind.WARNING, null).content(List.of(), text).isEmpty());
    assertEquals(
        List.of("Fixed"),
        element(PopoutKind.INFO, "Fixed").content(42, text).stream()
            .map(Component::getString)
            .toList());
  }

  @Test
  void fixedEmphasisAndStyledLiveParagraphsPreserveSpacingAndOwnership() {
    var element = element(PopoutKind.WARNING, "*Bold* /italic/ _underlined_");
    var live = Component.literal("Warning").withStyle(ChatFormatting.RED);
    var content = element.content(List.of(live, Component.literal("Next")), text);
    assertEquals(
        List.of("Bold italic underlined", "", "Warning", "", "Next", ""),
        content.stream().map(Component::getString).toList());
    assertTrue(
        content.getFirst().getSiblings().stream()
            .anyMatch(c -> c.getString().equals("Bold") && c.getStyle().isBold()));
    assertEquals(live.getStyle(), content.get(2).getStyle());
    live.append(" changed");
    assertEquals("Warning", content.get(2).getString());
    assertThrows(UnsupportedOperationException.class, () -> content.add(Component.empty()));
    assertEquals(
        List.of("Heading", "", "Value"),
        element(PopoutKind.INFO, "Heading").content(Component.literal("Value"), text).stream()
            .map(Component::getString)
            .toList());
    assertEquals(
        "*literal*",
        element(PopoutKind.INFO, "\\*literal\\*").content(null, text).getFirst().getString());
  }

  private static class Custom extends PopoutElement {
    Custom() {
      super(
          Common.empty(),
          PopoutKind.INFO,
          Optional.empty(),
          Edge.LEFT,
          Optional.of(new Text("test", Optional.empty())));
    }

    public int colour() {
      return 0x699fc4;
    }

    public List<Component> content(Object value, Function<Text, Component> resolve) {
      return List.of(Component.literal("Load: " + value));
    }
  }

  @Test
  void subclassesOwnColourAndNonTextValuePresentationWithInheritedRendering() {
    var element = new Custom();
    assertEquals(0x699fc4, element.colour());
    assertEquals("Load: 7", element.content(7, text).getFirst().getString());
    var renderers = ElementRenderers.builtins();
    assertSame(renderers.require(PopoutElement.class), renderers.require(Custom.class));
    assertSame(
        renderers.require(ElementRenderers.Pass.FOREGROUND, PopoutElement.class),
        renderers.require(ElementRenderers.Pass.FOREGROUND, Custom.class));
  }
}
