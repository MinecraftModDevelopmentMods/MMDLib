package zone.moddev.mc.lib.gui.model;

import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/**
 * One element of a screen file. Each record matches an XML element of the same name; {@link
 * #common()} holds the attributes every element takes. Containers list their children in document
 * order.
 */
public sealed interface Element {
  /** The attributes every element takes. */
  Common common();

  /** Pixels in one slot: the unit of {@code s}-suffixed sizes such as {@code length="3s"}. */
  int SLOT = 18;

  /**
   * A {@code length} of {@code fill}: as long as the box around the element is across. Layout
   * replaces it with the real length.
   */
  int FILL = -1;

  /** The element's children; empty for leaves. */
  default List<Element> children() {
    return List.of();
  }

  /** Cross-axis alignment inside a box. */
  enum Align {
    START,
    CENTER,
    END
  }

  /** Which way a gauge or meter runs. */
  enum Orientation {
    VERTICAL,
    HORIZONTAL
  }

  /** Which way a progress indicator fills. */
  enum Direction {
    RIGHT,
    LEFT,
    UP,
    DOWN
  }

  /** The panel edge a tab grows from. */
  enum Edge {
    LEFT,
    RIGHT
  }

  /**
   * {@code vbox} (vertical) or {@code hbox} (horizontal): stacks its children.
   *
   * @param vertical true for {@code vbox}
   * @param padding space inside the box's edges, in GUI pixels
   * @param spacing space between children
   * @param align cross-axis alignment of the children
   */
  record Box(
      Common common,
      boolean vertical,
      int padding,
      int spacing,
      Align align,
      List<Element> children)
      implements Element {
    /** Copies the children. */
    public Box {
      children = List.copyOf(children);
    }
  }

  /** {@code spacer}: takes the leftover room in its box. */
  record Spacer(Common common) implements Element {}

  /**
   * {@code group}: a recessed panel around its children.
   *
   * @param label an optional caption
   * @param tint an optional ARGB tint for the panel
   * @param padding space inside the panel's edges
   */
  record Group(
      Common common,
      Optional<Text> label,
      Optional<Integer> tint,
      int padding,
      List<Element> children)
      implements Element {
    /** Copies the children. */
    public Group {
      children = List.copyOf(children);
    }
  }

  /** The size of a slot: 18x18, or 26x26 for an output. */
  enum SlotSize {
    NORMAL,
    LARGE
  }

  /** What a slot is for. */
  enum SlotKind {
    /** An ordinary slot. */
    NORMAL,
    /** An upgrade slot: one item, gold tint, ghost outline. */
    UPGRADE,
    /** A JEI ghost slot: sets the item the slot expects, never holds real items. */
    GHOST
  }

  /**
   * {@code slot}: slot {@code index} of the menu, exactly as numbered by the block entity's code.
   *
   * @param index the menu slot number
   * @param size the slot's size
   * @param kind what the slot is for
   * @param tint an optional ARGB tint
   * @param icon an optional sprite shown while the slot is empty
   */
  record Slot(
      Common common,
      int index,
      SlotSize size,
      SlotKind kind,
      Optional<Integer> tint,
      Optional<ResourceLocation> icon)
      implements Element {}

  /**
   * {@code gauge}: the glass graduated gauge, 18 px thick. Filled with {@code color} when given,
   * otherwise with the fluid its value returns.
   *
   * @param orientation which way it runs
   * @param length its length in GUI pixels, or {@link #FILL}
   * @param color an optional ARGB solid fill
   */
  record Gauge(Common common, Orientation orientation, int length, Optional<Integer> color)
      implements Element {
    /** Whether the length is {@code fill}. */
    public boolean fills() {
      return length == FILL;
    }
  }

  /**
   * {@code meter}: the compact segmented gauge.
   *
   * @param orientation which way it runs
   * @param thickness 5 or 10 GUI pixels
   * @param length its length in GUI pixels, or {@link #FILL}
   * @param color the ARGB segment color
   * @param segment segment size in pixels
   * @param gap gap between segments in pixels
   * @param gapColor an optional ARGB gap color; the panel background otherwise
   */
  record Meter(
      Common common,
      Orientation orientation,
      int thickness,
      int length,
      int color,
      int segment,
      int gap,
      Optional<Integer> gapColor)
      implements Element {
    /** Whether the length is {@code fill}. */
    public boolean fills() {
      return length == FILL;
    }
  }

