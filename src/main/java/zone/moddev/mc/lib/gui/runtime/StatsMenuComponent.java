package zone.moddev.mc.lib.gui.runtime;

import java.util.*;

import net.minecraft.network.chat.Component;

import zone.moddev.mc.lib.gui.runtime.component.*;

/** Statistics sampling and display belong to this component, not the menu coordinator. */
public final class StatsMenuComponent implements MenuSnapshot {
  public static final ComponentSync<List<StatsPage>> PAGES =
      new ComponentSync<>(
          net.minecraft.resources.ResourceLocation.parse("mmdlib:statistics"),
          2,
          net.minecraft.network.codec.StreamCodec.of(
              (buffer, pages) -> {
                if (!validPages(pages))
                  throw new IllegalArgumentException("Invalid statistics pages");
                buffer.writeVarInt(pages.size());
                for (var page : pages) {
                  buffer.writeResourceLocation(page.id());
                  net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(
                      buffer, page.label());
                  var lines = page.lines();
                  buffer.writeVarInt(lines.size());
                  for (var line : lines)
                    net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(
                        buffer, line);
                }
              },
              buffer -> {
                int count = buffer.readVarInt();
                if (count < 0 || count > zone.moddev.mc.lib.api.stats.StatsTimescale.MAX_TIMESCALES)
                  throw new IllegalArgumentException("Too many statistics pages");
                var pages = new ArrayList<StatsPage>();
                for (int i = 0; i < count; i++) {
                  var id = buffer.readResourceLocation();
                  var label =
                      net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(buffer);
                  int rows = buffer.readVarInt();
                  if (rows < 0 || rows > StatsPage.MAX_ROWS)
                    throw new IllegalArgumentException("Too many statistics rows");
                  var lines = new ArrayList<Component>();
                  for (int row = 0; row < rows; row++)
                    lines.add(
                        net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(
                            buffer));
                  pages.add(new StatsPage(id, label, lines));
                }
                return List.copyOf(pages);
              }),
          65536,
          List::of,
          StatsMenuComponent::validPages);

  private static boolean validPages(List<StatsPage> pages) {
    return pages.size() <= zone.moddev.mc.lib.api.stats.StatsTimescale.MAX_TIMESCALES
        && pages.stream().allMatch(Objects::nonNull)
        && pages.stream().map(StatsPage::id).distinct().count() == pages.size();
  }

  private final MenuComponentRegistry.Context context;
  private final ScreenStats sampler;

  public StatsMenuComponent(MenuComponentRegistry.Context context) {
    this.context = context;
    sampler =
        context.client() || context.host() == null
            ? new ScreenStats()
            : ScreenStats.forHost(context.host());
  }

  public void snapshot(ComponentSnapshot values) {
    values.put(
        PAGES, sampler.read(context.host(), context.menu().blockEntity.getLevel().getGameTime()));
  }

  public List<StatsPage> pages() {
    return context.menu().components().state(this, PAGES);
  }
}
