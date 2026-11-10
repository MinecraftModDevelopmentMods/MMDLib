/**
 * Extensible common-side GUI value contracts. Register {@link
 * zone.moddev.mc.lib.gui.value.ValueType} declarations during mod construction with {@link
 * zone.moddev.mc.lib.gui.value.ValueTypeRegistry#GLOBAL}. Each declaration owns Java bindings,
 * normalization, snapshot isolation, content equality and bounded versioned transport. Built-in
 * declarations use the same path as mod-owned types.
 */
package zone.moddev.mc.lib.gui.value;
