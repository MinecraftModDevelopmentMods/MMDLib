package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.client.DefaultElementBehavior;
import zone.moddev.mc.lib.gui.client.ElementBehaviors;
import zone.moddev.mc.lib.gui.client.ElementClient;
import zone.moddev.mc.lib.gui.client.ElementRenderers;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.load.BuiltinElements;
import zone.moddev.mc.lib.gui.load.ScreenAttribute;
import zone.moddev.mc.lib.gui.load.ScreenException;
import zone.moddev.mc.lib.gui.load.ScreenReader;
import zone.moddev.mc.lib.gui.load.ScreenTag;
import zone.moddev.mc.lib.gui.load.ScreenTags;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Screen;

class ElementRegistrationTest {
    interface Family extends Element {
    }

    record Custom(Common common, int width) implements Family {
        public int[] measure(TextMeasure text) {
            return new int[]{width, 10};
        }
    }

    private static final QName NAME = new QName("https://example.test/ui", "custom");

    private static ElementType<Custom> declaration() {
        return ElementType.xml(Custom.class, NAME, x -> new Custom(x.common(), x.integer("width")))
            .attribute("width", ScreenAttribute.defaulted("xs:positiveInteger", "23"))
            .build();
    }

    @Test
    void commonDeclarationBuildsRealXmlWithDefaultsAndSourceDiagnostics() throws Exception {
        var tags = ScreenTags.builtins();
        declaration().register(tags);
        var screen = read(tags, "<mod:custom id=\"mine\"/>");
        assertEquals(23, ((Custom) screen.find("mine").orElseThrow()).width());
        var failure = assertThrows(ScreenException.class,
            () -> read(tags, "<mod:custom width=\"bad\"/>"));
        assertTrue(failure.getMessage().contains("declaration.xml"));
        assertThrows(ScreenException.class, () -> read(tags, "<mod:custom unexpected=\"1\"/>"));
    }

    private Screen read(ScreenTags tags, String body) throws Exception {
        String xml = "<screen xmlns=\""
            + ScreenReader.NAMESPACE
            + "\" xmlns:mod=\""
            + NAME.getNamespaceURI()
            + "\" title=\"test\" block-entity=\""
            + ScreenLoaderTest.Press.class.getName()
            + "\">"
            + body
            + "</screen>";
        return ScreenReader.read(
            ResourceLocation.parse("test:declaration"),
            "declaration.xml",
            new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
            new WidgetTypes(),
            tags);
    }

    @Test
    void aliasesAreImmutableAndUseExistingDuplicateAndFreezeRules() {
        var original = declaration();
        var alias = new QName(NAME.getNamespaceURI(), "alias");
        var tag = ScreenTag.element(Custom.class, x -> new Custom(x.common(), 5)).build();
        var extended = original.withXml(alias, tag);
        var first = new ScreenTags();
        original.register(first);
        assertThrows(IllegalArgumentException.class, () -> first.require(alias));
        var second = ScreenTags.builtins();
        extended.register(second);
        assertSame(tag, second.require(alias));
        assertThrows(IllegalArgumentException.class, () -> original.register(second));
        assertThrows(IllegalArgumentException.class, () -> extended.withXml(alias, tag));
        second.freeze();
        assertThrows(
            IllegalStateException.class,
            () -> ElementType.xml(new QName(NAME.getNamespaceURI(), "late"), tag).register(second));
        assertThrows(
            IllegalArgumentException.class,
            () -> original.withXml(
                alias, ScreenTag.data(Custom.class, x -> new Custom(x.common(), 1)).build()));
    }

