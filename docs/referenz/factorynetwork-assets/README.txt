Factory Network - current block and item set
=============================================

Minecraft 1.21.1, Java Edition, NeoForge. Everything in here is the set as
it runs in game today. It is the starting point for the redesign, not a
sketch: every model loads, every texture is mapped.

What is in here
---------------

renders/
    blocks.png      the 18 blocks plus connector and two states (burner on,
                    bridge linked), rendered from the models below in the
                    inventory perspective
    items.png       all items with their in-game names, grouped by family,
                    cables in all 17 colours
    textures.png    every raw texture, 2x magnified, with file names

names.txt           block and item ids with their in-game names

assets/factorynetwork/
    blockstates/    which model each block uses in which state
    models/block/   Java Edition JSON models, plain boxes, no rotations
    models/item/    item models; most are thin 3D cards with one "face"
                    texture, a few are flat item/generated icons, cables
                    reuse the block inventory models
    textures/block/ 64x64 PNG
    textures/item/  64x64 PNG

Notes
-----

- The folder keeps the vanilla layout, so Blockbench resolves textures if
  you open a model from inside assets/factorynetwork/models/.
- Textures are 64x64 for a 16x16 block face (4 texels per unit). Please
  stay at that resolution.
- Cables and connector status lights are tinted in code. Plain cable is
  untinted; the colour variants multiply the dye colour by 0.82. The
  status ring on the connector shows 0xA3D9A5 when the device is online.
- display_0 ... display_15 are one block in 16 states. The screen content
  is drawn at runtime, so the front texture is a plain dark surface.
- The Web Panel exists in the mod but is not part of this order and is
  left out here.
