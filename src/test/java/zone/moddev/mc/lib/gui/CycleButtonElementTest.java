package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;

import java.nio.charset.StandardCharsets;

import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.load.ScreenReader;
import zone.moddev.mc.lib.gui.model.elements.CycleButtonElement;
import zone.moddev.mc.lib.machine.feature.Processor;
import zone.moddev.mc.lib.machine.feature.ProcessorAction;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;

class CycleButtonElementTest {
    private static final String XML = """
        <cycle-button id="mode" variable="$processors.test.mode" tooltip="test.cycle">
          <cycle-mode value="7" icon="test:first" label="test.first" tooltip="test.first.help" action="test:first"/>
          <cycle-mode value="2" icon="test:second" label="test.second" tooltip="test.second.help" action="test:second"/>
          <cycle-mode value="9" icon="test:last" label="test.last" action="test:last"/>
        </cycle-button>
        """;

    private CycleButtonElement read(String body) throws Exception {
        String xml = "<screen xmlns=\"https://moddev.zone/mmdlib/screen\" title=\"test\" block-entity=\""
            + ElementInteractionTest.Host.class.getName() + "\">" + body
            + "</screen>";
        return (CycleButtonElement) ScreenReader
            .read(ResourceLocation.parse("test:cycle"), "cycle.xml",
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
            .find("mode").orElseThrow();
    }

    @Test
    void xmlOrderWrapsNonContiguousValuesAndSelectsPresentation()
        throws Exception {
        var button = read(XML);
        assertArrayEquals(new int[]{18, 18}, button.measure(text -> 0));
        assertEquals(2, button.nextMode(7).value());
        assertEquals(9, button.nextMode(2).value());
        assertEquals(7, button.nextMode(9).value());
        assertEquals(7, button.nextMode(-1).value());
        assertEquals(ResourceLocation.parse("test:second"),
            button.iconFor(2).orElseThrow());
        assertEquals("test.second", button.labelFor(2).orElseThrow().key());
        assertEquals("test.second.help",
            button.modeFor(2).orElseThrow().tooltip().orElseThrow().key());
        assertTrue(button.modeFor(.5).isEmpty());
        assertTrue(button.modeFor("2").isEmpty());
        assertTrue(button.modeFor(java.math.BigInteger.ONE.shiftLeft(64)
            .add(java.math.BigInteger.TWO)).isEmpty());
        assertEquals(button, read(XML));
    }

    @Test
    void rejectsAmbiguousModesAndCompetingActions() {
        for (String invalid : new String[]{
            XML.replace("value=\"2\"", "value=\"7\""),
            XML.replace("action=\"test:first\"", ""),
            XML.replace("tooltip=\"test.cycle\"", ""),
            XML.replace("id=\"mode\"", "id=\"mode\" on-click=\"test:click\""),
            XML.replace("id=\"mode\"", "id=\"mode\" value=\"#mode\""),
            XML.replace("$processors.test.mode", "invalid")})
            assertThrows(Exception.class, () -> read(invalid));
    }

    @Test
    void validatesSyncedVariableDefaultAndDeclaredActions() throws Exception {
        var fixture = new Fixture();
        Map<String, Processor<?>> processors = Map.of("test", fixture);
        read(XML).validateDefinition(processors);
        for (String invalid : new String[]{
            XML.replace("test.mode", "test.hidden"),
            XML.replace("test.mode", "missing.mode"),
            XML.replace("action=\"test:last\"", "action=\"test:missing\""),
            XML.replace("value=\"7\"", "value=\"8\"")}) {
            var button = read(invalid);
            assertThrows(IllegalArgumentException.class,
                () -> button.validateDefinition(processors));
        }
    }

    private static class Fixture extends StoredProcessor {
        final ProcessorState.Key<Integer> mode = variables
            .integer("mode", 0, 9, 7).synced().define();
        final ProcessorState.Key<Integer> hidden = variables
            .integer("hidden", 0, 9, 7).define();
        @Override
        public Map<ResourceLocation, ProcessorAction> actions() {
            var action = new ProcessorAction((state, context) -> true,
                (state, context) -> ProcessorAction.Result.success());
            return Map.of(ResourceLocation.parse("test:first"), action,
                ResourceLocation.parse("test:second"), action,
                ResourceLocation.parse("test:last"), action);
        }
        public boolean canStart(ProcessorState state,
            ProcessorMachine context) {
            return false;
        }
        public ProcessState process(ProcessorState state,
            ProcessorMachine context, int ticks) {
            return ProcessState.IDLE;
        }
    }
}
