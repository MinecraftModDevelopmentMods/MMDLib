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

import zone.moddev.mc.lib.api.automation.AutomationInventory;
import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.model.*;
import zone.moddev.mc.lib.gui.runtime.*;

/** Client presentation of a data-defined machine; getters are never invoked here. */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
  public ScreenLayout layout;
  private final AutomationDrawer automation;
  private final RedstoneDrawer redstone;
  final StatsDrawer stats = new StatsDrawer(this);
  private final ReservationOverlay reservations = new ReservationOverlay();
  private final ContainerDrawer containerDrawer;
  private final ContainerDrawer curiosDrawer;
  private WidgetInput widgetInput;
  private boolean widgetsRemoved;
  private final Set<Element.Popout> open = Collections.newSetFromMap(new IdentityHashMap<>());
  private final Map<Element.Popout, Integer> scroll = new IdentityHashMap<>();
  private final Map<Element.SideConfig, Integer> groups = new IdentityHashMap<>();
  private static final Map<ResourceLocation, Map<String, String>> REMEMBERED = new HashMap<>();

  /** Optional integration entry point, installed only by JEI. */
  public static java.util.function.BiPredicate<MachineScreen, Element> recipes = (s, e) -> false;

  /** Optional overlays can reserve space for a bottom toolbar without moving the main panel. */
  public static java.util.function.IntSupplier drawerBottomInset = () -> 4;

  public int drawerBottom() {
    return Math.max(0, height - Math.max(4, drawerBottomInset.getAsInt()));
  }

  /**
   * Installed only by JEI: whether JEI draws the tooltip of an element's recipe area, in which case
   * the screen leaves it to JEI (which asks for {@link #tooltipLines}) instead of drawing its own.
   */
  public static java.util.function.BiPredicate<MachineScreen, Element> recipeTooltip =
      (s, e) -> false;

  private Element hovered;
  private boolean restored;
  private final DrawerMemory drawerMemory = new DrawerMemory();

  public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    WidgetRenderers.GLOBAL.validate(menu.bound.screen());
    layout = menu.layout();
    imageWidth = layout.panel().width();
    imageHeight = layout.panel().height();
    widgetInput = createWidgets();
    automation = menu.automation == null ? null : new AutomationDrawer(this);
    redstone = menu.redstone == null ? null : new RedstoneDrawer(this);
    containerDrawer = new ContainerDrawer(this);
    curiosDrawer = new ContainerDrawer(this, true);
  }

  private WidgetInput createWidgets() {
    return new WidgetInput(
        menu.elements.stream()
            .filter(Element.Custom.class::isInstance)
            .map(Element.Custom.class::cast)
            .toList(),
        widget ->
            WidgetRenderers.GLOBAL
                .require(widget.type())
                .create(
                    new WidgetAccess() {
                      public Element.Custom element() {
                        return widget;
                      }

                      public Rect bounds() {
                        return MachineScreen.this.bounds(widget);
                      }

                      public Object value() {
                        return menu.value(widget, false);
                      }

                      public Object maximum() {
                        return menu.value(widget, true);
                      }

                      public boolean pending() {
                        return menu.widgetPending(widget);
                      }

                      public <T> boolean submit(
                          zone.moddev.mc.lib.gui.WidgetAction<T> action, T value) {
                        var packet = menu.prepareWidgetAction(widget, action, value);
                        packet.ifPresent(PacketDistributor::sendToServer);
                        return packet.isPresent();
                      }
                    }),
        this::visible,
        this::bounds);
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

  @EventBusSubscriber(modid = "mmdlib", value = Dist.CLIENT)
  public static final class PendingActions {
    @SubscribeEvent
    public static void logout(
        net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
      DrawerMemory.disconnected();
    }

    @SubscribeEvent
    public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
      var player = net.minecraft.client.Minecraft.getInstance().player;
      if (player != null && player.containerMenu instanceof MachineMenu menu) {
        menu.tickWidgetActions();
        menu.containerDrawer.tickClient();
        menu.curiosDrawer.tickClient();
        if (menu.automation != null) menu.automation.tick();
        if (menu.redstone != null) menu.redstone.tick();
      }
    }
  }

  @Override
  protected void init() {
    super.init();
    containerDrawer.cancelGesture();
    curiosDrawer.cancelGesture();
    if (automation != null) automation.closeEditorInput();
    // Recipe browsers can return to the same screen object after removed() was called.
    if (widgetsRemoved) {
      widgetInput = createWidgets();
      widgetsRemoved = false;
    }
    widgetInput.resized();
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
    updateLayout();
    return layout.bounds(e).offset(leftPos, topPos);
  }

  public Rect panelBounds() {
    updateLayout();
    return layout.panel().offset(leftPos, topPos);
  }

  private void updateLayout() {
    if (layout == menu.layout()) return;
    layout = menu.layout();
    imageWidth = layout.panel().width();
    imageHeight = layout.panel().height();
    leftPos = (width - imageWidth) / 2;
    topPos = (height - imageHeight) / 2;
    hoveredSlot = null;
    quickCraftSlots.clear();
    isQuickCrafting = false;
    widgetInput.resized();
    containerDrawer.cancelGesture();
    curiosDrawer.cancelGesture();
    if (automation != null) automation.closeEditorInput();
  }

  void rememberContainerIntent(
      boolean equipment, ContainerDrawerNetwork.State state, int kind, int argument) {
    drawerMemory.containerIntent(equipment, state, kind, argument);
  }

  void rememberAutomationIntent(String kind, int group) {
    drawerMemory.automationIntent(kind, menu.automation.inventory.group(group).id());
  }

  void rememberRedstoneIntent(int kind, int face) {
    drawerMemory.redstoneIntent(menu.redstone.open(), kind, face);
  }

  void rememberStatsIntent() {
    drawerMemory.statsIntent(stats.open);
  }

  void closeRightPopouts() {
    open.removeIf(p -> p.edge() == Element.Edge.RIGHT);
  }

  void closeLeftDrawers() {
    stats.open = false;
    open.removeIf(p -> p.edge() == Element.Edge.LEFT);
  }

  void openStatsDrawer() {
    closeLeftDrawers();
    containerDrawer.close();
    curiosDrawer.close();
  }

  boolean onOtherLeftDrawerTab(double x, double y) {
    return containerDrawer.tabContains(x, y)
        || curiosDrawer.tabContains(x, y)
        || menu.bound.screen().tabs().stream()
            .anyMatch(
                e ->
                    e instanceof Element.Popout p
                        && p.edge() == Element.Edge.LEFT
                        && bounds(e).contains(x, y));
  }

  boolean onDrawerTab(double x, double y) {
    return stats.tabContains(x, y)
        || containerDrawer.tabContains(x, y)
        || curiosDrawer.tabContains(x, y)
        || menu.bound.screen().tabs().stream().anyMatch(e -> bounds(e).contains(x, y));
  }

  private void positionAutomation() {
    updateLayout();
    if (automation == null) return;
    if (menu.automation.open() && !menu.automation.pending())
      open.removeIf(p -> p.edge() == Element.Edge.RIGHT);
    automation.layout();
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
    for (var e : menu.elements) if (visible(e)) draw(g, e, bounds(e), mx, my, partial);
    layout
        .inventory()
        .ifPresent(
            r -> {
              for (int index = 0; index <= 40; index++)
                sprite(g, "slot", ScreenLayout.playerSlot(r, index).offset(leftPos, topPos));
            });
    if (automation != null) automation.drawBackground(g, mx, my);
    if (redstone != null) redstone.render(g, mx, my);
    containerDrawer.render(g, mx, my);
    curiosDrawer.render(g, mx, my);
    stats.render(g, mx, my);
    containerDrawer.renderTab(g);
    curiosDrawer.renderTab(g);
    if (automation != null) automation.renderTab(g);
    if (redstone != null) redstone.renderTab(g);
  }

  private void draw(GuiGraphics g, Element e, Rect r, int mx, int my, float partial) {
    switch (e) {
      case Element.Custom custom -> {
        g.pose().pushPose();
        g.enableScissor(r.x(), r.y(), r.right(), r.bottom());
        try {
          widgetInput
              .instance(custom)
              .render(
                  new WidgetRenderer.Context(
                      g, custom, r, menu.value(e, false), menu.value(e, true), mx, my, partial));
        } finally {
          g.setColor(1, 1, 1, 1);
          g.disableScissor();
          g.pose().popPose();
        }
      }
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
        if (b.flat()) {
          sprite(
              g, r.contains(mx, my) ? "mmdlib:button_flat_highlighted" : "mmdlib:button_flat", r);
        } else
          sprite(
              g,
              r.contains(mx, my)
                  ? "minecraft:widget/button_highlighted"
                  : "minecraft:widget/button",
              r);
        b.text()
            .ifPresent(
                t ->
                    g.drawCenteredString(
                        font, text(t), r.x() + r.width() / 2, r.y() + 6, 0xffffff));
        int iconSize = b.flat() ? 12 : 16;
        b.iconFor(menu.value(e, false))
            .ifPresent(
                icon ->
                    blit(
                        g,
                        icon,
                        r.x() + (r.width() - iconSize) / 2,
                        r.y() + (r.height() - iconSize) / 2,
                        iconSize,
                        iconSize));
        if (menu.processorConflict(b))
          g.blitSprite(
              ResourceLocation.parse("mmdlib:icon_processor_conflict"),
              r.right() - 6,
              r.bottom() - 6,
              5,
              5);
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
        SideDrawerRenderer.tab(g, r, pop.kind());
        blit(g, pop.iconOrDefault(), r.x() + 3, r.y() + 3, 16, 16);
      }
      case Element.SideConfig config -> drawSides(g, config, r);
      case Element.Gauge gauge ->
          GaugeRenderer.draw(g, r, gauge, menu.value(e, false), menu.value(e, true));
      case Element.EnergyGauge gauge ->
          EnergyGaugeRenderer.draw(
              g, r, gauge, ScreenText.ratio(menu.value(e, false), menu.value(e, true)));
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
      if (e instanceof Element.Popout p && open.contains(p)) {
        result.add(drawerRail(p));
        result.add(popBounds(p));
      } else result.add(bounds(e));
    }
    if (automation != null) result.addAll(automation.exclusions());
    if (redstone != null) result.addAll(redstone.exclusions());
    result.addAll(containerDrawer.exclusions());
    result.addAll(curiosDrawer.exclusions());
    result.addAll(stats.exclusions());
    return result;
  }

  private int popWidth(Element.Popout p) {
    Rect tab = bounds(p);
    int room = p.edge() == Element.Edge.LEFT ? tab.x() - 4 : width - tab.right() - 4;
    return Math.max(0, Math.min(150, room));
  }

  private Rect popBounds(Element.Popout p) {
    Rect tab = bounds(p);
    int w = popWidth(p);
    int top = SideDrawerLayout.top(panelBounds());
    int h = Math.max(tab.bottom() - top, popLines(p).size() * 10 + 12);
    return SideDrawerLayout.body(
        panelBounds(),
        drawerBottom(),
        p.edge() == Element.Edge.LEFT ? tab.x() - w : tab.right(),
        top,
        w,
        h);
  }

  private Rect drawerRail(Element.Popout p) {
    Rect tab = bounds(p);
    return SideDrawerLayout.rail(tab, popBounds(p));
  }

  private List<net.minecraft.util.FormattedCharSequence> popLines(Element.Popout p) {
    Object value = menu.value(p, false);
    var result = new ArrayList<net.minecraft.util.FormattedCharSequence>();
    int textWidth = Math.max(1, popWidth(p) - 12);
    if (p.text().isPresent())
      result.addAll(font.split(ScreenText.markup(text(p.text().get()).getString()), textWidth));
    if (!result.isEmpty()
        && (value instanceof Component || value instanceof List<?> list && !list.isEmpty()))
      result.add(net.minecraft.util.FormattedCharSequence.EMPTY);
    if (value instanceof Component c) result.addAll(font.split(c, textWidth));
    else if (value instanceof List<?> list)
      for (var c : list)
        if (c instanceof Component text) {
          result.addAll(font.split(text, textWidth));
          result.add(net.minecraft.util.FormattedCharSequence.EMPTY);
        }
    return result;
  }

  @Override
  public void render(GuiGraphics g, int mx, int my, float partial) {
    positionAutomation();
    updateWidgets();
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
          .ifPresent(e -> draw(g, e, bounds(e), mx, my, partial));
    for (var p : open) {
      Rect r = popBounds(p);
      Rect tab = bounds(p);
      SideDrawerRenderer.open(g, tab, r, p.edge(), p.kind());
      blit(g, p.iconOrDefault(), tab.x() + 3, tab.y() + 3, 16, 16);
      var lines = popLines(p);
      int max = Math.max(0, lines.size() * 10 - (r.height() - 12));
      int offset = Math.clamp(scroll.getOrDefault(p, 0), 0, max);
      scroll.put(p, offset);
      g.enableScissor(r.x() + 5, r.y() + 5, r.right() - 5, r.bottom() - 5);
      for (int i = 0; i < lines.size(); i++)
        g.drawString(font, lines.get(i), r.x() + 6, r.y() + 6 + i * 10 - offset, 0xff303030, false);
      g.disableScissor();
    }
    // Keep every drawer tab clickable and visible on the expanded rail.
    for (var e : menu.bound.screen().tabs())
      if (e instanceof Element.Popout p) {
        Rect tab = bounds(p);
        SideDrawerRenderer.tab(g, tab, p.kind());
        blit(g, p.iconOrDefault(), tab.x() + 3, tab.y() + 3, 16, 16);
      }
    renderTooltip(g, mx, my);
    if (hovered != null) tooltip(g, hovered, mx, my);
    if (automation != null) automation.tooltip(g, mx, my);
    if (redstone != null) redstone.tooltip(g, mx, my);
    containerDrawer.tooltip(g, mx, my);
    curiosDrawer.tooltip(g, mx, my);
    stats.tooltip(g, mx, my);
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
    if (!e.common().jeiRecipes().isEmpty() && recipeTooltip.test(this, e)) return;
    // A filled slot's lines ride along in the item's own tooltip (getTooltipFromContainerItem).
    if (e instanceof Element.Slot && hoveredSlot != null && hoveredSlot.hasItem()) return;
    var lines = tooltipLines(e, mx, my);
    if (!lines.isEmpty()) g.renderComponentTooltip(font, lines, mx, my);
  }

  @Override
  protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
    var lines = super.getTooltipFromContainerItem(stack);
    if (hovered instanceof Element.Slot slot && menu.slots.get(slot.index()) == hoveredSlot) {
      lines = new ArrayList<>(lines);
      lines.addAll(tooltipLines(slot, 0, 0));
    }
    return lines;
  }

  /** The tooltip lines of {@code e} with the mouse at {@code mx, my}; empty for none. */
  public List<Component> tooltipLines(Element e, int mx, int my) {
    var c = e.common();
    boolean shift = hasShiftDown();
    var text = shift ? c.tooltipShift().or(c::tooltip) : c.tooltip();
    var lines = new ArrayList<Component>();
    lines.addAll(menu.tooltip(e));
    text.ifPresent(
        t -> {
          String translated =
              this.text(t)
                  .getString()
                  .replace(
                      "{value}",
                      e instanceof Element.Button button
                          ? button
                              .labelFor(menu.value(e, false))
                              .map(label -> this.text(label).getString())
                              .orElseGet(() -> ScreenText.number(menu.value(e, false), shift))
                          : ScreenText.number(menu.value(e, false), shift))
                  .replace("{max}", ScreenText.number(menu.value(e, true), shift));
          if (e instanceof Element.Button button && button.processorControl()) {
            var action =
                ProcessorButtonState.fromWire(menu.value(button, false))
                    .map(ProcessorButtonState::action)
                    .map(key -> Component.translatable(key).getString())
                    .orElse("");
            translated = translated.replace("{action}", action);
          }
          translated.lines().map(Component::literal).forEach(lines::add);
        });
    if (c.tooltipShift().isPresent() && !shift)
      lines.add(Component.translatable("screen.mmdlib.shift_for_more"));
    if (e instanceof Element.Button button && menu.processorConflict(button))
      Component.translatable(
              menu.redstone.paused()
                  ? "screen.mmdlib.processor.conflict.redstone"
                  : "screen.mmdlib.processor.conflict.manual")
          .getString()
          .lines()
          .map(Component::literal)
          .forEach(lines::add);
    if (e instanceof Element.Slot slot) {
      var plan = menu.reservationPlan(slot.index());
      if (plan.take() > 0)
        lines.add(Component.translatable("screen.mmdlib.reservation.input", plan.take()));
      var output = plan.add();
      if (!output.isEmpty())
        lines.add(
            Component.translatable(
                "screen.mmdlib.reservation.output", output.getCount(), output.getHoverName()));
    }
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
    return lines;
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
    drawerMemory.userInput();
    if (stats.click(mx, my, button)) {
      widgetInput.clearFocus();
      return true;
    }
    if (containerDrawer.click(mx, my, button) || curiosDrawer.click(mx, my, button)) {
      widgetInput.clearFocus();
      return true;
    }
    positionAutomation();
    if (redstone != null && redstone.click(mx, my, button)) {
      widgetInput.clearFocus();
      return true;
    }
    if (automation != null && automation.click(mx, my, button)) {
      widgetInput.clearFocus();
      return true;
    }
    updateWidgets();
    if (widgetInput.capturing()) return true;
    widgetInput.beginClick();
    boolean onDrawerTab =
        menu.bound.screen().tabs().stream()
            .anyMatch(e -> e instanceof Element.Popout && bounds(e).contains(mx, my));
    for (var p : open)
      if (button >= 0
          && button <= 1
          && (popBounds(p).contains(mx, my) || drawerRail(p).contains(mx, my) && !onDrawerTab)) {
        widgetInput.clearFocus();
        return true;
      }
    for (int i = menu.elements.size() - 1; i >= 0; i--) {
      var e = menu.elements.get(i);
      Rect r = bounds(e);
      if (!visible(e) || !r.contains(mx, my)) continue;
      widgetInput.blurUnless(e);
      if (e instanceof Element.Custom custom && widgetInput.click(custom, mx, my, button))
        return true;
      if (button > 1) continue;
      if (e instanceof Element.Popout p) {
        if (p.edge() == Element.Edge.LEFT) {
          stats.open = false;
          containerDrawer.close();
          curiosDrawer.close();
        }
        if (p.edge() == Element.Edge.RIGHT && menu.redstone != null && menu.redstone.open())
          menu.redstone.send(RedstoneMenu.TOGGLE, menu.redstone.selected(), 0, 0);
        if (automation != null && p.edge() == Element.Edge.RIGHT && menu.automation.open())
          menu.automation.send("close", menu.automation.selected(), -1, "", 0, "");
        if (!open.remove(p)) {
          open.removeIf(other -> other.edge() == p.edge());
          open.add(p);
        }
        drawerMemory.popoutIntent(p, open.contains(p));
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
      if (e instanceof Element.Button b && b.processorControl()) {
        if (button == 0) send(e, 0, button, 0);
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
    widgetInput.clearFocus();
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
    drawerMemory.userInput();
    if (stats.key(key)) return true;
    // A lost drawer acknowledgment must never trap the player in the screen.
    if ((menu.containerDrawer.pending()
            || menu.curiosDrawer.pending()
            || menu.redstone != null && menu.redstone.pending()
            || menu.automation != null && menu.automation.pending())
        && (key == 256
            || net.minecraft.client.Minecraft.getInstance()
                .options
                .keyInventory
                .matches(key, scan))) {
      onClose();
      return true;
    }
    if (containerDrawer.key(key, scan) || curiosDrawer.key(key, scan)) return true;
    if (redstone != null && redstone.key(key)) return true;
    if (automation != null && automation.key(key, scan, modifiers)) return true;
    updateWidgets();
    if (widgetInput.keyPressed(key, scan, modifiers)) return true;
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
    drawerMemory.capture(this, open);
    containerDrawer.cancelGesture();
    curiosDrawer.cancelGesture();
    if (automation != null) automation.removed();
    widgetInput.removed();
    widgetsRemoved = true;
    hover(hovered, false);
    hovered = null;
    super.removed();
  }

  @Override
  public boolean mouseScrolled(double mx, double my, double dx, double dy) {
    if (stats.scroll(mx, my, dy)) return true;
    if (curiosDrawer.scroll(mx, my, dy) || containerDrawer.scroll(mx, my, dy)) return true;
    if (redstone != null && redstone.scroll(mx, my, dy)) return true;
    if (automation != null && automation.scroll(mx, my, dy)) return true;
    updateWidgets();
    for (var p : open)
      if (popBounds(p).contains(mx, my)) {
        scroll.merge(p, (int) (-dy * 10), Integer::sum);
        return true;
      }
    return widgetInput.scroll(mx, my, dx, dy) || super.mouseScrolled(mx, my, dx, dy);
  }

  private void updateWidgets() {
    updateLayout();
    widgetInput.update();
    for (var e : menu.elements)
      if (e instanceof Element.Custom custom) {
        var result = menu.takeWidgetResult(custom);
        if (result != null) widgetInput.instance(custom).actionResult(result.accepted());
      }
  }

  @Override
  protected void containerTick() {
    super.containerTick();
    drawerMemory.restore(this, open);
    updateWidgets();
    widgetInput.tick();
  }

  @Override
  public void mouseMoved(double x, double y) {
    containerDrawer.mouseMoved(x, y);
    curiosDrawer.mouseMoved(x, y);
    super.mouseMoved(x, y);
  }

  @Override
  public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
    if (stats.drag()) return true;
    if (containerDrawer.drag(x, y, button) || curiosDrawer.drag(x, y, button)) return true;
    if (redstone != null && redstone.drag(x, y, button)) return true;
    if (automation != null && automation.drag(x, y, button, dx, dy)) return true;
    updateWidgets();
    return widgetInput.drag(x, y, button, dx, dy) || super.mouseDragged(x, y, button, dx, dy);
  }

  @Override
  public boolean mouseReleased(double x, double y, int button) {
    if (stats.release()) return true;
    boolean curiosReleased = curiosDrawer.release(x, y, button);
    if (containerDrawer.release(x, y, button) || curiosReleased) return true;
    if (redstone != null && redstone.release(x, y, button)) return true;
    if (automation != null && automation.release(x, y, button)) return true;
    updateWidgets();
    return widgetInput.release(x, y, button) || super.mouseReleased(x, y, button);
  }

  @Override
  public boolean charTyped(char character, int modifiers) {
    if (automation != null && automation.character(character, modifiers)) return true;
    updateWidgets();
    return widgetInput.charTyped(character, modifiers) || super.charTyped(character, modifiers);
  }

  @Override
  public boolean keyReleased(int key, int scan, int modifiers) {
    updateWidgets();
    return widgetInput.keyReleased(key, scan, modifiers) || super.keyReleased(key, scan, modifiers);
  }

  @Override
  protected boolean hasClickedOutside(double x, double y, int left, int top, int button) {
    return !stats.contains(x, y)
        && (redstone == null || !redstone.contains(x, y))
        && (automation == null || !automation.contains(x, y))
        && !containerDrawer.contains(x, y)
        && !curiosDrawer.contains(x, y)
        && super.hasClickedOutside(x, y, left, top, button);
  }

  private boolean isFilterSlot(net.minecraft.world.inventory.Slot slot) {
    return menu.automation != null
        && slot.index >= menu.automation.slotStart()
        && slot.index < menu.automation.slotStart() + menu.automation.slotCount()
        && (slot.index - menu.automation.slotStart()) % AutomationInventory.SLOTS_PER_GROUP
            >= AutomationInventory.COLUMNS;
  }

  @Override
  protected void renderSlotHighlight(
      GuiGraphics graphics,
      net.minecraft.world.inventory.Slot slot,
      int mouseX,
      int mouseY,
      float partialTick) {
    if (!isFilterSlot(slot)) {
      super.renderSlotHighlight(graphics, slot, mouseX, mouseY, partialTick);
      return;
    }
    if (slot.isHighlightable()) {
      int colour = getSlotColor(slot.index);
      graphics.fillGradient(
          net.minecraft.client.renderer.RenderType.guiOverlay(),
          slot.x,
          slot.y,
          slot.x + AutomationLayout.FILTER_ITEM_SIZE,
          slot.y + AutomationLayout.FILTER_ITEM_SIZE,
          colour,
          colour,
          0);
    }
  }

  @Override
  protected void renderSlot(GuiGraphics graphics, net.minecraft.world.inventory.Slot slot) {
    if (!isFilterSlot(slot)) {
      super.renderSlot(graphics, slot);
      return;
    }
    graphics.pose().pushPose();
    graphics.pose().translate(slot.x, slot.y, 0);
    float scale = AutomationLayout.FILTER_ITEM_SIZE / 16f;
    graphics.pose().scale(scale, scale, 1);
    graphics.pose().translate(-slot.x, -slot.y, 0);
    super.renderSlot(graphics, slot);
    graphics.pose().popPose();
  }

  @Override
  protected void renderSlotContents(
      GuiGraphics graphics,
      ItemStack displayed,
      net.minecraft.world.inventory.Slot slot,
      @org.jetbrains.annotations.Nullable String countString) {
    if (!menu.isMachineItemSlot(slot.index)) {
      super.renderSlotContents(graphics, displayed, slot, countString);
      return;
    }
    // Keep vanilla item/drag rendering and decorations, then draw both quantities at one scale.
    super.renderSlotContents(graphics, displayed, slot, "");
    reservations.render(
        graphics, font, slot, displayed, menu.reservationPlan(slot.index), countString);
  }

  @Override
  protected boolean isHovering(int x, int y, int w, int h, double mx, double my) {
    if (menu.automation != null && menu.automation.open() && w == 16 && h == 16)
      for (int c = 0; c < AutomationInventory.COLUMNS; c++) {
        int i =
            menu.automation.slotStart()
                + AutomationInventory.filterSlot(menu.automation.selected(), c);
        var slot = menu.slots.get(i);
        if (slot.isActive() && slot.x == x && slot.y == y)
          return super.isHovering(
              x, y, AutomationLayout.FILTER_ITEM_SIZE, AutomationLayout.FILTER_ITEM_SIZE, mx, my);
      }
    return super.isHovering(x, y, w, h, mx, my);
  }
}
