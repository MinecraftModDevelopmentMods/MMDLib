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

import zone.moddev.mc.lib.gui.load.BoundValue.Kind;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Element.*;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.ValueRef;

/**
 * Resolves a screen's {@code value} and {@code max} getters against its block-entity class at load,
 * from the class and method name alone; no block entity exists yet. Each getter must be public and
 * return a kind of value the element can show.
 *
 * <ul>
 *   <li>{@code #method}: a no-argument instance method of the block-entity class, a superclass or
 *       an interface.
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
      if (c.value().isPresent())
        values.put(
            element,
            resolve(c.value().get(), owner, loader, source, c.line(), valueKinds(element)));
      if (c.max().isPresent())
        maxima.put(element, resolve(c.max().get(), owner, loader, source, c.line(), NUMBERS));
    }
    return new Bound(screen, owner, values, maxima);
  }

  private static final Set<Kind> NUMBERS = Set.of(Kind.WHOLE, Kind.DECIMAL, Kind.NUMBER);

  /** What each element's {@code value} may return. */
  private static Set<Kind> valueKinds(Element element) {
    return switch (element) {
      case Gauge gauge ->
          gauge.color().isPresent()
              ? NUMBERS
              : Set.of(Kind.WHOLE, Kind.DECIMAL, Kind.NUMBER, Kind.FLUID);
      case Meter meter -> NUMBERS;
      case Progress progress -> NUMBERS;
      case Checkbox checkbox -> Set.of(Kind.BOOLEAN);
      case RadioGroup group -> Set.of(Kind.WHOLE);
      case Popout popout -> Set.of(Kind.TEXT, Kind.TEXT_LIST);
      // Anything else only feeds {value} into its tooltip.
      default -> Set.of(Kind.values());
    };
  }

  private static BoundValue resolve(
      ValueRef ref,
      Class<?> owner,
      ClassLoader loader,
      String source,
      int line,
      Set<Kind> allowed) {
    Method method =
        switch (ref) {
          case ValueRef.Instance instance -> instanceMethod(owner, instance, source, line);
          case ValueRef.Static fixed -> staticMethod(owner, fixed, loader, source, line);
        };
    var kind = Kind.of(method.getReturnType());
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

  private static String describe(Set<Kind> kinds) {
    if (kinds.size() == Kind.values().length) return "a number, boolean, FluidStack or Component";
    if (kinds.equals(NUMBERS)) return "a number";
    if (kinds.equals(Set.of(Kind.BOOLEAN))) return "a boolean";
    if (kinds.equals(Set.of(Kind.WHOLE))) return "a whole number";
    if (kinds.contains(Kind.FLUID)) return "a number or a FluidStack";
    if (kinds.contains(Kind.TEXT)) return "a Component or a list of Components";
    return "a number, boolean, FluidStack or Component";
  }
}
