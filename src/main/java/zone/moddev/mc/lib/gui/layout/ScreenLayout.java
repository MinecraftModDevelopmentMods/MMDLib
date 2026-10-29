package zone.moddev.mc.lib.gui.layout;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Element.*;
import zone.moddev.mc.lib.gui.model.Screen;

/**
 * Places every element of a screen, in GUI pixels relative to the main panel's top-left corner.
 *
 * <p>The main panel stacks the screen's top-level elements, then (unless the screen places it or
 * leaves it out) the player inventory, centred. Boxes stack their children; inside a {@code vbox} a
 * child {@code hbox} stretches to the full width, and inside an {@code hbox} a child {@code vbox}
 * stretches to the full height, so {@code spacer}s have room to fill. A context area is as large as
 * its largest view, and every view is placed at the same spot. Side tabs and pop-out tabs stack
 * down their panel edge, side tabs first.
 *
 * <p>Fixed sizes come from the sprites ({@code docs/gui-sprites.md}): slots 18 px (26 large),
 * gauges 18 px thick, progress arrows 24x16, checkboxes and radios 10 px, buttons 20 px high,
 * side-configuration faces 20 px in a 3x3 grid.
 */
public final class ScreenLayout {
  /** Width and height of the player inventory: three rows and the hotbar, plus its title. */
  public static final int INVENTORY_WIDTH = 162, INVENTORY_HEIGHT = 11 + 3 * 18 + 4 + 18;

  /** Margin around the panel's content, as vanilla screens. */
  public static final int PANEL_MARGIN = 7;

  /** Gap between stacked top-level elements, and before the player inventory. */
  public static final int GAP = 4;

  /** Space around the content of a side tab, and the tab's overlap with the panel. */
  public static final int TAB_PADDING = 4, TAB_OVERLAP = 4, TAB_BORDER = 3;

  /** Size of a collapsed pop-out tab: a 16 px icon with a 3 px border. */
  public static final int POPOUT_TAB = 22;

  /** Gap between tabs stacked on one edge. */
  public static final int TAB_GAP = 2;

  /** Size of one side-configuration face, and the gap between faces. */
  public static final int FACE = 20, FACE_GAP = 2;

  /** Height of the side-configuration group selector above the faces. */
  public static final int SELECTOR = 20;

  private final Map<Element, Rect> bounds = new IdentityHashMap<>();
  private final TextMeasure text;
  private Rect panel;
  private Rect inventory;

  private ScreenLayout(TextMeasure text) {
    this.text = text;
  }

  /**
   * Lays out {@code screen}, measuring text with {@code text}.
   *
   * @throws IllegalArgumentException if the tabs on one edge do not fit the panel's height
   */
  public static ScreenLayout of(Screen screen, TextMeasure text) {
    var layout = new ScreenLayout(text);
    layout.place(screen);
    return layout;
  }

  /** The main panel, at (0, 0). */
  public Rect panel() {
    return panel;
  }

  /** Where the player inventory's title starts, if the screen shows the inventory. */
  public Optional<Rect> inventory() {
    return Optional.ofNullable(inventory);
  }

  /** Where {@code element} is drawn. */
  public Rect bounds(Element element) {
    var rect = bounds.get(element);
    if (rect == null) throw new IllegalArgumentException("Not on this screen: " + element);
    return rect;
  }

  /**
   * Where a menu slot puts its item, as vanilla {@code Slot} coordinates: 1 px inside an 18 px
   * slot, centred in a 26 px one.
   */
  public static int[] itemPosition(Rect slot) {
    int inset = slot.width() == 26 ? 5 : 1;
    return new int[] {slot.x() + inset, slot.y() + inset};
  }

  // Measuring

