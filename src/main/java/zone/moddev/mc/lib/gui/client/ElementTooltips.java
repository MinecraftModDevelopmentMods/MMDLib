package zone.moddev.mc.lib.gui.client;

import java.util.*;
import java.util.function.Function;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import zone.moddev.mc.lib.gui.model.*;

/** Shared leaf-to-root composition for screens, native item tooltips, JEI and fragments. */
public final class ElementTooltips {
  private ElementTooltips() {}

  public static List<Component> collect(
      Element target,
      ElementRenderAccess access,
      Function<Element, ElementBehavior> behaviors,
      int x,
      int y,
      boolean shift,
      boolean control,
      boolean alt) {
    if (!access.visible(target)) return List.of();
    var rows = new ArrayList<Component>();
    var visited = Collections.newSetFromMap(new IdentityHashMap<Element, Boolean>());
    Element current = target, child = null;
    while (current != null && access.visible(current)) {
      if (!visited.add(current)) throw new IllegalArgumentException("Cyclic tooltip parent chain");
      var context =
          new Context(
              target, current, Optional.ofNullable(child), x, y, shift, control, alt, access);
      var behavior = behaviors.apply(current);
      if (child == null) {
        current.appendTooltip(context, rows);
        behavior.appendTooltip(context, rows);
      } else {
        current.appendChildTooltip(context, rows);
        behavior.appendChildTooltip(context, rows);
      }
      child = current;
      current = access.parent(current).orElse(null);
    }
    return rows.stream().map(Component::copy).map(Component.class::cast).toList();
  }

  private record Context(
      Element target,
      Element element,
      Optional<Element> child,
      int mouseX,
      int mouseY,
      boolean shiftDown,
      boolean controlDown,
      boolean altDown,
      ElementRenderAccess access)
      implements TooltipContext {
    public Object value(Element e, boolean maximum) {
      return access.value(e, maximum);
    }

    public Component text(Text value) {
      return access.text(value);
    }

    public List<Component> serverLines() {
      return access.serverTooltip(element);
    }

    public void appendDefaults(List<Component> rows) {
      rows.addAll(serverLines());
      var common = element.common();
      var authored =
          shiftDown ? common.tooltipShift().or(element::tooltipText) : element.tooltipText();
      authored.ifPresent(
          t -> {
            var arguments = new LinkedHashMap<String, String>();
            arguments.put(
                "value",
                access
                    .capability(element, ValueLabelProvider.class)
                    .flatMap(ValueLabelProvider::valueLabel)
                    .map(label -> text(label).getString())
                    .orElseGet(() -> element.displayText(value(), shiftDown).getString()));
            arguments.put("max", element.displayText(maximum(), shiftDown).getString());
            element
                .tooltipArguments(value(), maximum())
                .forEach((key, v) -> arguments.put(key, text(v).getString()));
            appendLines(text(t), arguments, rows);
          });
      if (common.tooltipShift().isPresent() && !shiftDown)
        rows.add(Component.translatable("screen.mmdlib.shift_for_more"));
      element.valueTooltip(value(), maximum()).forEach(line -> appendLines(line, Map.of(), rows));
    }
  }

  /** Split translated rows while retaining each component span's style. */
  private static void appendLines(
      Component component, Map<String, String> arguments, List<Component> rows) {
    var parts = new ArrayList<net.minecraft.network.chat.MutableComponent>();
    parts.add(Component.empty());
    component.visit(
        (style, span) -> {
          for (var entry : arguments.entrySet())
            span = span.replace("{" + entry.getKey() + "}", entry.getValue());
          var lines = span.split("\n", -1);
          for (int i = 0; i < lines.length; i++) {
            if (i > 0) parts.add(Component.empty());
            parts.getLast().append(Component.literal(lines[i]).withStyle(style));
          }
          return Optional.empty();
        },
        Style.EMPTY);
    if (parts.size() > 1 && parts.getLast().getString().isEmpty()) parts.removeLast();
    rows.addAll(parts);
  }
}
