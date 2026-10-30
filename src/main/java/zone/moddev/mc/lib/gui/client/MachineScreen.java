package zone.moddev.mc.lib.gui.client;

import java.util.*;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.model.*;
import zone.moddev.mc.lib.gui.runtime.*;

/** Client presentation of a data-defined machine; getters are never invoked here. */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
  public final ScreenLayout layout;
  private final Set<Element.Popout> open = Collections.newSetFromMap(new IdentityHashMap<>());
  private final Map<Element.Popout, Integer> scroll = new IdentityHashMap<>();
  private final Map<Element.SideConfig, Integer> groups = new IdentityHashMap<>();
  private static final Map<ResourceLocation, Map<String, String>> REMEMBERED = new HashMap<>();

  /** Optional integration entry point, installed only by JEI. */
  public static java.util.function.BiPredicate<MachineScreen, Element> recipes = (s, e) -> false;

  private Element hovered;
  private boolean restored;

  public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    layout =
        ScreenLayout.of(
            menu.bound.screen(),
            text -> net.minecraft.client.Minecraft.getInstance().font.width(text(text)));
    imageWidth = layout.panel().width();
    imageHeight = layout.panel().height();
  }

  @EventBusSubscriber(modid = "mmdlib", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
  public static final class Registration {
    @SubscribeEvent
    public static void register(RegisterMenuScreensEvent event) {
      ScreenRuntime.textMeasure =
          text ->
              net.minecraft.client.Minecraft.getInstance().font.width(ScreenText.component(text));
      event.register(ScreenRuntime.MENUS.get(), MachineScreen::new);
    }
  }

  @Override
  protected void init() {
    super.init();
    if (restored) return;
    restored = true;
    if (REMEMBERED.containsKey(menu.bound.screen().id())) {
      for (var entry : REMEMBERED.get(menu.bound.screen().id()).entrySet()) {
        var c = menu.bound.screen().find(entry.getKey()).orElse(null);
        if (c instanceof Element.Context context
            && !context.defaultView().common().id().orElse("").equals(entry.getValue())) {
          var button =
              menu.elements.stream()
                  .filter(
                      e ->
                          e instanceof Element.Button b
                              && b.shows()
                                  .filter(
                                      ref ->
                                          ref.context().equals(entry.getKey())
                                              && ref.view().equals(entry.getValue()))
                                  .isPresent())
                  .findFirst();
          button.ifPresent(
              e -> {
                send(e, 1, 0, 0);
                menu.navigate(entry.getKey(), entry.getValue());
              });
        }
      }
    }
  }

  public Component text(Text text) {
    return ScreenText.resolve(text, menu.bound.screen().source());
  }

  public Rect bounds(Element e) {
    return layout.bounds(e).offset(leftPos, topPos);
  }

  public int guiLeft() {
    return leftPos;
  }

  public int guiTop() {
    return topPos;
  }

  public boolean visible(Element e) {
    return menu.visible(e);
  }

  private void blit(GuiGraphics g, ResourceLocation icon, int x, int y, int width, int height) {
    com.mojang.blaze3d.systems.RenderSystem.enableBlend();
    com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
    g.blitSprite(icon, x, y, width, height);
    com.mojang.blaze3d.systems.RenderSystem.disableBlend();
  }

  private void sprite(GuiGraphics g, String id, Rect r) {
    blit(
        g,
        ResourceLocation.parse(id.contains(":") ? id : "mmdlib:" + id),
        r.x(),
        r.y(),
        r.width(),
        r.height());
  }

  private void sideTab(GuiGraphics g, Rect r, Element.Edge edge) {
    if (edge == Element.Edge.RIGHT) {
      sprite(g, "side_tab", r);
      return;
    }
    g.pose().pushPose();
    g.pose().translate(r.right(), r.y(), 0);
    g.pose().scale(-1, 1, 1);
    sprite(g, "side_tab", new Rect(0, 0, r.width(), r.height()));
    g.pose().popPose();
  }

  private void tinted(GuiGraphics g, String sprite, Rect r, int color) {
    g.setColor(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, 1);
    sprite(g, sprite, r);
    g.setColor(1, 1, 1, 1);
  }

  private void caption(GuiGraphics g, Component text, int x, int y) {
    g.drawString(font, text, x, y, 0xff404040, false);
  }

  private void caption(GuiGraphics g, Text text, Rect r) {
    caption(g, text(text), r.x(), r.y());
  }

  @Override
  protected void renderLabels(GuiGraphics g, int x, int y) {}

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
    sprite(g, "panel", layout.panel().offset(leftPos, topPos));
    caption(g, text(menu.bound.screen().title()), leftPos + 7, topPos + 6);
    for (var e : menu.elements) if (visible(e)) draw(g, e, bounds(e), mx, my);
    layout
        .inventory()
        .ifPresent(
            r -> {
              caption(
                  g,
                  Component.translatable("container.inventory"),
                  leftPos + r.x(),
                  topPos + r.y());
              for (int row = 0; row < 4; row++)
                for (int col = 0; col < 9; col++)
                  sprite(
                      g,
                      "slot",
                      new Rect(
                          leftPos + r.x() + col * 18,
                          topPos + r.y() + 11 + row * 18 + (row == 3 ? 4 : 0),
                          18,
                          18));
            });
  }

  private void draw(GuiGraphics g, Element e, Rect r, int mx, int my) {
    switch (e) {
      case Element.Group group -> {
        tinted(g, "group", r, group.tint().orElse(0xffffff));
        group
            .label()
            .ifPresent(
                t ->
                    caption(
                        g, t, new Rect(r.x() + group.padding(), r.y() + group.padding(), 0, 0)));
      }
      case Element.Slot slot -> {
        tinted(
            g,
            "slot",
            r,
            slot.tint().orElse(slot.kind() == Element.SlotKind.UPGRADE ? 0xf0cd82 : 0xffffff));
        if (menu.slots.get(slot.index()).getItem().isEmpty())
          slot.icon()
              .ifPresent(
                  icon ->
                      blit(
                          g,
                          icon,
                          r.x() + (r.width() - 16) / 2,
                          r.y() + (r.height() - 16) / 2,
                          16,
                          16));
      }
      case Element.Label label -> caption(g, label.text(), r);
      case Element.Button b -> {
        sprite(
            g,
            r.contains(mx, my) ? "minecraft:widget/button_highlighted" : "minecraft:widget/button",
            r);
        b.text()
            .ifPresent(
                t ->
                    g.drawCenteredString(
                        font, text(t), r.x() + r.width() / 2, r.y() + 6, 0xffffff));
        b.icon().ifPresent(icon -> blit(g, icon, r.x() + (r.width() - 16) / 2, r.y() + 2, 16, 16));
      }
      case Element.Checkbox c -> {
        sprite(
            g,
            Boolean.TRUE.equals(menu.value(e, false)) ? "checkbox_selected" : "checkbox",
            new Rect(r.x(), r.y(), 10, 10));
        caption(g, c.text(), new Rect(r.x() + 14, r.y() + 1, 0, 0));
      }
      case Element.Radio radio -> {
        var parent =
            menu.elements.stream()
                .filter(
                    p ->
                        p instanceof Element.RadioGroup group
                            && group.options().stream().anyMatch(option -> option == radio))
                .findFirst()
                .orElse(null);
        boolean selected =
            parent instanceof Element.RadioGroup group
                && java.math.BigInteger.valueOf(radioIndex(group, radio))
                    .equals(menu.value(parent, false));
        sprite(g, selected ? "radio_selected" : "radio", new Rect(r.x(), r.y(), 10, 10));
        caption(g, radio.text(), new Rect(r.x() + 14, r.y() + 1, 0, 0));
      }
      case Element.RadioGroup group -> group.label().ifPresent(t -> caption(g, t, r));
      case Element.View view ->
          view.title().ifPresent(t -> caption(g, t, new Rect(r.x() + 12, r.y(), 0, 0)));
      case Element.Context c -> {
        if (!menu.views()
            .get(c.common().id().orElseThrow())
            .equals(c.defaultView().common().id().orElseThrow()))
          caption(g, Component.literal("‹"), r.x(), r.y());
      }
      case Element.SideTab tab -> sideTab(g, r, tab.edge());
      case Element.Popout pop -> {
        sideTab(g, r, pop.edge());
        blit(g, pop.iconOrDefault(), r.x() + 3, r.y() + 3, 16, 16);
      }
      case Element.SideConfig config -> drawSides(g, config, r);
      case Element.Gauge gauge ->
          GaugeRenderer.draw(g, r, gauge, menu.value(e, false), menu.value(e, true));
      case Element.Meter meter ->
          meter(g, r, meter, ScreenText.ratio(menu.value(e, false), menu.value(e, true)));
      case Element.Progress progress -> {
        var icon = progress.icon().orElse(ResourceLocation.parse("mmdlib:progress_arrow"));
        if (progress.icon().isPresent()) g.setColor(.25f, .25f, .25f, 1);
        progressSprite(g, icon, r, progress);
        g.setColor(1, 1, 1, 1);
        double ratio = ScreenText.ratio(menu.value(e, false), menu.value(e, true));
        int w = (int) Math.round(r.width() * ratio), h = (int) Math.round(r.height() * ratio);
        Rect fill =
            switch (progress.direction()) {
              case RIGHT -> new Rect(r.x(), r.y(), w, r.height());
              case LEFT -> new Rect(r.right() - w, r.y(), w, r.height());
              case UP -> new Rect(r.x(), r.bottom() - h, r.width(), h);
              case DOWN -> new Rect(r.x(), r.y(), r.width(), h);
            };
        if (fill.width() > 0 && fill.height() > 0) {
          g.enableScissor(fill.x(), fill.y(), fill.right(), fill.bottom());
          progressSprite(
              g,
              progress.icon().orElse(ResourceLocation.parse("mmdlib:progress_arrow_filled")),
              r,
              progress);
          g.disableScissor();
        }
      }
      default -> {}
    }
  }

  private static int radioIndex(Element.RadioGroup group, Element.Radio radio) {
    for (int i = 0; i < group.options().size(); i++) if (group.options().get(i) == radio) return i;
    return -1;
  }

  private void progressSprite(
      GuiGraphics g, ResourceLocation icon, Rect r, Element.Progress progress) {
    if (progress.icon().isPresent()) {
      blit(g, icon, r.x(), r.y(), r.width(), r.height());
      return;
    }
    float angle =
        switch (progress.direction()) {
          case RIGHT -> 0;
          case LEFT -> (float) Math.PI;
          case UP -> (float) -Math.PI / 2;
          case DOWN -> (float) Math.PI / 2;
        };
    g.pose().pushPose();
    g.pose().translate(r.x() + r.width() / 2f, r.y() + r.height() / 2f, 0);
    g.pose().mulPose(new org.joml.Quaternionf().rotationZ(angle));
    blit(g, icon, -12, -8, 24, 16);
    g.pose().popPose();
  }

  private void meter(GuiGraphics g, Rect r, Element.Meter m, double ratio) {
    g.fill(r.x(), r.y(), r.right(), r.bottom(), m.gapColor().orElse(0xff000000));
    boolean vertical = m.orientation() == Element.Orientation.VERTICAL;
    int length = (vertical ? r.height() : r.width()) - 2;
    int count = Math.max(1, Math.round((float) (length - m.gap()) / (m.segment() + m.gap())));
    int space = length - m.gap() - count * m.gap(), pos = 1 + m.gap();
    for (int i = 0; i < count; i++) {
      int size = space / count + (i < space % count ? 1 : 0);
      int color = i < Math.round(count * ratio) ? m.color() : dark(m.color());
      if (vertical)
        g.fill(r.x() + 1, r.bottom() - pos - size, r.right() - 1, r.bottom() - pos, color);
      else g.fill(r.x() + pos, r.y() + 1, r.x() + pos + size, r.bottom() - 1, color);
      pos += size + m.gap();
    }
    sprite(g, "compact_frame", r);
  }

  private static int dark(int color) {
    return 0xff000000 | ((color & 0xfefefe) >> 1 & 0x7f7f7f);
  }

  private static final int[][] FACES = {{1, 1}, {2, 2}, {0, 1}, {2, 1}, {1, 0}, {1, 2}};

  private Rect face(Rect r, int face) {
    return new Rect(r.x() + FACES[face][0] * 22, r.y() + 20 + FACES[face][1] * 22, 20, 20);
  }

  private void drawSides(GuiGraphics g, Element.SideConfig config, Rect r) {
    int group = groups.getOrDefault(config, 0), index = menu.index(config);
    sprite(g, "minecraft:widget/button", new Rect(r.x(), r.y(), r.width(), 20));
    var target = menu.bound.screen().find(config.groups().get(group)).orElseThrow();
    Component label =
        target instanceof Element.Group g1
            ? g1.label()
                .map(this::text)
                .orElseGet(() -> Component.translatable("screen.mmdlib.side_config"))
            : target
                .common()
                .tooltip()
                .map(this::text)
                .orElseGet(() -> Component.translatable("screen.mmdlib.side_config"));
    g.drawCenteredString(
        font,
        font.plainSubstrByWidth(label.getString(), r.width() - 6),
        r.x() + r.width() / 2,
        r.y() + 6,
        0xffffff);
    int[] colors = {0x777777, 0x679fea, 0xef994e, 0xad6fea};
    for (int i = 0; i < 6; i++) {
      Object value = menu.wireValue(-1 - index * 65536 - group * 6 - i);
      int mode = value instanceof java.math.BigInteger n ? n.intValue() : 0;
      Rect f = face(r, i);
      tinted(g, "slot", f, colors[Math.clamp(mode, 0, 3)]);
      g.drawCenteredString(
          font,
          Component.translatable(
              "screen.mmdlib.face.short."
                  + zone.moddev.mc.lib.api.capabilities.RelativeFace.values()[i]
                      .getSerializedName()),
          f.x() + 10,
          f.y() + 6,
          0xffffff);
    }
    if (visible(target)) {
      Rect highlight = bounds(target);
      g.renderOutline(
          highlight.x(), highlight.y(), highlight.width(), highlight.height(), 0xfff0cf55);
    }
  }

  public List<Rect> exclusions() {
    var result = new ArrayList<Rect>();
    for (var e : menu.bound.screen().tabs()) {
      result.add(bounds(e));
      if (e instanceof Element.Popout p && open.contains(p)) result.add(popBounds(p));
    }
    return result;
  }

  private Rect popBounds(Element.Popout p) {
    Rect tab = bounds(p);
    int w = 150, h = Math.max(22, Math.min(topPos + imageHeight - tab.y(), 120));
    return new Rect(p.edge() == Element.Edge.LEFT ? tab.x() - w : tab.right(), tab.y(), w, h);
  }

  private List<net.minecraft.util.FormattedCharSequence> popLines(Element.Popout p) {
    Object value = menu.value(p, false);
    var result = new ArrayList<net.minecraft.util.FormattedCharSequence>();
    if (p.text().isPresent())
      result.addAll(font.split(ScreenText.markup(text(p.text().get()).getString()), 138));
    else if (value instanceof Component c) result.addAll(font.split(c, 138));
    else if (value instanceof List<?> list)
      for (var c : list)
        if (c instanceof Component text) {
          result.addAll(font.split(text, 138));
          result.add(net.minecraft.util.FormattedCharSequence.EMPTY);
        }
    return result;
  }

  @Override
  public void render(GuiGraphics g, int mx, int my, float partial) {
    super.render(g, mx, my, partial);
    Element next = null;
    for (var e : menu.elements) if (visible(e) && bounds(e).contains(mx, my)) next = e;
    if (next != hovered) {
      hover(hovered, false);
      hovered = next;
      hover(hovered, true);
    }
    if (hovered != null)
      hovered
          .common()
          .hoverShows()
          .flatMap(id -> menu.bound.screen().find(id))
          .ifPresent(e -> draw(g, e, bounds(e), mx, my));
    for (var p : open) {
      Rect r = popBounds(p);
      sprite(g, "panel", r);
      var lines = popLines(p);
      int max = Math.max(0, lines.size() * 10 - (r.height() - 12));
      int offset = Math.clamp(scroll.getOrDefault(p, 0), 0, max);
      scroll.put(p, offset);
      g.enableScissor(r.x() + 5, r.y() + 5, r.right() - 5, r.bottom() - 5);
      for (int i = 0; i < lines.size(); i++)
        g.drawString(font, lines.get(i), r.x() + 6, r.y() + 6 + i * 10 - offset, 0xff303030, false);
      g.disableScissor();
    }
    renderTooltip(g, mx, my);
    if (hovered != null) tooltip(g, hovered, mx, my);
  }

  private void hover(Element e, boolean enter) {
    if (e != null)
      e.common()
          .onHover()
          .ifPresent(
              handler ->
                  zone.moddev.mc.lib.api.MaterialExtensions.GLOBAL
                      .events()
                      .dispatch(
                          handler,
                          zone.moddev.mc.lib.api.MaterialEventTypes.GUI_HOVER,
                          new ScreenHover(menu, e, enter)));
  }

  private void tooltip(GuiGraphics g, Element e, int mx, int my) {
    var c = e.common();
    boolean shift = hasShiftDown();
    var text = shift ? c.tooltipShift().or(c::tooltip) : c.tooltip();
    var lines = new ArrayList<Component>();
    text.ifPresent(
        t -> {
          String translated =
              this.text(t)
                  .getString()
                  .replace("{value}", ScreenText.number(menu.value(e, false), shift))
                  .replace("{max}", ScreenText.number(menu.value(e, true), shift));
          lines.add(Component.literal(translated));
        });
    if (c.tooltipShift().isPresent() && !shift)
      lines.add(Component.translatable("screen.mmdlib.shift_for_more"));
    if (e instanceof Element.SideConfig config) {
      int group = groups.getOrDefault(config, 0);
      for (int i = 0; i < 6; i++)
        if (face(bounds(e), i).contains(mx, my)) {
          Object mode = menu.wireValue(-1 - menu.index(e) * 65536 - group * 6 - i);
          int n = mode instanceof java.math.BigInteger b ? Math.clamp(b.intValue(), 0, 3) : 0;
          lines.add(
              Component.translatable(
                  "screen.mmdlib.face."
                      + zone.moddev.mc.lib.api.capabilities.RelativeFace.values()[i]
                          .getSerializedName()));
          lines.add(
              Component.translatable(
                  "screen.mmdlib.mode."
                      + zone.moddev.mc.lib.api.capabilities.IoMode.values()[n]
                          .getSerializedName()));
        }
    }
    if (!lines.isEmpty()) g.renderComponentTooltip(font, lines, mx, my);
  }

  private void send(Element e, int kind, int button, int option) {
    int modifiers = (hasShiftDown() ? 1 : 0) | (hasControlDown() ? 2 : 0) | (hasAltDown() ? 4 : 0);
    minecraft.gameMode.handleInventoryButtonClick(
        menu.containerId,
        new ScreenNetwork.Action(menu.containerId, menu.index(e), kind, button, modifiers, option)
            .pack());
  }

  @Override
  public boolean mouseClicked(double mx, double my, int button) {
    if (button > 1) return super.mouseClicked(mx, my, button);
    for (var p : open) if (popBounds(p).contains(mx, my)) return true;
    for (int i = menu.elements.size() - 1; i >= 0; i--) {
      var e = menu.elements.get(i);
      Rect r = bounds(e);
      if (!visible(e) || !r.contains(mx, my)) continue;
      if (e instanceof Element.Popout p) {
        if (!open.remove(p)) open.add(p);
        return true;
      }
      if (e instanceof Element.Slot s && s.kind() == Element.SlotKind.GHOST) {
        PacketDistributor.sendToServer(
            new ScreenNetwork.Ghost(
                menu.containerId,
                i,
                button == 1 ? ItemStack.EMPTY : menu.getCarried().copyWithCount(1)));
        return true;
      }
      if (e instanceof Element.Button b && b.shows().isPresent()) {
        send(e, 1, button, 0);
        var ref = b.shows().get();
        var c = (Element.Context) menu.bound.screen().find(ref.context()).orElseThrow();
        menu.navigate(
            ref.context(),
            menu.views().get(ref.context()).equals(ref.view())
                ? c.defaultView().common().id().orElseThrow()
                : ref.view());
        remember();
        return true;
      }
      if (e instanceof Element.Context c
          && mx < r.x() + 12
          && my < r.y() + 12
          && !menu.views()
              .get(c.common().id().orElseThrow())
              .equals(c.defaultView().common().id().orElseThrow())) {
        back(c);
        return true;
      }
      if (e instanceof Element.SideConfig config) {
        int group = groups.getOrDefault(config, 0);
        if (my < r.y() + 20) {
          groups.put(config, (group + 1) % config.groups().size());
          return true;
        }
        for (int f = 0; f < 6; f++)
          if (face(r, f).contains(mx, my)) {
            send(e, 3, button, group * 6 + f);
            return true;
          }
      }
      if (!e.common().jeiRecipes().isEmpty() && recipes.test(this, e)) return true;
      if (e instanceof Element.Radio radio && !e.common().clicks().any()) {
        for (var parent : menu.elements)
          if (parent instanceof Element.RadioGroup group
              && group.options().stream().anyMatch(option -> option == radio)) {
            send(parent, 0, button, radioIndex(group, radio));
            return true;
          }
      }
      if (e.common().clicks().any()) {
        send(e, 0, button, 0);
        return true;
      }
    }
    return super.mouseClicked(mx, my, button);
  }

  private void remember() {
    REMEMBERED.put(menu.bound.screen().id(), menu.views());
  }

  private void back(Element.Context c) {
    send(c, 2, 0, 0);
    menu.navigate(c.common().id().orElseThrow(), c.defaultView().common().id().orElseThrow());
    remember();
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if (key == 256)
      for (var e : menu.elements)
        if (e instanceof Element.Context c
            && !menu.views()
                .get(c.common().id().orElseThrow())
                .equals(c.defaultView().common().id().orElseThrow())) {
          back(c);
          return true;
        }
    return super.keyPressed(key, scan, modifiers);
  }

  @Override
  public void removed() {
    hover(hovered, false);
    hovered = null;
    super.removed();
  }

  @Override
  public boolean mouseScrolled(double mx, double my, double dx, double dy) {
    for (var p : open)
      if (popBounds(p).contains(mx, my)) {
        scroll.merge(p, (int) (-dy * 10), Integer::sum);
        return true;
      }
    return super.mouseScrolled(mx, my, dx, dy);
  }
}
