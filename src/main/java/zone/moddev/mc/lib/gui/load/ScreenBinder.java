package zone.moddev.mc.lib.gui.load;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import zone.moddev.mc.lib.gui.WidgetTypes;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.ValueRef;
import zone.moddev.mc.lib.gui.value.ValueType;
import zone.moddev.mc.lib.gui.value.ValueTypeRegistry;

/**
 * Resolves a screen's {@code value} and {@code max} getters against its block-entity class at load,
 * from the class and method name alone; no block entity exists yet. Each getter must be public and
 * return a kind of value the element can show.
 *
 * <ul>
 *   <li>{@code #method}: a no-argument instance method of the block-entity class, a superclass or
 *       an interface.
 *   <li>{@code #method(index)}: an instance getter taking one fixed nonnegative integer.
 *   <li>{@code Class#method}: a static method taking one parameter the block entity can be passed
 *       as.
 * </ul>
 */
public final class ScreenBinder {
  private static final String BLOCK_ENTITY = "net.minecraft.world.level.block.entity.BlockEntity";

  private ScreenBinder() {}

  /**
   * A screen with its getters resolved.
   *
   * @param screen the screen
   * @param blockEntityClass the class every getter accepts
   * @param values each element's {@code value} getter, by element identity
   * @param maxima each element's {@code max} getter, by element identity
   */
  public record Bound(
      Screen screen,
      Class<?> blockEntityClass,
      Map<Element, BoundValue> values,
      Map<Element, BoundValue> maxima) {}

  /**
   * Resolves every getter of {@code screen}.
   *
   * @param loader the class loader of the mod that owns the screen file
   * @throws ScreenException naming the file and line, if a class or method is missing, is not
   *     public, has the wrong shape, or returns a kind the element cannot show
   */
  public static Bound bind(Screen screen, ClassLoader loader) {
    return bind(screen, loader, WidgetTypes.GLOBAL);
  }

  /** Binds a screen read with an explicit widget registry. */
  public static Bound bind(Screen screen, ClassLoader loader, WidgetTypes widgets) {
    return bind(screen, loader, widgets, ValueTypeRegistry.GLOBAL);
  }

  /** Binds using explicit registries, useful for isolated loaders and extension tests. */
  public static Bound bind(
      Screen screen, ClassLoader loader, WidgetTypes widgets, ValueTypeRegistry types) {
    ScreenValidation.check(screen, widgets);
    var source = screen.source();
    Class<?> owner = load(screen.blockEntity(), loader, source, 0, "block-entity class");
    Class<?> base =
        load(BLOCK_ENTITY, ScreenBinder.class.getClassLoader(), source, 0, "BlockEntity");
    if (!base.isAssignableFrom(owner))
      throw new ScreenException(source, 0, screen.blockEntity() + " is not a BlockEntity");
    var values = new IdentityHashMap<Element, BoundValue>();
    var maxima = new IdentityHashMap<Element, BoundValue>();
    for (var element : screen.elements()) {
      var c = element.common();
      var elementSource = screen.source(c);
      if (element.resourceBinding().isPresent()
          && !zone.moddev.mc.lib.gui.runtime.ScreenHost.class.isAssignableFrom(owner))
        throw new ScreenException(
            elementSource, c.line(), "Resource-bound elements require a ScreenHost");
      try {
        element.validateHostType(owner);
      } catch (IllegalArgumentException failure) {
        throw new ScreenException(elementSource, c.line(), failure.getMessage());
      }
      if (c.value().isPresent())
        values.put(
            element,
            resolve(
                c.value().get(),
                owner,
                loader,
                elementSource,
                c.line(),
                element.valueKinds(widgets),
                types));
      if (c.max().isPresent())
        maxima.put(
            element,
            resolve(
                c.max().get(),
                owner,
                loader,
                elementSource,
                c.line(),
                element.maximumKinds(),
                types));
    }
    return new Bound(screen, owner, values, maxima);
  }

