package zone.moddev.mc.lib.gui.model;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.ElementAction;
import zone.moddev.mc.lib.gui.ElementActions;
import zone.moddev.mc.lib.gui.model.containers.NavigableElement;
import zone.moddev.mc.lib.gui.sync.ElementStateView;
import zone.moddev.mc.lib.gui.sync.ElementStates;

/** An element-owned navigation target. Selecting the current target returns to its default. */
public record Navigation(ViewRef target) {
  public Navigation {
    Objects.requireNonNull(target);
  }

  public static final ElementAction<ElementActions.Click> NAVIGATE =
      new ElementAction<>(
          ResourceLocation.parse("mmdlib:navigate"),
          ElementActions.Click.CODEC,
          8,
          (context, click) ->
              click.option() == 0
                  && context
                      .element()
                      .navigation()
                      .map(
                          binding ->
                              binding.select(
                                  context.menu().bound.screen()::find,
                                  context.menu().elementStates()))
                      .orElse(false));

  /** Resolve only the public navigation contract, independently of concrete container classes. */
  public Optional<NavigableElement> resolve(Function<String, Optional<Element>> find) {
    return find.apply(target.context())
        .filter(NavigableElement.class::isInstance)
        .map(NavigableElement.class::cast);
  }

  public void validate(Screen screen) {
    var container =
        resolve(screen::find)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "shows names no navigable element: " + target.context()));
    if (!container.hasSelection(target.view()))
      throw new IllegalArgumentException("shows names no view: " + target);
    if (!container.hasSelection(container.defaultSelection()))
      throw new IllegalArgumentException(
          "Navigation default is not a valid selection: " + target.context());
  }

  /** The same destination calculation is used for local prediction and authoritative selection. */
  public String destination(NavigableElement container, ElementStateView state) {
    return container.selected(state).equals(target.view())
        ? container.defaultSelection()
        : target.view();
  }

  public boolean select(Function<String, Optional<Element>> find, ElementStates state) {
    var container = resolve(find).orElse(null);
    if (container == null || !container.hasSelection(target.view())) return false;
    var destination = destination(container, state);
    if (!container.hasSelection(destination)) return false;
    container.select(state, destination);
    return container.selected(state).equals(destination);
  }
}
