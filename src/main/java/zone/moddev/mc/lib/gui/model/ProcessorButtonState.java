package zone.moddev.mc.lib.gui.model;

import java.math.BigInteger;
import java.util.Optional;

/** Current processor activity shown by a generic button, independently of its click action. */
public enum ProcessorButtonState {
  PAUSED,
  PROCESSING,
  IDLE,
  STALLED,
  BLOCKED,
  HELD;

  public static ProcessorButtonState of(
      zone.moddev.mc.lib.machine.feature.RecipeProcessor processor) {
    if (processor.paused() || processor.processing())
      return of(processor.paused(), processor.processing());
    return switch (processor.status()) {
      case RUNNING -> PROCESSING;
      case STALLED -> STALLED;
      case BLOCKED -> BLOCKED;
      case HELD -> HELD;
      default -> IDLE;
    };
  }

  public static ProcessorButtonState of(boolean paused, boolean processing) {
    return paused ? PAUSED : processing ? PROCESSING : IDLE;
  }

  public BigInteger wireValue() {
    return BigInteger.valueOf(ordinal());
  }

  public static Optional<ProcessorButtonState> fromWire(Object value) {
    for (var state : values()) if (state.wireValue().equals(value)) return Optional.of(state);
    return Optional.empty();
  }
}
