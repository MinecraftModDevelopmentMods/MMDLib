package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.layout.LayoutContribution;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.ScreenLayout;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.containers.PlayerInventoryElement;
import zone.moddev.mc.lib.gui.model.containers.PlayerSlotsElement;

class LayoutContributionTest {
    private static class Footer implements Element {
        final ResourceLocation id;
        final int width, height, growth;
        final Common common;

        Footer(String id, int width, int height, int growth, Common common) {
            this.id = ResourceLocation.parse(id);
            this.width = width;
            this.height = height;
            this.growth = growth;
            this.common = common;
        }

        public Common common() {
            return common;
        }

        public int[] measure(TextMeasure text) {
            return new int[]{width, height};
        }

        public Optional<LayoutContribution> layoutContribution(
            LayoutContribution.Context context) {
            return Optional.of(new LayoutContribution() {
                Rect allocated, region;

                public List<Element> claimedRoots() {
                    return List.of(Footer.this);
                }

                public Optional<Size> footerSize() {
                    return Optional.of(new Size(width, height));
                }

                public void footer(Rect bounds) {
                    allocated = bounds;
                }

                public void finish(Panel panel) {
                    panel.shiftBelow(allocated.y(), growth);
                    panel.grow(growth);
                    panel.place(Footer.this, allocated.offset(0, growth));
                }

                public void resolveOffsets(Panel panel) {
                    region = panel.bounds(Footer.this);
                }

                public Map<ResourceLocation, Rect> regions() {
                    return Map.of(id, region);
                }
            });
        }
    }

    private Screen screen(boolean inventory, Element... elements) {
        return new Screen(ResourceLocation.parse("test:layout"), "test",
            new Text("test", Optional.empty()), Object.class.getName(),
            inventory, List.of(elements), List.of());
    }

    @Test
    void customFootersComposeInOrderAndRespectGrowthOffsetsAndRepeatedLayouts() {
        var c = Common.empty();
        var shifted = new Common(c.id(), c.value(), c.max(), c.tooltip(),
            c.tooltipShift(), c.clicks(), c.onHover(), c.hoverShows(),
            c.jeiRecipes(), 2, 3, 0);
        var first = new Footer("test:first", 80, 12, 9, shifted);
        var second = new Footer("test:second", 120, 18, 0, Common.empty());
        var screen = screen(false, first, second);
        var layout = ScreenLayout.of(screen, TextMeasure.NONE);
        assertEquals(134, layout.panel().width());
        assertEquals(2 + (134 - 80) / 2, layout.bounds(first).x());
        assertEquals(layout.bounds(first).bottom() - 3 + ScreenLayout.GAP,
            layout.bounds(second).y());
        assertEquals(layout.bounds(second).bottom() + ScreenLayout.PANEL_MARGIN,
            layout.panel().bottom());
        assertEquals(layout.bounds(first),
            layout.region(first.id).orElseThrow());
        var repeated = ScreenLayout.of(screen, TextMeasure.NONE);
        assertEquals(layout.panel(), repeated.panel());
        assertEquals(layout.bounds(first), repeated.bounds(first));
        assertEquals(layout.bounds(second), repeated.bounds(second));
    }

    @Test
    void customFooterAndImplicitInventoryCoexist() {
        var footer = new Footer("test:extra", 220, 14, 7, Common.empty());
        var model = screen(true, footer);
        var layout = ScreenLayout.of(model, TextMeasure.NONE);
        var inventory = layout.region(PlayerInventoryElement.INVENTORY)
            .orElseThrow();
        assertEquals(layout.bounds(footer).bottom() + ScreenLayout.GAP,
            inventory.y());
        assertEquals(inventory.bottom() + ScreenLayout.PANEL_MARGIN,
            layout.panel().bottom());
        for (var part : model.implicitElements())
            assertNotNull(layout.bounds(part));
    }

    @Test
    void laterPlansCanShiftInventoryWithoutApplyingTheShiftToEquipmentTwice() {
        var inventory = new PlayerInventoryElement(Common.empty());
        Element shifter = new Footer("test:shift", 20, 10, 0, Common.empty()) {
            public Optional<LayoutContribution> layoutContribution(
                LayoutContribution.Context context) {
                return Optional.of(new LayoutContribution() {
                    public void finish(Panel panel) {
                        panel.shiftBelow(0, 7);
                        panel.grow(7);
                    }
                });
            }
        };
        var model = screen(true, inventory, shifter);
        var layout = ScreenLayout.of(model, TextMeasure.NONE);
        assertEquals(layout.bounds(inventory),
            layout.region(PlayerInventoryElement.INVENTORY).orElseThrow());
        for (var part : model.implicitElements())
            assertEquals(((PlayerSlotsElement) part)
                .defaultBounds(layout.bounds(inventory)), layout.bounds(part));
    }

    @Test
    void conflictingClaimsAndRegionNamesFailExplicitly() {
        var first = new Footer("test:same", 20, 10, 0, Common.empty());
        var second = new Footer("test:same", 20, 10, 0, Common.empty());
        assertThrows(IllegalArgumentException.class, () -> ScreenLayout
            .of(screen(false, first, second), TextMeasure.NONE));
        Element conflict = new Footer("test:claim", 20, 10, 0, Common.empty()) {
            public Optional<LayoutContribution> layoutContribution(
                LayoutContribution.Context context) {
                return Optional.of(new LayoutContribution() {
                    public List<Element> claimedRoots() {
                        return List.of(first);
                    }
                });
            }
        };
        assertThrows(IllegalArgumentException.class, () -> ScreenLayout
            .of(screen(false, first, conflict), TextMeasure.NONE));
        assertThrows(IllegalArgumentException.class,
            () -> ScreenLayout.of(screen(false, conflict), TextMeasure.NONE));
    }
}