  private int[] size(Element element) {
    return switch (element) {
      case Box box -> boxSize(box);
      case Spacer spacer -> new int[] {0, 0};
      case Group group -> {
        int[] inner = stack(group.children(), 2);
        int label = group.label().isPresent() ? TextMeasure.LINE_HEIGHT + 2 : 0;
        int width = Math.max(inner[0], group.label().map(text::width).orElse(0));
        yield new int[] {width + 2 * group.padding(), inner[1] + label + 2 * group.padding()};
      }
      case Slot slot -> slot.size() == SlotSize.LARGE ? new int[] {26, 26} : new int[] {18, 18};
      // A fill length counts as its minimum here; placement stretches it.
      case Gauge gauge -> oriented(gauge.orientation(), 18, gauge.fills() ? 8 : gauge.length());
      case Meter meter ->
          oriented(meter.orientation(), meter.thickness(), meter.fills() ? 4 : meter.length());
      case Progress progress ->
          progress.direction() == Direction.UP || progress.direction() == Direction.DOWN
              ? new int[] {16, 24}
              : new int[] {24, 16};
      case Label label -> new int[] {text.width(label.text()), TextMeasure.LINE_HEIGHT};
      case Button button ->
          button.text().isPresent()
              ? new int[] {Math.max(20, text.width(button.text().get()) + 8), 20}
              : new int[] {20, 20};
      case Checkbox box -> new int[] {10 + 4 + text.width(box.text()), 10};
      case Radio radio -> new int[] {10 + 4 + text.width(radio.text()), 10};
      case RadioGroup group -> {
        int[] inner = stack(group.children(), 2);
        int label = group.label().isPresent() ? TextMeasure.LINE_HEIGHT + 2 : 0;
        yield new int[] {
          Math.max(inner[0], group.label().map(text::width).orElse(0)), inner[1] + label
        };
      }
      case Context context -> {
        int width = 0, height = 0;
        for (var view : context.views()) {
          int[] size = size(view);
          width = Math.max(width, size[0]);
          height = Math.max(height, size[1]);
        }
        yield new int[] {width, height};
      }
      case View view -> {
        int[] inner = stack(view.children(), GAP);
        int title = view.title().isPresent() ? TextMeasure.LINE_HEIGHT + GAP : 0;
        yield new int[] {
          Math.max(inner[0], view.title().map(text::width).orElse(0) + 12), inner[1] + title
        };
      }
      case SideConfig config -> {
        int grid = 3 * FACE + 2 * FACE_GAP;
        yield new int[] {grid, SELECTOR + GAP + grid};
      }
      case PlayerInventory inventory -> new int[] {INVENTORY_WIDTH, INVENTORY_HEIGHT};
      case SideTab tab -> {
        int[] inner = stack(tab.children(), 2);
        yield new int[] {
          inner[0] + 2 * TAB_PADDING + TAB_OVERLAP + TAB_BORDER, inner[1] + 2 * TAB_PADDING
        };
      }
      case Popout popout -> new int[] {POPOUT_TAB, POPOUT_TAB};
    };
  }

  private static int[] oriented(Orientation orientation, int thickness, int length) {
    return orientation == Orientation.VERTICAL
        ? new int[] {thickness, length}
        : new int[] {length, thickness};
  }

  private int[] boxSize(Box box) {
    int[] inner = box.vertical() ? stack(box.children(), box.spacing()) : row(box);
    return new int[] {inner[0] + 2 * box.padding(), inner[1] + 2 * box.padding()};
  }

  private int[] row(Box box) {
    int width = 0, height = 0;
    for (var child : box.children()) {
      int[] size = size(child);
      width += size[0];
      height = Math.max(height, size[1]);
    }
    width += box.spacing() * Math.max(0, box.children().size() - 1);
    return new int[] {width, height};
  }

  /** Children stacked top to bottom: widest child, summed heights plus gaps. */
  private int[] stack(List<Element> children, int spacing) {
    int width = 0, height = 0;
    for (var child : children) {
      int[] size = size(child);
      width = Math.max(width, size[0]);
      height += size[1];
    }
    height += spacing * Math.max(0, children.size() - 1);
    return new int[] {width, height};
  }

  // Placing

  private void place(Screen screen) {
    int[] body = stack(screen.body(), GAP);
    boolean addInventory =
        screen.playerInventory()
            && screen.elements().stream().noneMatch(PlayerInventory.class::isInstance);
    int width = body[0];
    int height = body[1];
    if (addInventory) {
      width = Math.max(width, INVENTORY_WIDTH + 2 * PANEL_MARGIN);
      height += GAP + INVENTORY_HEIGHT + PANEL_MARGIN;
    }
    int y = 0;
    for (var element : screen.body()) {
      int[] size = size(element);
      // Top-level boxes span the panel, as a vbox's children do.
      int w = element instanceof Box || fills(element) ? width : size[0];
      placeAt(element, 0, y, w, size[1]);
      y += size[1] + GAP;
    }
    panel = new Rect(0, 0, width, height);
    if (addInventory)
      inventory =
          new Rect((width - INVENTORY_WIDTH) / 2, body[1] + GAP, INVENTORY_WIDTH, INVENTORY_HEIGHT);
    for (var element : screen.elements())
      if (element instanceof PlayerInventory) inventory = bounds.get(element);
    placeTabs(screen, Edge.LEFT);
    placeTabs(screen, Edge.RIGHT);
    applyOffsets(screen);
  }

