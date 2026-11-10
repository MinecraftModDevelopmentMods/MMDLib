package zone.moddev.mc.lib.gui.client;

import zone.moddev.mc.lib.gui.model.elements.RadioElement;

/** A radio option delegates activation to its parent's registered selection capability. */
public class RadioBehavior extends DefaultElementBehavior {
  private final RadioElement radio;

  public RadioBehavior(RadioElement radio, ElementAccess access) {
    super(access);
    this.radio = radio;
  }

  protected Click click(double x, double y, int button) {
    if (radio.common().clicks().any()) return Click.PASS;
    return OptionSelection.parent(radio, access.presentation())
        .map(selection -> selection.activate(radio, button, access.modifiers()))
        .orElse(Click.PASS);
  }
}
