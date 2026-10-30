/**
 * MMDLib's declarative GUI: screens described in XML ({@code mmdlib/screen/*.xml}), validated
 * against {@code mmdlib/schema/screen.xsd} and loaded into a typed model at startup. A block
 * entity's screen has the same ID as its block-entity type. See {@code docs/gui-screens.md} for the
 * format and its design decisions.
 *
 * <ul>
 *   <li>{@link zone.moddev.mc.lib.gui.Screens}: the loaded screens.
 *   <li>{@code gui.model}: the typed screen model, one record per XML element.
 *   <li>{@code gui.load}: reading, validating and binding screen files.
 *   <li>{@code gui.runtime}: generic menus, block opening, server actions and value packets.
 *   <li>{@code gui.client}: widget rendering, navigation, pop-outs and tooltips.
 * </ul>
 */
package zone.moddev.mc.lib.gui;
