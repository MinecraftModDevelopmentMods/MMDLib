package zone.moddev.mc.lib.api.automation;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Common-side declarative editor fragment. Ghost cells never create real inventory slots. */
public sealed interface FilterControl {
  String id();

  record Toggle(String id, ResourceLocation icon, Component tooltip) implements FilterControl {}

  /** A registered leaf can provide specialised rendering/input and a typed, validated action. */
  record Custom(String id, FilterWidgetType<?> type, int height) implements FilterControl {
    public Custom {
      if (height < 1 || height > 256) throw new IllegalArgumentException("Custom control height");
      java.util.Objects.requireNonNull(type);
    }
  }

  record Text(String id, int maxLength, Component tooltip) implements FilterControl {
    public Text {
      if (maxLength < 1 || maxLength > 256) throw new IllegalArgumentException("Text limit");
    }
  }

  record Number(String id, int min, int max, int step, Component tooltip) implements FilterControl {
    public Number {
      if (min > max || step < 1) throw new IllegalArgumentException("Number bounds");
    }
  }

  record Samples(String id, int count, ResourceType<?> resource) implements FilterControl {
    public Samples {
      if (count < 1 || count > 256) throw new IllegalArgumentException("Sample limit");
    }
  }

  record Box(String id, boolean horizontal, List<FilterControl> children) implements FilterControl {
    public Box {
      children = List.copyOf(children);
    }
  }

  static List<FilterControl> flatten(FilterControl root) {
    var controls = new java.util.ArrayList<FilterControl>();
    collect(root, controls, 0);
    var ids = new java.util.HashSet<String>();
    for (var control : controls)
      if (!control.id().matches("[a-z0-9_.-]{1,64}") || !ids.add(control.id()))
        throw new IllegalArgumentException("Invalid/duplicate editor control: " + control.id());
    return List.copyOf(controls);
  }

  private static void collect(FilterControl control, List<FilterControl> result, int depth) {
    if (depth > 8 || result.size() >= 128) throw new IllegalArgumentException("Editor too large");
    result.add(control);
    if (control instanceof Box box)
      for (var child : box.children()) collect(child, result, depth + 1);
  }
}