  /**
   * {@code progress}: a fill-in progress icon.
   *
   * @param icon the icon sprite; the default arrow when empty
   * @param direction which way it fills
   */
  record Progress(Common common, Optional<ResourceLocation> icon, Direction direction)
      implements Element {}

  /**
   * {@code label}: a line of text.
   *
   * @param text the text
   */
  record Label(Common common, Text text) implements Element {}

  /**
   * {@code button}: a vanilla-style button. Its tooltip is required.
   *
   * @param text the caption, if any
   * @param icon the icon sprite, if any
   * @param shows the view it switches to, if it is a navigation button
   */
  record Button(
      Common common, Optional<Text> text, Optional<ResourceLocation> icon, Optional<ViewRef> shows)
      implements Element {}

  /**
   * {@code checkbox}: a checkbox whose value is a boolean getter.
   *
   * @param text the caption
   */
  record Checkbox(Common common, Text text) implements Element {}

  /**
   * {@code radio-group}: radio buttons; its value is the selected index.
   *
   * @param label an optional caption
   * @param options the radio buttons, in order
   */
  record RadioGroup(Common common, Optional<Text> label, List<Radio> options) implements Element {
    /** Copies the options. */
    public RadioGroup {
      options = List.copyOf(options);
    }

    @Override
    public List<Element> children() {
      return List.copyOf(options);
    }
  }

  /**
   * {@code radio}: one option of a radio group.
   *
   * @param text the caption
   */
  record Radio(Common common, Text text) implements Element {}

  /**
   * {@code context}: an area whose content buttons swap in place. The first view is the default.
   *
   * @param views the alternative contents
   */
  record Context(Common common, List<View> views) implements Element {
    /** Copies the views. */
    public Context {
      views = List.copyOf(views);
    }

    @Override
    public List<Element> children() {
      return List.copyOf(views);
    }

    /** The view shown when the screen opens. */
    public View defaultView() {
      return views.getFirst();
    }
  }

  /**
   * {@code view}: one alternative content of a context area.
   *
   * @param title an optional title drawn at its top
   */
  record View(Common common, Optional<Text> title, List<Element> children) implements Element {
    /** Copies the children. */
    public View {
      children = List.copyOf(children);
    }
  }

  /**
   * {@code side-config}: six-face IO configuration, one resource group at a time.
   *
   * @param groups the IDs of the elements showing each group, in the order offered
   */
  record SideConfig(Common common, List<String> groups) implements Element {
    /** Copies the groups. */
    public SideConfig {
      groups = List.copyOf(groups);
    }
  }

  /** {@code player-inventory}: places the player inventory explicitly. */
  record PlayerInventory(Common common) implements Element {}

  /**
   * {@code side-tab}: a tab growing out of the panel's edge, such as the upgrade column.
   *
   * @param edge the panel edge
   */
  record SideTab(Common common, Edge edge, List<Element> children) implements Element {
    /** Copies the children. */
    public SideTab {
      children = List.copyOf(children);
    }
  }

  /** What a pop-out reports. */
  enum PopoutKind {
    /** Explains the screen; fixed text. */
    INFO,
    /** A live warning from the block entity. */
    WARNING,
    /** A live error from the block entity. */
    ERROR
  }

  /**
   * {@code popout}: an information pop-out on an edge tab. An {@code info} pop-out shows {@code
   * text}; {@code warning} and {@code error} pop-outs show what their value getter reports.
   *
   * @param kind what it reports
   * @param icon the tab icon; the kind's default when empty
   * @param edge the panel edge
   * @param text the fixed text of an info pop-out
   */
  record Popout(
      Common common,
      PopoutKind kind,
      Optional<ResourceLocation> icon,
      Edge edge,
      Optional<Text> text)
      implements Element {
    /** The tab icon: the given one, or {@code mmdlib:icon_<kind>}. */
    public ResourceLocation iconOrDefault() {
      return icon.orElse(
          ResourceLocation.fromNamespaceAndPath(
              "mmdlib", "icon_" + kind.name().toLowerCase(java.util.Locale.ROOT)));
    }
  }

  /**
   * A {@code shows} target: {@code context:view}.
   *
   * @param context the context area's ID
   * @param view the view's ID
   */
  record ViewRef(String context, String view) {
    /** Parses {@code context:view}. */
    public static ViewRef parse(String text) {
      int colon = text.indexOf(':');
      if (colon <= 0 || colon == text.length() - 1)
        throw new IllegalArgumentException("Expected context:view: " + text);
      return new ViewRef(text.substring(0, colon), text.substring(colon + 1));
    }

    @Override
    public String toString() {
      return context + ":" + view;
    }
  }
}
