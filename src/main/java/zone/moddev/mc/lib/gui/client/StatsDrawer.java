package zone.moddev.mc.lib.gui.client;

import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.model.Element;

/** Read-only statistics; unit selection and scrolling are entirely local. */
final class StatsDrawer {
  private final MachineScreen screen;
  boolean open;
  private int unit, scroll;
  private boolean handled;
  private Rect tab, body;
  private List<FormattedCharSequence> lines = List.of();
  private List<Component> cachedText = List.of();
  private int cachedWidth = -1;
  private net.minecraft.client.gui.Font cachedFont;
  private net.minecraft.locale.Language cachedLanguage;
  private static final int COLOUR = 0xa7cdd6;
  private static final float SCALE = .75f;

  StatsDrawer(MachineScreen screen) {
    this.screen = screen;
  }

  private Component text(String key) {
    return Component.translatable("screen.mmdlib.stats." + key);
  }

  void layout() {
    var panel = screen.panelBounds();
    int y = panel.y() + AutomationLayout.TOP;
    for (var e : screen.getMenu().bound.screen().tabs())
      if (e instanceof Element.Popout p && p.edge() == Element.Edge.LEFT)
        y = Math.max(y, screen.bounds(e).bottom() + 2);
    tab =
        new Rect(
            panel.x() - AutomationLayout.RAIL,
            y,
            AutomationLayout.RAIL,
            AutomationLayout.TAB_HEIGHT);
    if (!open) return;
    int width = Math.max(0, Math.min(160, tab.x() - 4));
    var font = Minecraft.getInstance().font;
    var text = screen.getMenu().stats(unit);
    var language = net.minecraft.locale.Language.getInstance();
    if (!text.equals(cachedText)
        || width != cachedWidth
        || font != cachedFont
        || language != cachedLanguage) {
      cachedText = text;
      cachedWidth = width;
      cachedFont = font;
      cachedLanguage = language;
      lines =
          text.stream()
              .flatMap(c -> font.split(c, Math.max(1, (int) ((width - 12) / SCALE))).stream())
              .toList();
    }
    body =
        SideDrawerLayout.body(
            panel,
            screen.drawerBottom(),
            tab.x() - width,
            panel.y() + AutomationLayout.TOP,
            width,
            28 + lines.size() * 9,
            SideDrawerLayout.HeightLimit.SCREEN);
    scroll = Math.clamp(scroll, 0, Math.max(0, lines.size() * 9 - Math.max(0, body.height() - 28)));
  }

  boolean show() {
    var menu = screen.getMenu();
    if (menu.containerDrawer.pending() || menu.curiosDrawer.pending()) return false;
    screen.openStatsDrawer();
    open = true;
    return true;
  }

  void render(GuiGraphics g, int mx, int my) {
    layout();
    if (open) SideDrawerRenderer.open(g, tab, body, Element.Edge.LEFT, COLOUR);
    else SideDrawerRenderer.tab(g, tab, COLOUR);
    // Small bar-chart glyph, drawn at native GUI resolution.
    for (int i = 0; i < 3; i++)
      g.fill(
          tab.x() + 5 + i * 5, tab.y() + 14 - i * 4, tab.x() + 8 + i * 5, tab.y() + 19, 0xff30545e);
    if (!open || body.width() < 12 || body.height() < 12) return;
    var font = Minecraft.getInstance().font;
    g.enableScissor(body.x() + 5, body.y() + 4, body.right() - 5, body.bottom() - 4);
    var r = unitBounds();
    g.enableScissor(body.x() + 5, body.y() + 4, Math.max(body.x() + 5, r.x() - 4), body.y() + 19);
    g.drawString(font, text("title"), body.x() + 6, body.y() + 6, 0xff263e45, false);
    g.disableScissor();
    boolean hover = r.contains(mx, my);
    g.fill(r.x(), r.y(), r.right(), r.bottom(), 0xff486e78);
    g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1, hover ? 0xffc2e3e9 : 0xffa7cdd6);
    g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.y() + 2, 0xffd6edf1);
    var label = text(new String[] {"per_tick", "per_second", "per_minute"}[unit]);
    g.pose().pushPose();
    g.pose()
        .translate(
            r.x() + Math.max(3, (r.width() - 10 - font.width(label) * SCALE) / 2), r.y() + 4, 0);
    g.pose().scale(SCALE, SCALE, 1);
    g.drawString(font, label, 0, 0, 0xff263e45, false);
    g.pose().popPose();
    int arrowX = r.right() - 6, arrowY = r.y() + 6;
    g.fill(arrowX - 1, arrowY - 2, arrowX, arrowY + 3, 0xff41616b);
    g.fill(arrowX, arrowY - 1, arrowX + 1, arrowY + 2, 0xff41616b);
    g.fill(arrowX + 1, arrowY, arrowX + 2, arrowY + 1, 0xff41616b);
    g.enableScissor(
        body.x() + 5,
        Math.min(body.bottom() - 4, body.y() + 24),
        body.right() - 5,
        body.bottom() - 4);
    g.pose().pushPose();
    g.pose().translate(body.x() + 6, body.y() + 24 - scroll, 0);
    g.pose().scale(SCALE, SCALE, 1);
    for (int i = 0; i < lines.size(); i++)
      g.drawString(font, lines.get(i), 0, i * 12, 0xff263e45, false);
    g.pose().popPose();
    g.disableScissor();
    g.disableScissor();
  }

  private Rect unitBounds() {
    int width = Math.min(64, Math.max(0, body.width() - 12));
    return new Rect(body.right() - 6 - width, body.y() + 4, width, 14);
  }

  boolean tabContains(double x, double y) {
    layout();
    return tab.contains(x, y);
  }

  void tooltip(GuiGraphics g, int x, int y) {
    if (tab.contains(x, y) || open && unitBounds().contains(x, y))
      g.renderComponentTooltip(
          Minecraft.getInstance().font,
          List.of(text("title"), text("cycle_tip"), text("tip")),
          x,
          y);
  }

  boolean contains(double x, double y) {
    layout();
    return tab.contains(x, y)
        || open
            && (body.contains(x, y)
                || SideDrawerLayout.rail(tab, body).contains(x, y) && !otherTab(x, y));
  }

  private boolean otherTab(double x, double y) {
    return screen.onOtherLeftDrawerTab(x, y);
  }

  boolean click(double x, double y, int button) {
    if (!contains(x, y)) {
      if ((button == 0 || button == 1) && otherTab(x, y)) open = false;
      return false;
    }
    handled = true;
    if (button != 0 && button != 1) return true;
    if (tab.contains(x, y)) {
      if (open) open = false;
      else show();
      screen.rememberStatsIntent();
    } else if (open && unitBounds().contains(x, y)) {
      unit = Math.floorMod(unit + (button == 0 ? 1 : -1), 3);
      scroll = 0;
    }
    return true;
  }

  boolean release() {
    boolean result = handled;
    handled = false;
    return result;
  }

  boolean drag() {
    return handled;
  }

  boolean key(int key) {
    if (key != 256 || !open) return false;
    open = false;
    screen.rememberStatsIntent();
    return true;
  }

  boolean scroll(double x, double y, double amount) {
    if (!open || !contains(x, y)) return false;
    scroll += amount < 0 ? 18 : -18;
    return true;
  }

  List<Rect> exclusions() {
    layout();
    return open ? List.of(tab, body, SideDrawerLayout.rail(tab, body)) : List.of(tab);
  }
}
