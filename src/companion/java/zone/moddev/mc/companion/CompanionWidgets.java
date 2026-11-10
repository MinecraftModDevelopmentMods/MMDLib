package zone.moddev.mc.companion;

import java.util.Set;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.WidgetAction;
import zone.moddev.mc.lib.gui.WidgetType;
import zone.moddev.mc.lib.gui.WidgetTypes;
import zone.moddev.mc.lib.gui.value.ValueTypes;

/** A consuming mod owns the widget contract; no client class is referenced here. */
public final class CompanionWidgets {
  public static final ResourceLocation PROGRESS_DIAL = CompanionMod.id("progress_dial");
  public static final ResourceLocation NAME_EDITOR = CompanionMod.id("name_editor");
  public static final ResourceLocation RESERVE_SLIDER = CompanionMod.id("reserve_slider");
  public static final ResourceLocation LANE_SELECTOR = CompanionMod.id("lane_selector");

  public static final WidgetAction<String> RENAME =
      new WidgetAction<>(
          CompanionMod.id("rename"),
          net.minecraft.network.codec.StreamCodec.of(
              (b, value) -> b.writeUtf(value, 32), b -> b.readUtf(32)),
          132,
          (context, value) ->
              context.menu().blockEntity instanceof ProcessorEntity machine
                  && machine.rename(value));

  public static final WidgetAction<Integer> RESERVE =
      new WidgetAction<>(
          CompanionMod.id("reserve"),
          net.minecraft.network.codec.StreamCodec.of(
              (b, value) -> b.writeVarInt(value), b -> b.readVarInt()),
          5,
          (context, value) ->
              context.menu().blockEntity instanceof ProcessorEntity machine
                  && machine.setReservePercent(value));

  public record LaneSetting(int lane, boolean paused) {}

  public static final WidgetAction<LaneSetting> SET_LANE =
      new WidgetAction<>(
          CompanionMod.id("set_lane"),
          net.minecraft.network.codec.StreamCodec.of(
              (b, value) -> {
                b.writeVarInt(value.lane());
                b.writeBoolean(value.paused());
              },
              b -> new LaneSetting(b.readVarInt(), b.readBoolean())),
          6,
          (context, value) -> {
            if (!(context.menu().blockEntity instanceof ProcessorEntity machine)
                || value.lane() != Integer.parseInt(context.widget().properties().get("lane"))
                || value.lane() < 0
                || value.lane() >= machine.lanes.size()) return false;
            if (machine.lanes.get(value.lane()).paused() != value.paused())
              machine.toggle(value.lane());
            return true;
          });

  private CompanionWidgets() {}

  public static void register() {
    WidgetTypes.GLOBAL.register(
        NAME_EDITOR,
        new WidgetType(
            Set.of(ValueTypes.TEXT),
            widget -> validateEditor(widget, 140, 42, Set.of()),
            java.util.List.of(RENAME)));
    WidgetTypes.GLOBAL.register(
        RESERVE_SLIDER,
        new WidgetType(
            Set.of(ValueTypes.WHOLE),
            widget -> validateEditor(widget, 100, 30, Set.of()),
            java.util.List.of(RESERVE)));
    WidgetTypes.GLOBAL.register(
        LANE_SELECTOR,
        new WidgetType(
            Set.of(ValueTypes.BOOLEAN),
            widget -> {
              validateEditor(widget, 100, 30, Set.of("lane"));
              String lane = widget.properties().get("lane");
              if (!"0".equals(lane) && !"1".equals(lane))
                throw new IllegalArgumentException("lane must be 0 or 1");
            },
            java.util.List.of(SET_LANE)));
    WidgetTypes.GLOBAL.register(
        PROGRESS_DIAL,
        new WidgetType(
            Set.of(ValueTypes.WHOLE, ValueTypes.DECIMAL, ValueTypes.NUMBER),
            widget -> {
              if (widget.common().value().isEmpty() || widget.common().max().isEmpty())
                throw new IllegalArgumentException("A progress dial needs value and max getters");
              if (widget.width() < 16 || widget.height() < 16)
                throw new IllegalArgumentException("A progress dial needs at least 16 x 16 px");
              for (var name : widget.properties().keySet())
                if (!name.equals("color"))
                  throw new IllegalArgumentException("Unknown property: " + name);
              String color = widget.properties().getOrDefault("color", "#55CC88");
              if (!color.matches("#[0-9a-fA-F]{6}"))
                throw new IllegalArgumentException("color must be #RRGGBB");
            }));
  }

  private static void validateEditor(
      zone.moddev.mc.lib.gui.model.elements.CustomElement widget,
      int width,
      int height,
      Set<String> properties) {
    if (widget.common().value().isEmpty())
      throw new IllegalArgumentException("Editor needs a value getter");
    if (widget.width() < width || widget.height() < height)
      throw new IllegalArgumentException("Editor needs at least " + width + " x " + height + " px");
    if (!properties.containsAll(widget.properties().keySet()))
      throw new IllegalArgumentException("Unknown editor property");
  }
}
