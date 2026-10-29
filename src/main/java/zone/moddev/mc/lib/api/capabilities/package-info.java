/**
 * Ready-made item, fluid and energy storage for machine block entities, and the glue that exposes
 * it through NeoForge capabilities.
 *
 * <p>A block entity owns an {@link zone.moddev.mc.lib.api.capabilities.ItemStorage}, {@link
 * zone.moddev.mc.lib.api.capabilities.FluidStorage} or {@link
 * zone.moddev.mc.lib.api.capabilities.EnergyBuffer} and works on it directly. Automation sees
 * per-face views limited by each slot's {@link zone.moddev.mc.lib.api.capabilities.Role} and each
 * face's {@link zone.moddev.mc.lib.api.capabilities.IoMode}, set in a {@link
 * zone.moddev.mc.lib.api.capabilities.SideConfig}. {@link
 * zone.moddev.mc.lib.api.capabilities.MachineCapabilities} registers the views and {@link
 * zone.moddev.mc.lib.api.capabilities.Transfer} moves resources between handlers. See {@code
 * docs/capabilities.md} for a worked example.
 */
package zone.moddev.mc.lib.api.capabilities;
