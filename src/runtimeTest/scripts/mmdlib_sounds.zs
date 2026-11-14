#loader mmdlib
import mods.mmdlib.SoundBuilder;
import mods.mmdlib.SoundTypeBuilder;
import mods.mmdlib.MaterialBuilder;

// A new sound event, with the default range.
new SoundBuilder("test/ping").build();

// The JSON test event test/buzz, given a fixed 32-block range.
var buzz = SoundBuilder.edit("mmdlib:test/buzz");
buzz.range = 32;
buzz.build();

// A new sound set: wood, but breaking plays the ping, at half volume.
var thud = new SoundTypeBuilder("test/thud");
thud.base = "wood";
thud.breakSound = "mmdlib:test/ping";
thud.volume = 0.5;
thud.build();

// The JSON test set test/chiming, at a higher pitch.
var chiming = SoundTypeBuilder.edit("mmdlib:test/chiming");
chiming.pitch = 1.2;
chiming.build();

// A material naming the script's sound set as its block sound.
var material = new MaterialBuilder("sound_material");
material.removeItems("all");
material.removeBlocks("all");
material.hasFluid = false;
material.blockSound = "mmdlib:test/thud";
material.build();
