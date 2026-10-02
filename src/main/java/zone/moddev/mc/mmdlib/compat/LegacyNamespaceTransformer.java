package zone.moddev.mc.mmdlib.compat;

import java.util.Map;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Relinks old consumer bytecode, including descriptors, inheritance, frames,
 * annotations and lambda handles. Shared fields, enums and registries consequently
 * have the same identity for consumers compiled against either namespace.
 */
public final class LegacyNamespaceTransformer implements IClassTransformer {
    private final Map<String, String> aliases;

    public LegacyNamespaceTransformer() { this(LegacyNamespaceAliases.load()); }

    public LegacyNamespaceTransformer(final Map<String, String> aliases) { this.aliases = aliases; }

    @Override
    public byte[] transform(final String name, final String transformedName, final byte[] bytes) {
        if (bytes == null || name == null || name.startsWith(LegacyNamespaceAliases.NEW_DOTTED)
                || aliases.containsKey(name.replace('.', '/'))
                || !(contains(bytes, LegacyNamespaceAliases.OLD_INTERNAL)
                     || contains(bytes, LegacyNamespaceAliases.OLD_DOTTED))) return bytes;
        ClassWriter output = new ClassWriter(0);
        new ClassReader(bytes).accept(new ClassRemapper(output, new Remapper() {
            @Override public String map(final String internalName) {
                return aliases.getOrDefault(internalName, internalName);
            }
            @Override public Object mapValue(final Object value) {
                if (value instanceof String) {
                    return ((String) value).replace(LegacyNamespaceAliases.OLD_DOTTED, LegacyNamespaceAliases.NEW_DOTTED)
                            .replace(LegacyNamespaceAliases.OLD_INTERNAL, LegacyNamespaceAliases.NEW_INTERNAL);
                }
                return super.mapValue(value);
            }
        }), 0);
        return output.toByteArray();
    }

    private static boolean contains(final byte[] bytes, final String ascii) {
        for (int offset = 0; offset <= bytes.length - ascii.length(); offset++) {
            int index = 0;
            while (index < ascii.length() && bytes[offset + index] == ascii.charAt(index)) index++;
            if (index == ascii.length()) return true;
        }
        return false;
    }
}
