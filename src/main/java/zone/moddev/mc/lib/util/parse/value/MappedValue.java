package zone.moddev.mc.lib.util.parse.value;

import java.util.function.Consumer;
import java.util.function.Function;

/** A value produced by mapping part of a JSON document. */
public interface MappedValue<T> {
  /** Wraps {@code value}. */
  static <T> MappedValue<T> of(T value) {
    return new Impl<>(value);
  }

  /** The mapped value. */
  T value();

  /** Passes the value to {@code visitor}. */
  default void handle(Consumer<T> visitor) {
    visitor.accept(value());
  }

  /** Maps the value. */
  default <R> MappedValue<R> map(Function<T, R> mapping) {
    return of(mapping.apply(value()));
  }

  /** The plain implementation returned by {@link #of}. */
  record Impl<T>(T value) implements MappedValue<T> {}
}
