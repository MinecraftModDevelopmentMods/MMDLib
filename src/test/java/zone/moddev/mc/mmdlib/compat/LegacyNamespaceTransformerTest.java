package zone.moddev.mc.mmdlib.compat;

import java.util.Map;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

class LegacyNamespaceTransformerTest {
    @Test void exportsCoverTheFrozenApiWithoutCompatibilityImplementationAliases() {
        Map<String, String> aliases = LegacyNamespaceAliases.load();
        assertEquals(500, aliases.size());
        assertEquals("zone/moddev/mc/mmdlib/data/Names", aliases.get("com/mcmoddev/lib/data/Names"));
        assertTrue(aliases.values().stream().noneMatch(n -> n.contains("/compat/")));
        assertThrows(UnsupportedOperationException.class, () -> aliases.clear());
    }

    @Test void ordinaryClassesAndCanonicalMetadataRemainUntouched() {
        LegacyNamespaceTransformer transformer = new LegacyNamespaceTransformer();
        byte[] unrelated = fixture(false);
        assertSame(unrelated, transformer.transform("another.mod.Consumer", "another.mod.Consumer", unrelated));
        byte[] oldReferences = fixture(true);
        assertSame(oldReferences, transformer.transform("zone.moddev.mc.mmdlib.compat.Metadata", null, oldReferences));
        assertNull(transformer.transform("missing", "missing", null));
    }

    @Test void compilerExportsCannotRegisterDuplicateModsOrSubscribers() throws Exception {
        int factoryForwarders = 0;
        for (String alias : LegacyNamespaceAliases.load().keySet()) {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(alias + ".class")) {
                assertNotNull(input, alias);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) bytes.write(buffer, 0, count);
                ClassNode node = new ClassNode();
                new ClassReader(bytes.toByteArray()).accept(node, 0);
                if ((node.access & Opcodes.ACC_ANNOTATION) == 0) {
                    assertTrue(node.visibleAnnotations == null || node.visibleAnnotations.isEmpty(), alias);
                    assertTrue(node.invisibleAnnotations == null || node.invisibleAnnotations.isEmpty(), alias);
                }
                for (Object field : node.fields) {
                    FieldNode metadata = (FieldNode) field;
                    assertTrue(metadata.visibleAnnotations == null || metadata.visibleAnnotations.isEmpty(), alias);
                    assertTrue(metadata.invisibleAnnotations == null || metadata.invisibleAnnotations.isEmpty(), alias);
                    if (metadata.name.equals("$modernDelegate")) factoryForwarders++;
                }
                for (Object method : node.methods) {
                    MethodNode metadata = (MethodNode) method;
                    assertTrue(metadata.visibleAnnotations == null || metadata.visibleAnnotations.isEmpty(), alias);
                    assertTrue(metadata.invisibleAnnotations == null || metadata.invisibleAnnotations.isEmpty(), alias);
                }
            }
        }
        assertEquals(5, factoryForwarders);
        assertArrayEquals(new String[] { "zone.moddev.mc.mmdlib.compat.LegacyNamespaceTransformer" },
                new LegacyNamespaceBootstrap().getASMTransformerClass());
    }

    @Test void consumerInheritanceDescriptorsClassLiteralsAndReflectionNamesAreRelinked() {
        byte[] migrated = new LegacyNamespaceTransformer().transform("another.mod.Consumer", "another.mod.Consumer", fixture(true));
        ClassNode node = new ClassNode();
        new ClassReader(migrated).accept(node, 0);
        assertEquals("another/mod/Consumer", node.name);
        assertEquals("zone/moddev/mc/mmdlib/integration/IntegrationInitEvent", node.superName);
        assertEquals("Lzone/moddev/mc/mmdlib/data/Names;", ((FieldNode) node.fields.get(0)).desc);
        MethodNode method = (MethodNode) node.methods.get(0);
        assertEquals("(Lzone/moddev/mc/mmdlib/data/Names;)V", method.desc);
        assertEquals(Type.getObjectType("zone/moddev/mc/mmdlib/data/NameToken"), ((LdcInsnNode) method.instructions.get(0)).cst);
        assertEquals("zone.moddev.mc.mmdlib.data.Names", ((LdcInsnNode) method.instructions.get(2)).cst);
    }

    @Test void addonClassesInsideTheOldPackageStillRelinkTheirApiReferences() {
        ClassWriter addon = new ClassWriter(0);
        addon.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "com/mcmoddev/lib/addon/Consumer", null,
                "com/mcmoddev/lib/integration/IntegrationInitEvent", null);
        addon.visitEnd();
        ClassNode node = new ClassNode();
        new ClassReader(new LegacyNamespaceTransformer().transform("com.mcmoddev.lib.addon.Consumer", null,
                addon.toByteArray())).accept(node, 0);
        assertEquals("com/mcmoddev/lib/addon/Consumer", node.name);
        assertEquals("zone/moddev/mc/mmdlib/integration/IntegrationInitEvent", node.superName);
    }

    private static byte[] fixture(final boolean legacy) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "another/mod/Consumer", null,
                legacy ? "com/mcmoddev/lib/integration/IntegrationInitEvent" : "java/lang/Object", null);
        if (legacy) writer.visitField(Opcodes.ACC_PUBLIC, "token", "Lcom/mcmoddev/lib/data/Names;", null, null).visitEnd();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "consume",
                legacy ? "(Lcom/mcmoddev/lib/data/Names;)V" : "()V", null, null);
        method.visitCode();
        if (legacy) {
            method.visitLdcInsn(Type.getObjectType("com/mcmoddev/lib/data/NameToken"));
            method.visitInsn(Opcodes.POP);
            method.visitLdcInsn("com.mcmoddev.lib.data.Names");
            method.visitInsn(Opcodes.POP);
        }
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, legacy ? 1 : 0);
        method.visitEnd(); writer.visitEnd();
        return writer.toByteArray();
    }
}
