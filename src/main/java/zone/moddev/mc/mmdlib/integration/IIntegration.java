package zone.moddev.mc.mmdlib.integration;

/**
 * Core of the MMDPlugin system, this interface acts as the base of all plugin code.
 * 
 * @author J. Iwanek
 * @author D. Hazelton
 *
 */
@FunctionalInterface
public interface IIntegration {

	/**
	 * This is called to properly initialize the plugin code.
	 */
	void init();
}
