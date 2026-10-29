/**
 * MMDLib's public extension API: what another mod uses to add content to the material system.
 *
 * <ul>
 *   <li>{@link zone.moddev.mc.lib.api.MaterialExtensions} is the catalog of modules, Java
 *       implementations, part types and events. Register with {@link
 *       zone.moddev.mc.lib.api.MaterialExtensions#GLOBAL} during mod construction.
 *   <li>{@link zone.moddev.mc.lib.api.ItemImplementation} and {@link
 *       zone.moddev.mc.lib.api.BlockImplementation} are the Java code a data module's {@code
 *       factory} names; they receive an {@link zone.moddev.mc.lib.api.ItemContext} or {@link
 *       zone.moddev.mc.lib.api.BlockContext} with everything MMDLib's own content is built from.
 *   <li>{@link zone.moddev.mc.lib.api.MaterialEvents} holds typed events and their callbacks;
 *       {@link zone.moddev.mc.lib.api.MaterialEventTypes} lists the built-in events and {@link
 *       zone.moddev.mc.lib.api.MaterialEventContexts} their context objects.
 *   <li>{@link zone.moddev.mc.lib.api.MaterialRegistration} publishes loaded definitions to
 *       MMDLib's definition registries.
 * </ul>
 *
 * <p>The guides in {@code docs/} (start with {@code mod-author-guide.md}) explain the data side.
 */
package zone.moddev.mc.lib.api;
