/**
 * MMDLib's implementation, in the order data flows through it: {@code codecs} and {@code modules}
 * read JSON; {@code types} are the immutable definitions; {@code builders} assemble them; {@code
 * loading} and {@code factories} load and plan materials; {@code runtime} turns plans into
 * Minecraft objects.
 */
package zone.moddev.mc.lib.common;
