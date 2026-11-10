package zone.moddev.mc.lib.gui.load;

import java.util.List;
import java.util.Objects;

/** An XML attribute contract. Types are XSD types (xs:int) or shared screen types (pixels). */
public record ScreenAttribute(
    String type, boolean required, String defaultValue, List<String> choices) {
  public ScreenAttribute {
    Objects.requireNonNull(type);
    choices = List.copyOf(choices);
    if (required && defaultValue != null)
      throw new IllegalArgumentException("A required attribute cannot have a default");
  }

  public static ScreenAttribute optional(String type) {
    return new ScreenAttribute(type, false, null, List.of());
  }

  public static ScreenAttribute required(String type) {
    return new ScreenAttribute(type, true, null, List.of());
  }

  public static ScreenAttribute defaulted(String type, String value) {
    return new ScreenAttribute(type, false, Objects.requireNonNull(value), List.of());
  }

  public static ScreenAttribute choices(String... values) {
    return new ScreenAttribute("xs:string", false, null, List.of(values));
  }

  public ScreenAttribute asRequired() {
    return new ScreenAttribute(type, true, null, choices);
  }

  public ScreenAttribute withDefault(String value) {
    return new ScreenAttribute(type, false, Objects.requireNonNull(value), choices);
  }
}
