package zone.moddev.mc.lib.gui.model;

import java.math.BigInteger;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/** Current processor activity shown by a generic button, independently of its click action. */
public enum ProcessorButtonState {
  PAUSED("pause", "screen.mmdlib.processor.paused", "unpause"),
  PROCESSING("stop", "screen.mmdlib.processor.processing", "stop"),
  IDLE("play", "screen.mmdlib.processor.idle", "pause"),
  STALLED("stop", "screen.mmdlib.machine.stalled", "stop"),
  BLOCKED("stop", "screen.mmdlib.machine.blocked", "stop"),
  HELD("stop", "screen.mmdlib.machine.held", "stop");

  private final ResourceLocation icon;
  private final String label, action;

  ProcessorButtonState(String icon, String label, String action) {
    this.icon = ResourceLocation.parse("mmdlib:icon_processor_" + icon);
    this.label = label;
    this.action = "screen.mmdlib.processor.action." + action;
  }

  public ResourceLocation icon() {
    return icon;
  }

  public String label() {
    return label;
  }

  public String action() {
    return action;
  }

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
