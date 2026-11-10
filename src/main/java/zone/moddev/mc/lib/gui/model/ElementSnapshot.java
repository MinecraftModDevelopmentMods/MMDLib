package zone.moddev.mc.lib.gui.model;

/** An element's server-sampled value and maximum, before conversion to synchronized values. */
public record ElementSnapshot(Object value, Object maximum) {}
