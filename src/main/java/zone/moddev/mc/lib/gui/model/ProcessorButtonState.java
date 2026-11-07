package zone.moddev.mc.lib.gui.model;

import java.math.BigInteger;
import java.util.Optional;

/** Current processor activity shown by a generic button, independently of its click action. */
public enum ProcessorButtonState {
  PAUSED,
  PROCESSING,
  IDLE;

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
