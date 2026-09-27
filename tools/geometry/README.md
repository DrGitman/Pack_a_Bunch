# Item geometry families

All geometry is original, authored procedurally in `families.py`. Nothing was downloaded, imported, traced or converted from any third-party asset.

Canonical box 1000 × 1000 × 1000 mm, origin at the minimum corner, X width, Y depth, Z up. Quads only, planar, convex, counter-clockwise from outside, closed shells, no interior faces.

| Family | Quads | Parts | What it is |
| --- | ---: | ---: | --- |
| `flat_rectangle` | 24 | 4 | Book, notebook, board game: two covers, a page block, a spine. |
| `slim_slab` | 28 | 2 | Phone, tablet, laptop: a thin slab with softened corners and a screen step. |
| `small_carton` | 18 | 3 | A closed carton, with the two top flaps meeting in the middle. |
| `upright_cylinder` | 34 | 1 | Can, jar, candle: a cylinder with a rolled rim. |
| `lying_cylinder` | 46 | 1 | A cylinder on its side, along the width. |
| `bottle` | 68 | 1 | Body, shoulder, neck, cap. Neck ratio 0.34 of the body width. |
| `tight_roll` | 78 | 1 | Sleeping bag, rolled towel: a lying roll held by two straps. |
| `soft_pouch` | 20 | 2 | Wash bag, pencil case, soft bag: a pillow section with a zip ridge. |
| `cable_coil` | 60 | 1 | A coiled cable or hose: a flat torus. |
| `thin_bundle` | 18 | 3 | Cutlery, tent poles, a sheaf of rods: long bars side by side, ends staggered. |
| `shallow_tray` | 30 | 5 | Baking tray, pan, drawer organiser: a floor and four low walls. |
| `suitcase` | 32 | 4 | Standing case with rounded corners and a carry handle. |
| `duffel_bag` | 64 | 2 | A barrel bag lying along its width, with a strap handle on top. |
| `backpack` | 22 | 3 | Main body with a rounded top, a front pocket and a grab handle. |
| `cooler_box` | 18 | 3 | Cool box: body, overhanging lid, handle. |
| `folded_chair` | 18 | 3 | A folding chair folded flat: two frames and the hinge between them. |
| `yoga_mat` | 50 | 3 | A rolled mat: the roll, with its core showing at both ends. |
| `toolbox` | 30 | 5 | Box body, sloped lid, carry handle. |
| `ball` | 72 | 1 | Football, globe, any ball. |
| `crate` | 78 | 13 | Open-top slatted crate: base, corner posts, two boards per side. |
| `appliance_slab` | 34 | 3 | Washing machine, dishwasher: body, round door, control strip. |
| `upright_fridge` | 30 | 5 | Tall fridge: body, fridge and freezer doors, handles. |
| `mattress` | 14 | 1 | A mattress with softened long edges. |
| `plank_stack` | 24 | 4 | Boards stacked flat, each a little offset. |
| `ladder` | 42 | 7 | Two rails and five rungs, lying flat as it is carried. |
| `barrel` | 68 | 1 | Drum or barrel: bulging body with rims. |
| `bicycle` | 78 | 9 | Two wheels, a triangle frame, saddle and handlebar — seen side on along its width. |
| `lawnmower` | 64 | 8 | Deck, four wheels, and a handle rising to the back. |
| `sofa` | 24 | 4 | Seat base, back, two arms. |
| `armchair` | 24 | 4 | A deep seat between thick arms, high back. |
| `dining_table` | 30 | 5 | Top and four legs. |
| `chair` | 48 | 8 | Seat, four legs, back posts and a back rail. |
| `wardrobe` | 30 | 5 | Carcass, two doors, two handles. |
| `bed_frame` | 36 | 6 | Frame on legs, mattress, headboard and a low footboard; length runs along depth. |
| `lamp` | 58 | 1 | Base, stem and a shade wider at the bottom. |
| `tv_stand` | 18 | 3 | A TV on its stand: cabinet, neck, screen. |
| `plant_pot` | 46 | 1 | Tapered pot with a rim — also how a cup or tumbler is drawn. |
| `piano` | 24 | 4 | Upright piano: case, keyboard shelf, legs under it. |
| `kettle` | 52 | 5 | Jug kettle: round body, spout, a handle on the back reaching over the lid. |
| `coffee_maker` | 46 | 4 | Drip coffee maker: base plate, water tower at the back, hood over a round carafe. |
| `microwave` | 18 | 3 | Microwave or countertop oven: body, a door window and the control strip beside it. |
| `toaster` | 24 | 4 | Pop-up toaster: body with two slot rims on top and the lever on its side. |
| `cooker` | 64 | 8 | Free-standing cooker: body, four rings on the hob, splashback, oven door and handle. |
| `cooking_pot` | 46 | 3 | Saucepan or stock pot: round body, a lid knob and a long handle. |
| `frying_pan` | 28 | 2 | Frying pan or skillet: a shallow flared disc and a long handle. |
| `plate_stack` | 72 | 4 | A stack of plates or dishes: four shallow flared discs. |
| `bowl` | 46 | 1 | A bowl: a foot ring and a wide flared body. |
| `pillow` | 42 | 3 | Pillow or cushion: a puffed middle that thins towards the ends. |
| `folded_stack` | 24 | 4 | Folded clothes, towels or a blanket: four soft layers, none quite squared up. |
| `bookcase` | 60 | 10 | Bookcase or open shelving: sides, back, three shelves and books on two of them. |
| `chest_of_drawers` | 54 | 9 | Chest of drawers, cupboard or bedside table: carcass, four drawer fronts, handles. |
| `desk` | 36 | 6 | Desk: top, a drawer pedestal on one side, two legs on the other. |
| `stool` | 42 | 5 | Stool or bar stool: a round seat on four legs, no back. |
| `ottoman` | 34 | 5 | Ottoman, pouf or bench: a padded block with softened top edges on short feet. |
| `clothes_rail` | 36 | 6 | Clothes rail or coat stand: feet, two uprights, the top bar and the clothes hanging on it. |
| `framed_panel` | 30 | 5 | Mirror, picture, whiteboard or a door: a frame round a recessed panel, drawn lying flat. |
| `disc` | 46 | 1 | Clock, platter, frisbee, anything round and flat: a disc with a raised rim. |
| `monitor` | 18 | 3 | Monitor or flat-screen TV: foot, neck and a thin screen. |
| `printer` | 24 | 4 | Printer: body, scanner lid, output tray at the front and paper feed behind. |
| `helmet` | 70 | 2 | Helmet: a dome with a short peak at the front. |
| `shoe` | 18 | 3 | Shoe or boot: sole, a heel and ankle block, and the toe sloping down to the front. |
| `tote_bag` | 24 | 4 | Handbag, tote or shopping bag: a body wider at the top and two handles. |
| `guitar` | 48 | 4 | Guitar or violin lying on its back: lower and upper bouts, neck and headstock. |
| `upright_vacuum` | 24 | 4 | Upright vacuum cleaner: floor head, body, stick and handle. |
| `long_handle` | 12 | 2 | Broom, mop, rake or umbrella: a long pole and its head, lying along the width. |
| `watering_can` | 48 | 6 | Watering can: body, a long spout with its rose, and the handle at the back. |
| `power_drill` | 24 | 4 | Cordless drill: barrel, chuck, grip and the battery it stands on. |
| `hand_tool` | 12 | 2 | Hammer, spanner, screwdriver or pliers: a handle and a heavier head, lying along the width. |

## Compromises

- `cable_coil`, `ball`, `lamp` and the other round families are faceted (10–12 sides); the sphere poles are degenerate quads (one repeated vertex), the only triangles in the set.
- `bicycle` wheels are solid discs rather than rings — a tyre ring costs 60+ quads per wheel.
- `folded_chair`, `ladder` and `plank_stack` are drawn lying as they are usually packed.
- One variant per family. Proportions are ratios of the measured box, so an unusually squat bottle stays a bottle.

## Regenerating

From the project root, in plain Python 3 or Blender 4.x headless:

```
python3 tools/geometry/families.py app/src/main/res/raw
blender -b -P tools/geometry/families.py -- app/src/main/res/raw
```

It checks every family (fills the box, 4 points per face, planar, at most 80 quads) and exits 1 if any fails. `preview.py` redraws `families_preview.png` from the JSON.

## In the app

`ui/render/FamilyMeshes.kt` loads these with `org.json`, stretches each to the item's measured box, and chooses the family (scanned voxels first, then the measured form, then the item's name). `IsometricCrate` shades each face from its normal. The meshes are display only: they never reach `ItemSpec.shape` or the solver.
