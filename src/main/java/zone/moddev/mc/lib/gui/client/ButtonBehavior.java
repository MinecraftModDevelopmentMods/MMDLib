package zone.moddev.mc.lib.gui.client;

import java.util.Optional;

import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.elements.ButtonControl;

/** Button-local tooltip labels; navigation is shared by all default element behaviors. */
public class ButtonBehavior extends DefaultElementBehavior implements ValueLabelProvider {
  private final ButtonControl button;

  public ButtonBehavior(ButtonControl button, ElementAccess access) {
    super(access);
    this.button = button;
  }

  public Optional<Text> valueLabel() {
    return button.labelFor(access.value());
  }
}
