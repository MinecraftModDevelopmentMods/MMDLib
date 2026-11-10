package zone.moddev.mc.lib.gui.runtime;

import java.util.List;
import java.util.Objects;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** A synchronized display page; no statistic implementations or sampling state cross the wire. */
public record StatsPage(ResourceLocation id, Component label, List<Component> lines) {
  public static final int MAX_ROWS = 4096;

  public StatsPage {
    Objects.requireNonNull(id);
    label = Objects.requireNonNull(label).copy();
    if (lines.size() > MAX_ROWS) throw new IllegalArgumentException("Too many statistics rows");
    lines = lines.stream().map(row -> (Component) row.copy()).toList();
  }

  public Component label() {
    return label.copy();
  }

  public List<Component> lines() {
    return lines.stream().map(row -> (Component) row.copy()).toList();
  }
}
