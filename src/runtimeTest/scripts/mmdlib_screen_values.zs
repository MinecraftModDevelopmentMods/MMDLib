#loader mmdlib
import mods.mmdlib.ScreenValues;

// Value providers screen XML reads as value="@mmdlib:test/...": each gets the
// screen's block entity.
ScreenValues.whole("test/x", (machine) => machine.blockPos.x);
ScreenValues.decimal("test/half", (machine) => 2.5);
ScreenValues.flag("test/placed", (machine) => machine.hasLevel);
ScreenValues.text("test/where", (machine) => "At " + machine.blockPos.x);