  private static BoundValue resolve(
      ValueRef ref,
      Class<?> owner,
      ClassLoader loader,
      String source,
      int line,
      Set<ValueType<?>> allowed,
      ValueTypeRegistry types) {
    Method method =
        switch (ref) {
          case ValueRef.Instance instance -> instanceMethod(owner, instance, source, line);
          case ValueRef.Indexed indexed -> indexedMethod(owner, indexed, source, line);
          case ValueRef.Static fixed -> staticMethod(owner, fixed, loader, source, line);
        };
    ValueType<?> kind;
    try {
      kind = types.binding(method.getReturnType()).orElse(null);
    } catch (IllegalArgumentException failure) {
      throw new ScreenException(source, line, failure.getMessage(), failure);
    }
    if (kind == null || !allowed.contains(kind))
      throw new ScreenException(
          source,
          line,
          ref.text()
              + " returns "
              + method.getReturnType().getName()
              + "; this element needs "
              + describe(allowed));
    try {
      MethodHandle handle = MethodHandles.publicLookup().unreflect(method);
      if (ref instanceof ValueRef.Indexed indexed)
        handle = MethodHandles.insertArguments(handle, 1, indexed.index());
      return new BoundValue(
          ref, kind, handle.asType(MethodType.methodType(Object.class, Object.class)));
    } catch (IllegalAccessException ex) {
      throw new ScreenException(source, line, ref.text() + " is not accessible", ex);
    }
  }

  private static Method instanceMethod(
      Class<?> owner, ValueRef.Instance ref, String source, int line) {
    try {
      var method = owner.getMethod(ref.method());
      if (Modifier.isStatic(method.getModifiers()))
        throw new ScreenException(
            source, line, ref.text() + " is static; use Class#method for static getters");
      return method;
    } catch (NoSuchMethodException ex) {
      throw new ScreenException(
          source,
          line,
          "No public no-argument method " + ref.method() + "() on " + owner.getName(),
          ex);
    }
  }

  private static Method indexedMethod(
      Class<?> owner, ValueRef.Indexed ref, String source, int line) {
    try {
      var method = owner.getMethod(ref.method(), int.class);
      if (Modifier.isStatic(method.getModifiers()))
        throw new ScreenException(source, line, ref.text() + " must be an instance getter");
      return method;
    } catch (NoSuchMethodException ex) {
      throw new ScreenException(
          source,
          line,
          "No public indexed getter " + ref.method() + "(int) on " + owner.getName(),
          ex);
    }
  }

  private static Method staticMethod(
      Class<?> owner, ValueRef.Static ref, ClassLoader loader, String source, int line) {
    var holder = load(ref.owner(), loader, source, line, "class");
    var matches = new ArrayList<Method>();
    for (var method : holder.getMethods())
      if (method.getName().equals(ref.method())
          && Modifier.isStatic(method.getModifiers())
          && method.getParameterCount() == 1
          && method.getParameterTypes()[0].isAssignableFrom(owner)) matches.add(method);
    if (matches.isEmpty())
      throw new ScreenException(
          source,
          line,
          "No public static method "
              + ref.method()
              + "("
              + owner.getSimpleName()
              + ") on "
              + holder.getName());
    if (matches.size() > 1)
      throw new ScreenException(
          source, line, ref.text() + " is ambiguous: several overloads accept " + owner.getName());
    return matches.getFirst();
  }

  private static Class<?> load(
      String name, ClassLoader loader, String source, int line, String what) {
    try {
      return Class.forName(name, false, loader);
    } catch (ClassNotFoundException | LinkageError ex) {
      throw new ScreenException(source, line, "Cannot find " + what + " " + name, ex);
    }
  }

  private static String describe(Set<ValueType<?>> kinds) {
    if (kinds.isEmpty()) return "no value binding";
    return kinds.stream()
        .sorted(java.util.Comparator.comparing(ValueType::id))
        .map(ValueType::description)
        .collect(java.util.stream.Collectors.joining(" or "));
  }
}
