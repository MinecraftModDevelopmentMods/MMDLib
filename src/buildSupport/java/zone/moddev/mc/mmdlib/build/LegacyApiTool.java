package zone.moddev.mc.mmdlib.build;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/** Build-only compiler API exports; no duplicated library implementation is shipped. */
public final class LegacyApiTool {
    private static final String MODERN = "zone/moddev/mc/mmdlib/";
    private static final String LEGACY = "com/mcmoddev/lib/";
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private LegacyApiTool() { }

    public static void main(final String[] args) throws IOException {
        if ("capture".equals(args[0])) {
            Map<String, Object> inventory = new TreeMap<>();
            try (JarFile jar = new JarFile(args[1])) {
                jar.stream().filter(e -> e.getName().startsWith(LEGACY) && e.getName().endsWith(".class"))
                        .forEach(e -> {
                            try (InputStream input = jar.getInputStream(e)) {
                                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                                byte[] buffer = new byte[8192];
                                int count;
                                while ((count = input.read(buffer)) >= 0) bytes.write(buffer, 0, count);
                                addApi(inventory, bytes.toByteArray());
                            }
                            catch (IOException failure) { throw new IllegalStateException(failure); }
                        });
            }
            write(Paths.get(args[2]), JSON.toJson(inventory));
        } else if ("generate".equals(args[0])) {
            generate(Paths.get(args[1]), Paths.get(args[2]), Paths.get(args[3]), Paths.get(args[4]));
        } else {
            throw new IllegalArgumentException("Expected capture or generate");
        }
    }

