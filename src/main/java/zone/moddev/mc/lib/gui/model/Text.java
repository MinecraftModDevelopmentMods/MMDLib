package zone.moddev.mc.lib.gui.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Text shown on a screen: always a translation key, with an optional literal fallback shown only
 * when the key is missing from the lang file (the attribute's {@code -fallback} partner). MMDLib
 * never supplies a fallback itself.
 *
 * @param key the translation key
 * @param fallback literal text for a missing key, if the author gave one
 */
public record Text(String key, Optional<String> fallback) {
  /** Checks that the key is present. */
  public Text {
    Objects.requireNonNull(key);
    Objects.requireNonNull(fallback);
  }
}
