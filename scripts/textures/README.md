# Raw-form texture sources

`raw_item.png` and `raw_block.png` are the canonical 16×16 grayscale defaults.
They adapt the team-selected concept pairing: B's fractured raw mineral lump and
A's packed storage-block chunks with more angular edges. The concept sheet was
AI-generated during the November 15, 2026 texture discussion; these PNGs were
cropped, reduced to 16×16, and quantized to seven grayscale levels. The item has
binary transparency; the block is opaque.

`generate_material_templates.py --source ...` copies these pixels into the
bundled material templates instead of importing the old Base Metals raw sprite.
Update these sources alongside their bundled copies when revising the artwork.
The source hashes are recorded in `mmdlib_templates/provenance.json`.

These are fallback textures. Mod and pack authors are expected to replace them
with distinct per-material shapes or patterns for accessibility. Tint alone does
not reliably distinguish materials for color-blind players. Replacing a shared
template alone does not provide those per-material distinctions.
These defaults do not replace the in-world ore
overlay, which is deferred for separate artwork.