    private static void generate(final Path input, final Path output, final Path resources,
                                 final Path baseline) throws IOException {
        Map<String, Object> inventory = new TreeMap<>();
        List<String> aliases = new ArrayList<>();
        List<Path> classes;
        try (Stream<Path> paths = Files.walk(input)) {
            classes = paths.filter(p -> p.toString().endsWith(".class")).sorted().collect(Collectors.toList());
        }
        for (Path file : classes) {
            ClassReader source = new ClassReader(Files.readAllBytes(file));
            String name = source.getClassName();
            if (!name.startsWith(MODERN) || name.startsWith(MODERN + "compat/")) continue;
            String legacyName = LEGACY + name.substring(MODERN.length());
            ClassWriter target = new ClassWriter(0);
            source.accept(new ClassRemapper(new ExportVisitor(target), new Remapper() {
                @Override public String map(final String type) {
                    return type.startsWith(MODERN) ? LEGACY + type.substring(MODERN.length()) : type;
                }
                @Override public Object mapValue(final Object value) {
                    if (value instanceof String) return ((String) value).replace(MODERN, LEGACY)
                            .replace(MODERN.replace('/', '.'), LEGACY.replace('/', '.'));
                    return super.mapValue(value);
                }
            }), ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            byte[] bytes = target.toByteArray();
            Path destination = output.resolve(legacyName + ".class");
            Files.createDirectories(destination.getParent());
            Files.write(destination, bytes);
            addApi(inventory, bytes);
            aliases.add(legacyName + "=" + name);
        }
        JsonParser parser = new JsonParser();
        String expected = parser.parse(new String(Files.readAllBytes(baseline), StandardCharsets.UTF_8)).toString();
        if (!parser.parse(JSON.toJson(inventory)).toString().equals(expected)) {
            write(output.resolve("actual-api.json"), JSON.toJson(inventory));
            throw new IllegalStateException("Legacy API signatures changed; compare actual-api.json with " + baseline);
        }
        java.util.Collections.sort(aliases);
        write(resources.resolve("META-INF/mmdlib-legacy-names.list"), String.join("\n", aliases) + "\n");
        System.out.println("Verified " + aliases.size() + " legacy class aliases and all public/protected API signatures");
    }

    private static final class ExportVisitor extends ClassVisitor {
        private boolean annotation;
        private boolean reflectiveFactory;
        private String owner;
        private String modernOwner;
        ExportVisitor(final ClassVisitor target) { super(Opcodes.ASM5, target); }
        @Override public void visit(final int version, final int access, final String name, final String signature,
                                    final String parent, final String[] interfaces) {
            annotation = (access & Opcodes.ACC_ANNOTATION) != 0;
            owner = name;
            modernOwner = MODERN + name.substring(LEGACY.length());
            reflectiveFactory = "java/lang/Object".equals(parent)
                    && (access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT)) == 0
                    && Arrays.stream(interfaces).anyMatch(i -> i.startsWith("net/minecraftforge/common/crafting/I")
                            && i.endsWith("Factory"));
            super.visit(version, access, name, signature, parent, interfaces);
            if (reflectiveFactory) super.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                    "$modernDelegate", "L" + modernOwner + ";", null, null).visitEnd();
        }
        @Override public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
            // Annotation declarations need Retention/Target for old source consumers.
            // Export classes must never become a second mod, config or event subscriber.
            return annotation ? super.visitAnnotation(descriptor, visible) : null;
        }
        @Override public FieldVisitor visitField(final int access, final String name, final String descriptor,
                                                final String signature, final Object value) {
            return new FieldVisitor(Opcodes.ASM5, super.visitField(access, name, descriptor, signature, value)) {
                @Override public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) { return null; }
            };
        }
        @Override public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                                                  final String signature, final String[] exceptions) {
            MethodVisitor target = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new MethodVisitor(Opcodes.ASM5, target) {
                @Override public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) { return null; }
                @Override public void visitEnd() {
                    if ((access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) {
                        target.visitCode();
                        if (reflectiveFactory && "<init>".equals(name) && "()V".equals(descriptor)) {
                            target.visitVarInsn(Opcodes.ALOAD, 0);
                            target.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                            target.visitVarInsn(Opcodes.ALOAD, 0);
                            target.visitTypeInsn(Opcodes.NEW, modernOwner);
                            target.visitInsn(Opcodes.DUP);
                            target.visitMethodInsn(Opcodes.INVOKESPECIAL, modernOwner, "<init>", "()V", false);
                            target.visitFieldInsn(Opcodes.PUTFIELD, owner, "$modernDelegate", "L" + modernOwner + ";");
                            target.visitInsn(Opcodes.RETURN);
                        } else if (reflectiveFactory && (access & Opcodes.ACC_PUBLIC) != 0
                                && (access & Opcodes.ACC_STATIC) == 0 && !descriptor.contains(LEGACY)) {
                            target.visitVarInsn(Opcodes.ALOAD, 0);
                            target.visitFieldInsn(Opcodes.GETFIELD, owner, "$modernDelegate", "L" + modernOwner + ";");
                            int slot = 1;
                            for (Type type : Type.getArgumentTypes(descriptor)) {
                                target.visitVarInsn(type.getOpcode(Opcodes.ILOAD), slot);
                                slot += type.getSize();
                            }
                            target.visitMethodInsn(Opcodes.INVOKEVIRTUAL, modernOwner, name, descriptor, false);
                            target.visitInsn(Type.getReturnType(descriptor).getOpcode(Opcodes.IRETURN));
                        } else {
                            target.visitTypeInsn(Opcodes.NEW, "java/lang/UnsupportedOperationException");
                            target.visitInsn(Opcodes.DUP);
                            target.visitLdcInsn("MMDLib legacy API requires the Forge namespace compatibility adapter");
                            target.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/UnsupportedOperationException", "<init>", "(Ljava/lang/String;)V", false);
                            target.visitInsn(Opcodes.ATHROW);
                        }
                        int locals = (access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
                        for (Type type : Type.getArgumentTypes(descriptor)) locals += type.getSize();
                        target.visitMaxs(Math.max(3, locals + 1), locals);
                    }
                    target.visitEnd();
                }
            };
        }
    }

    private static void addApi(final Map<String, Object> inventory, final byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        Map<String, Object> metadata = new LinkedHashMap<>();
        Map<String, Object> fields = new TreeMap<>();
        Map<String, Object> methods = new TreeMap<>();
        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override public void visit(final int version, final int access, final String name, final String signature,
                                        final String parent, final String[] interfaces) {
                metadata.put("access", access); metadata.put("super", parent);
                metadata.put("interfaces", Arrays.asList(interfaces)); metadata.put("signature", signature);
            }
            @Override public FieldVisitor visitField(final int access, final String name, final String descriptor,
                                                    final String signature, final Object value) {
                if ((access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0)
                    fields.put(name + ":" + descriptor, Arrays.asList(access, signature, value));
                return null;
            }
            @Override public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                                                      final String signature, final String[] exceptions) {
                if ((access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0)
                    methods.put(name + descriptor, Arrays.asList(access, signature,
                            exceptions == null ? java.util.Collections.emptyList() : Arrays.asList(exceptions)));
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        metadata.put("fields", fields); metadata.put("methods", methods);
        inventory.put(reader.getClassName(), metadata);
    }

    private static void write(final Path destination, final String value) throws IOException {
        Files.createDirectories(destination.toAbsolutePath().getParent());
        Files.write(destination, value.getBytes(StandardCharsets.UTF_8));
    }
}
