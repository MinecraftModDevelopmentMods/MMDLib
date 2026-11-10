package zone.moddev.mc.lib.gui.model.elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.gui.ElementAction;
import zone.moddev.mc.lib.gui.ElementActions;
import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.sync.*;

/**
 * {@code side-config}: six-face IO configuration, one resource group at a time.
 *
 * <p>{@code groups}: the IDs of the elements showing each group, in the order offered
 */
public class SideConfigElement implements SynchronizedElement {
  private final Common common;
  private final List<String> groups;

  public static final ElementSync<List<Integer>> MODES =
      ElementSync.field(
              ResourceLocation.parse("mmdlib:side_modes"),
              StreamCodec.<net.minecraft.network.RegistryFriendlyByteBuf, List<Integer>>of(
                  (buffer, modes) -> {
                    buffer.writeVarInt(modes.size());
                    for (var mode : modes) buffer.writeByte(mode);
                  },
                  buffer -> {
                    int count = buffer.readVarInt();
                    if (count < 0 || count > 4092)
                      throw new IllegalArgumentException("Too many side modes");
                    var modes = new ArrayList<Integer>(count);
                    for (int i = 0; i < count; i++) modes.add((int) buffer.readUnsignedByte());
                    return List.copyOf(modes);
                  }),
              4096,
              element -> Collections.nCopies(((SideConfigElement) element).groups().size() * 6, 0))
          .validate(
              (element, modes) ->
                  modes.size() == ((SideConfigElement) element).groups().size() * 6
                      && modes.stream().allMatch(mode -> mode >= 0 && mode <= 3))
          .sample(
              (source, previous) -> {
                var config = (SideConfigElement) source.element();
                var modes = new ArrayList<Integer>();
                var sides =
                    source.machine() instanceof ScreenHost host
                        ? host.screenSides()
                        : java.util.Map
                            .<String, zone.moddev.mc.lib.api.capabilities.SideConfig>of();
                for (var group : config.groups())
                  for (var face : RelativeFace.values())
                    modes.add(sides.get(group) == null ? 0 : sides.get(group).get(face).ordinal());
                return List.copyOf(modes);
              })
          .build();

  public List<ElementSync<?>> synchronizedValues() {
    return List.of(MODES);
  }

  /** Copies the groups. */
  public SideConfigElement(Common common, List<String> groups) {
    groups = List.copyOf(groups);

    this.common = common;
    this.groups = groups;
  }

  public int[] measure(TextMeasure text) {
    return new int[] {64, 88};
  }

  /** Shared face geometry for drawing, tooltip hit tests and clicks. */
  public Rect face(Rect bounds, int face) {
    int[][] positions = {{1, 1}, {2, 2}, {0, 1}, {2, 1}, {1, 0}, {1, 2}};
    return new Rect(
        bounds.x() + positions[face][0] * 22, bounds.y() + 20 + positions[face][1] * 22, 20, 20);
  }

  public void validate() {
    SynchronizedElement.super.validate();
    if (groups.isEmpty()) throw new IllegalArgumentException("side-config needs groups");
    if (groups.size() > 682) throw new IllegalArgumentException("Too many side-config groups");
  }

  public void validateScreen(Screen screen) {
    for (var group : groups)
      if (screen.find(group).isEmpty())
        throw new IllegalArgumentException("side-config group names no element: " + group);
  }

  public static final ElementAction<ElementActions.Click> CYCLE_FACE =
      new ElementAction<>(
          ResourceLocation.parse("mmdlib:side_face"),
          ElementActions.Click.CODEC,
          8,
          (context, click) -> {
            var element = (SideConfigElement) context.element();
            int group = click.option() / 6, face = click.option() % 6;
            if (group >= element.groups().size()
                || !(context.menu().blockEntity
                    instanceof zone.moddev.mc.lib.gui.runtime.ScreenHost host)) return false;
            var sides = host.screenSides().get(element.groups().get(group));
            if (sides == null) return false;
            sides.cycle(zone.moddev.mc.lib.api.capabilities.RelativeFace.values()[face]);
            return true;
          });

  public List<? extends ElementAction<?>> actions() {
    return List.of(ElementActions.CLICK, CYCLE_FACE);
  }

  public Optional<ElementAction<ElementActions.Click>> legacyAction(int kind) {
    return kind == 3 ? Optional.of(CYCLE_FACE) : SynchronizedElement.super.legacyAction(kind);
  }

  public Common common() {
    return common;
  }

  public List<String> groups() {
    return groups;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;
    var other = (SideConfigElement) object;
    return java.util.Objects.equals(common, other.common)
        && java.util.Objects.equals(groups, other.groups);
  }

  @Override
  public int hashCode() {
    int hash = 0;
    hash = 31 * hash + java.util.Objects.hashCode(common);
    hash = 31 * hash + java.util.Objects.hashCode(groups);
    return hash;
  }

  @Override
  public String toString() {
    return "SideConfigElement[common=" + common + ", groups=" + groups + "]";
  }
}
