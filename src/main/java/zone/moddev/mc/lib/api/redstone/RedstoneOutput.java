package zone.moddev.mc.lib.api.redstone;

import java.util.*;
import java.util.function.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.api.automation.ResourceGroup;

/** Common-side metadata and a server signal supplier; contains no client rendering types. */
public record RedstoneOutput(
    String id,
    Component name,
    Component description,
    int colour,
    Optional<ResourceLocation> sprite,
    IntSupplier signal)
    implements IRedstoneOutput {
  public RedstoneOutput {
    if (id == null || !id.matches("[a-z0-9_.:/-]{1,128}"))
      throw new IllegalArgumentException("Invalid redstone output ID: " + id);
    if ((colour & 0xff000000) != 0) throw new IllegalArgumentException("Output colour must be RGB");
    name = Objects.requireNonNull(name).copy();
    description = Objects.requireNonNull(description).copy();
    sprite = Objects.requireNonNull(sprite);
    signal = Objects.requireNonNull(signal);
  }

  @Override
  public Component name() {
    return name.copy();
  }

  @Override
  public Component description() {
    return description.copy();
  }

  public int level() {
    return Math.clamp(signal.getAsInt(), 0, 15);
  }

  @Override
  public List<RedstoneOutput> redstoneOutputs() {
    return List.of(this);
  }

  /** Adapter preserving the resource group's ID, label and swatch. */
  public static RedstoneOutput resource(ResourceGroup<?> group, DoubleSupplier fullness) {
    return new RedstoneOutput(
        group.id(),
        group.name(),
        Component.translatable("screen.mmdlib.redstone.source.fullness", group.name()),
        group.colour(),
        Optional.empty(),
        () -> RedstoneControl.level(fullness.getAsDouble()));
  }
}
