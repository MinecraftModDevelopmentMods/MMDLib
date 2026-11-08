package zone.moddev.mc.lib.machine.recipe.generation;

import java.util.*;

import zone.moddev.mc.lib.gui.load.ScreenException;

/** Immutable XML attributes with source-aware typed access. The class attribute is excluded. */
public record GeneratorProperties(Map<String, String> values, String source, int line) {
  public GeneratorProperties {
    values = Map.copyOf(values);
  }

  public void requireOnly(String... names) {
    var allowed = Set.of(names);
    for (String name : values.keySet())
      if (!allowed.contains(name)) throw error("Unknown generator property " + name);
  }

  /** Removes subclass-specific options before passing the remaining options to a generator. */
  public GeneratorProperties without(String... names) {
    var remaining = new LinkedHashMap<>(values);
    for (var name : names) remaining.remove(name);
    return new GeneratorProperties(remaining, source, line);
  }

  public String requiredString(String name) {
    var value = values.get(name);
    if (value == null || value.isBlank()) throw error("Missing generator property " + name);
    return value;
  }

  public String string(String name, String fallback) {
    return values.containsKey(name) ? requiredString(name) : fallback;
  }

  public int requiredInt(String name, int min, int max) {
    return (int) requiredLong(name, min, max);
  }

  public int integer(String name, int fallback, int min, int max) {
    return values.containsKey(name) ? requiredInt(name, min, max) : fallback;
  }

  public long longValue(String name, long fallback, long min, long max) {
    return values.containsKey(name) ? requiredLong(name, min, max) : fallback;
  }

  public double decimal(String name, double fallback, double min, double max) {
    if (!values.containsKey(name)) return fallback;
    try {
      double value = Double.parseDouble(requiredString(name));
      if (Double.isFinite(value) && value >= min && value <= max) return value;
    } catch (NumberFormatException ex) {
      /* source-aware error below */
    }
    throw error(name + " must be finite and in " + min + ".." + max);
  }

  public long requiredLong(String name, long min, long max) {
    try {
      long value = Long.parseLong(requiredString(name));
      if (value >= min && value <= max) return value;
    } catch (NumberFormatException ex) {
      /* error below */
    }
    throw error(name + " must be in " + min + ".." + max);
  }

  public IllegalArgumentException error(String message) {
    return new ScreenException(source, line, message);
  }
}