  private void placeAt(Element element, int x, int y, int width, int height) {
    bounds.put(element, new Rect(x, y, width, height));
    switch (element) {
      case Box box -> placeBox(box, x, y, width, height);
      case Group group -> {
        int top =
            y + group.padding() + (group.label().isPresent() ? TextMeasure.LINE_HEIGHT + 2 : 0);
        placeStack(group.children(), x + group.padding(), top, width - 2 * group.padding(), 2);
      }
      case RadioGroup group -> {
        int top = y + (group.label().isPresent() ? TextMeasure.LINE_HEIGHT + 2 : 0);
        placeStack(group.children(), x, top, width, 2);
      }
      case Context context -> {
        for (var view : context.views()) placeAt(view, x, y, width, height);
      }
      case View view -> {
        int top = y + (view.title().isPresent() ? TextMeasure.LINE_HEIGHT + GAP : 0);
        placeStack(view.children(), x, top, width, GAP);
      }
      case SideTab tab -> {
        int inner =
            tab.edge() == Edge.RIGHT ? x + TAB_OVERLAP + TAB_PADDING : x + TAB_BORDER + TAB_PADDING;
        placeStack(
            tab.children(),
            inner,
            y + TAB_PADDING,
            width - 2 * TAB_PADDING - TAB_OVERLAP - TAB_BORDER,
            2);
      }
      default -> {}
    }
  }

  private void placeStack(List<Element> children, int x, int y, int width, int spacing) {
    for (var child : children) {
      int[] size = size(child);
      int w = (child instanceof Box box && !box.vertical()) || fills(child) ? width : size[0];
      placeAt(child, x, y, w, size[1]);
      y += size[1] + spacing;
    }
  }

  private void placeBox(Box box, int x, int y, int width, int height) {
    int innerX = x + box.padding(), innerY = y + box.padding();
    int innerW = width - 2 * box.padding(), innerH = height - 2 * box.padding();
    var sizes = new ArrayList<int[]>();
    int used = 0, spacers = 0;
    for (var child : box.children()) {
      int[] size = size(child);
      sizes.add(size);
      used += box.vertical() ? size[1] : size[0];
      if (child instanceof Spacer) spacers++;
    }
    used += box.spacing() * Math.max(0, box.children().size() - 1);
    int leftover = Math.max(0, (box.vertical() ? innerH : innerW) - used);
    int position = box.vertical() ? innerY : innerX;
    int spacerIndex = 0;
    for (int i = 0; i < box.children().size(); i++) {
      var child = box.children().get(i);
      int[] size = sizes.get(i);
      int main = box.vertical() ? size[1] : size[0];
      if (child instanceof Spacer) {
        // Share the leftover room; the first spacers take any remainder pixel.
        main = leftover / spacers + (spacerIndex++ < leftover % spacers ? 1 : 0);
      }
      if (box.vertical()) {
        boolean stretch = (child instanceof Box b && !b.vertical()) || fills(child);
        int w = stretch ? innerW : size[0];
        placeAt(child, innerX + align(box.align(), innerW, w), position, w, main);
      } else {
        boolean stretch = (child instanceof Box b && b.vertical()) || fills(child);
        int h = stretch ? innerH : size[1];
        placeAt(child, position, innerY + align(box.align(), innerH, h), main, h);
      }
      position += main + box.spacing();
    }
  }

  /** Whether {@code element} has {@code length="fill"}; the reader checks it sits where it can. */
  private static boolean fills(Element element) {
    return (element instanceof Gauge gauge && gauge.fills())
        || (element instanceof Meter meter && meter.fills());
  }

  private static int align(Align align, int room, int size) {
    return switch (align) {
      case START -> 0;
      case CENTER -> (room - size) / 2;
      case END -> room - size;
    };
  }

  private void placeTabs(Screen screen, Edge edge) {
    int y = TAB_PADDING;
    // Side tabs first, then pop-outs, each in document order.
    var ordered = new ArrayList<Element>();
    for (var tab : screen.tabs())
      if (tab instanceof SideTab side && side.edge() == edge) ordered.add(tab);
    for (var tab : screen.tabs())
      if (tab instanceof Popout popout && popout.edge() == edge) ordered.add(tab);
    for (var tab : ordered) {
      int[] size = size(tab);
      int x =
          edge == Edge.RIGHT
              ? panel.width() - (tab instanceof SideTab ? TAB_OVERLAP : 0)
              : -size[0] + (tab instanceof SideTab ? TAB_OVERLAP : 0);
      placeAt(tab, x, y, size[0], size[1]);
      y += size[1] + TAB_GAP;
    }
    if (y - TAB_GAP > panel.height())
      throw new IllegalArgumentException(
          "The tabs on the "
              + edge.name().toLowerCase(java.util.Locale.ROOT)
              + " edge need "
              + (y - TAB_GAP)
              + " px but the panel is "
              + panel.height()
              + " px high; move some to the other edge");
  }

  /** Nudges each offset element together with everything inside it. */
  private void applyOffsets(Screen screen) {
    for (var element : screen.elements()) {
      var c = element.common();
      if (c.offsetX() != 0 || c.offsetY() != 0) shift(element, c.offsetX(), c.offsetY());
    }
  }

  private void shift(Element element, int dx, int dy) {
    bounds.put(element, bounds.get(element).offset(dx, dy));
    for (var child : element.children()) shift(child, dx, dy);
  }
}
