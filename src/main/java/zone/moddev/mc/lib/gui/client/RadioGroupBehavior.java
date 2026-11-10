package zone.moddev.mc.lib.gui.client;

import java.math.BigInteger;

import zone.moddev.mc.lib.gui.ElementActions;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.containers.RadioGroupElement;

/** Owns radio membership, synchronized selection and activation of the group's server action. */
public class RadioGroupBehavior extends DefaultElementBehavior implements OptionSelection {
  private final RadioGroupElement group;

  public RadioGroupBehavior(RadioGroupElement group, ElementAccess access) {
    super(access);
    this.group = group;
  }

  private int index(Element option) {
    for (int i = 0; i < group.options().size(); i++) if (group.options().get(i) == option) return i;
    return -1;
  }

  public boolean selected(Element option) {
    int index = index(option);
    return index >= 0 && BigInteger.valueOf(index).equals(access.value());
  }

  public Click activate(Element option, int button, int modifiers) {
    int index = index(option);
    if (index < 0 || button < 0 || button > 1) return Click.PASS;
    access.submit(ElementActions.CLICK, new ElementActions.Click(button, modifiers, index));
    return Click.CONSUME;
  }
}
