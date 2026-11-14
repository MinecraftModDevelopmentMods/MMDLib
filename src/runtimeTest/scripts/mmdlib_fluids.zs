#loader mmdlib
// A standalone fluid built in a script: the plain module with its own colour,
// flow and name.
import mods.mmdlib.FluidBuilder;

var chocolate = new FluidBuilder("test/chocolate");
chocolate.tint = 0xFF5C3A21;
chocolate.viscosity = 3000;
chocolate.canSwim = false;
chocolate.tickRate = 15;
chocolate.displayName = "Liquid Chocolate";
chocolate.build();

// From the molten module, glowing, with no block: a bucket-only fluid.
var plasma = new FluidBuilder("test/plasma", "mmdlib:molten");
plasma.lightLevel = 15;
plasma.hasBlock = false;
plasma.build();