    @Test
    void optionalAttachmentsPreservePerPassAndBehaviorInheritance() {
        var renderers = new ElementRenderers();
        var behaviors = new ElementBehaviors();
        var seen = new ArrayList<String>();
        ElementClient.of(ElementType.of(Family.class))
            .renderer((e, c) -> seen.add("base"))
            .overlay((e, c) -> seen.add("overlay"))
            .foreground((e, c) -> seen.add("foreground"))
            .top((e, c) -> seen.add("top"))
            .behavior((e, a) -> new ElementBehaviorsTest.State())
            .register(renderers, behaviors);
        // An XML-only declaration must not shadow any inherited client registration.
        ElementClient.of(declaration()).register(renderers, behaviors);
        var element = new Custom(Common.empty(), 23);
        var context = new ElementRenderers.Context(null, element, new Rect(0, 0, 23, 10), null,
            null, 0, 0, 0);
        for (var pass : ElementRenderers.Pass.values())
            renderers.require(pass, Custom.class).accept(context);
        assertEquals(List.of("base", "overlay", "foreground", "top"), seen);
        var access = new ElementBehaviorsTest.Access(element);
        assertInstanceOf(ElementBehaviorsTest.State.class, behaviors.create(access));
        assertNotSame(behaviors.create(access), behaviors.create(access));

        ElementClient.of(declaration())
            .overlay((e, c) -> seen.add("custom"))
            .register(renderers, behaviors);
        renderers.require(ElementRenderers.Pass.OVERLAY, Custom.class).accept(context);
        assertEquals("custom", seen.getLast());
        assertSame(renderers.require(Family.class), renderers.require(Custom.class));
        assertInstanceOf(ElementBehaviorsTest.State.class, behaviors.create(access));
    }

    @Test
    void behaviorOnlyAttachmentKeepsDefaultRendererAndExactFactoryWins() {
        var renderers = new ElementRenderers();
        var behaviors = new ElementBehaviors();
        ElementClient.of(ElementType.of(Family.class))
            .renderer((e, c) -> {
            })
            .behavior((e, a) -> new ElementBehaviorsTest.State())
            .register(renderers, behaviors);
        ElementClient.of(declaration())
            .behavior((e, a) -> new DefaultElementBehavior(a))
            .register(renderers, behaviors);
        assertSame(renderers.require(Family.class), renderers.require(Custom.class));
        assertEquals(
            DefaultElementBehavior.class,
            behaviors
                .create(new ElementBehaviorsTest.Access(new Custom(Common.empty(), 10)))
                .getClass());
    }

    @Test
    void duplicateAttachmentsFailAndRendererValidationIsRetained() {
        var renderers = new ElementRenderers();
        var behaviors = new ElementBehaviors();
        var client = ElementClient.of(declaration())
            .renderer(
                new ElementRenderers.Renderer<>() {
                    public void render(Custom element, ElementRenderers.Context context) {
                    }

                    public void validate(Custom element) {
                        throw new IllegalArgumentException("custom dependency missing");
                    }
                })
            .behavior((e, a) -> new DefaultElementBehavior(a));
        assertThrows(IllegalArgumentException.class, () -> client.renderer((e, c) -> {
        }));
        assertThrows(
            IllegalArgumentException.class,
            () -> client.behavior((e, a) -> new DefaultElementBehavior(a)));
        client.register(renderers, behaviors);
        var failure = assertThrows(
            ScreenException.class,
            () -> renderers.validate(List.of(new Custom(Common.empty(), 10)), e -> "custom.xml:4"));
        assertTrue(failure.getMessage().contains("custom dependency missing"));
        assertTrue(failure.getMessage().contains("custom.xml:4"));
        assertThrows(IllegalArgumentException.class, () -> client.registerRenderers(renderers));
        assertThrows(IllegalArgumentException.class, () -> client.registerBehaviors(behaviors));
    }

    @Test
    void builtinAliasesAndLegacyPolymorphicGaugeStillConstructTheirOriginalTypes()
        throws Exception {
        var tags = ScreenTags.builtins();
        var screen = read(
            tags,
            "<hbox><gauge length=\"36\" tank=\"0\"/><fluid-tank length=\"36\" tank=\"0\"/>"
                + "<gauge length=\"36\" value=\"#getProgress\" max=\"#getTime\"/></hbox>");
        var children = screen.body().getFirst().children();
        assertInstanceOf(zone.moddev.mc.lib.gui.model.elements.FluidTankElement.class,
            children.get(0));
        assertInstanceOf(zone.moddev.mc.lib.gui.model.elements.FluidTankElement.class,
            children.get(1));
        assertInstanceOf(zone.moddev.mc.lib.gui.model.elements.GaugeElement.class, children.get(2));
        assertEquals(
            BuiltinElements.SLOT.type(), tags.require(ScreenTags.name("item-slot")).resultType());
    }
}
