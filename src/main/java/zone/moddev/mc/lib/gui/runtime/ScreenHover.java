package zone.moddev.mc.lib.gui.runtime;

import zone.moddev.mc.lib.gui.model.Element;

/** Client-only display notification. Hover callbacks must not mutate the machine. */
public record ScreenHover(MachineMenu menu, Element element, boolean entered) {}
