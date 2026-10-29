package zone.moddev.mc.lib.util.parse.function;

import java.util.function.Function;

import zone.moddev.mc.lib.util.parse.value.MappedArrayValue;

/** Maps a whole array of already-mapped items. */
@FunctionalInterface
public interface MappedArrayValueFunction<V, T> extends Function<MappedArrayValue<V>, T> {}
