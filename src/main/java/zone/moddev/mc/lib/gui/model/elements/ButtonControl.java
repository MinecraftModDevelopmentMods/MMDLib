package zone.moddev.mc.lib.gui.model.elements;

import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Text;

/** Shared button presentation; implementations own the meaning of their actions and values. */
public interface ButtonControl extends Element {
  boolean flat();

  Optional<Text> text();

  Optional<ResourceLocation> iconFor(Object value);

  Optional<Text> labelFor(Object value);

  default Optional<ResourceLocation> badge(Object value, Object maximum) {
    return Optional.empty();
  }

  default int[] measure(TextMeasure measure) {
    return flat()
        ? new int[] {14, 14}
        : text().isPresent()
            ? new int[] {Math.max(20, measure.width(text().get()) + 8), 20}
            : new int[] {20, 20};
  }
}
