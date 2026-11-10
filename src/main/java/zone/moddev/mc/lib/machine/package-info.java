/**
 * The machine module: internals (parts in sockets that change a machine's
 * stats), machine ingredients across items, fluids and energy, slot groups with
 * transactions, and recipe matching.
 *
 * <p>
 * Everything here except {@code machine.neoforge} imports only vanilla,
 * DataFixerUpper and Gson, so it can be shared by every loader build;
 * {@code verifyMachineImports} enforces that. {@code
 * machine.neoforge} adapts MMDLib's storages and registers the module's game
 * objects.
 */
package zone.moddev.mc.lib.machine;
