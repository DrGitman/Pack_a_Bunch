# Item geometry families

All geometry is original, authored procedurally in `families.py`. Nothing was downloaded, imported, traced or converted from any third-party asset.

Canonical box 1000 × 1000 × 1000 mm, origin at the minimum corner, X width, Y depth, Z up. Quads only, planar, convex, counter-clockwise from outside, closed shells, no interior faces.

| Family | Quads | Parts | What it is |
| --- | ---: | ---: | --- |
| `flat_rectangle` | 24 | 4 | Book, notebook, board game: two covers, a page block, a spine. |
| `slim_slab` | 28 | 2 | Phone, tablet, laptop: a thin slab with softened corners and a screen step. |
| `small_carton` | 18 | 3 | A closed carton, with the two top flaps meeting in the middle. |
| `upright_cylinder` | 94 | 1 | Can, jar, candle: a cylinder with a rolled rim. |
| `lying_cylinder` | 126 | 1 | A cylinder on its side, along the width. |
| `bottle` | 222 | 1 | Body, shoulder, neck, cap. Neck ratio 0.34 of the body width. |
| `tight_roll` | 238 | 1 | Sleeping bag, rolled towel: a lying roll held by two straps. |
| `soft_pouch` | 198 | 3 | Wash bag or pencil case: a plump soft body, the zip along its top and the pull tab. |
| `cable_coil` | 384 | 1 | A coiled cable or hose: a flat torus. |
| `thin_bundle` | 84 | 6 | A bundle of cutlery, poles or rods: round bars side by side, their ends staggered. |
| `shallow_tray` | 30 | 5 | Baking tray, pan, drawer organiser: a floor and four low walls. |
| `suitcase` | 318 | 9 | Hard-shell case standing on four spinner wheels, with a pull handle and a carry handle. |
| `duffel_bag` | 196 | 2 | A barrel bag lying along its width, with a strap handle on top. |
| `backpack` | 374 | 5 | Rounded main body, a front pocket, two side pockets and a grab loop. |
| `cooler_box` | 214 | 5 | Cool box: moulded body, an overhanging lid and a carry handle across the top. |
| `folded_chair` | 152 | 4 | Folding chair folded flat: two tube frames, the seat slats and the back panel. |
| `yoga_mat` | 154 | 3 | A rolled mat: the roll, with its core showing at both ends. |
| `toolbox` | 234 | 5 | Toolbox: a rounded body, a lid with two latches, and the carry handle on top. |
| `ball` | 512 | 1 | Football, globe, any ball. |
| `crate` | 78 | 13 | Open-top slatted crate: base, corner posts, two boards per side. |
| `appliance_slab` | 288 | 4 | Washing machine: a rounded cabinet, the control panel and dial, and the round porthole door. |
| `upright_fridge` | 30 | 5 | Tall fridge: body, fridge and freezer doors, handles. |
| `mattress` | 156 | 2 | A mattress: plump rounded edges and a quilted top panel. |
| `plank_stack` | 24 | 4 | Boards stacked flat, each a little offset. |
| `ladder` | 42 | 7 | Two rails and five rungs, lying flat as it is carried. |
| `barrel` | 222 | 1 | Drum or barrel: bulging body with rims. |
| `bicycle` | 1008 | 36 | Bicycle side on: tyres and rims, hubs, a diamond frame, fork, bars, saddle and cranks. |
| `lawnmower` | 838 | 11 | Push lawnmower: a rounded deck on four wheels, the engine, a grass box and the handle. |
| `sofa` | 556 | 14 | Sofa: base, three seat cushions, three back cushions against the frame, padded arms. |
| `armchair` | 516 | 10 | Armchair: a deep seat cushion between padded arms, a back cushion and short legs. |
| `dining_table` | 194 | 7 | Table: a top with rounded edges, an apron under it and four turned legs. |
| `chair` | 344 | 8 | Chair: a padded seat on four turned legs, back posts and a curved back rest. |
| `wardrobe` | 30 | 5 | Carcass, two doors, two handles. |
| `bed_frame` | 466 | 8 | Bed: frame on turned legs, a plump mattress, a padded headboard and a low footboard. |
| `lamp` | 190 | 1 | Base, stem and a shade wider at the bottom. |
| `tv_stand` | 18 | 3 | A TV on its stand: cabinet, neck, screen. |
| `plant_pot` | 126 | 1 | Tapered pot with a rim — also how a cup or tumbler is drawn. |
| `piano` | 24 | 4 | Upright piano: case, keyboard shelf, legs under it. |
| `kettle` | 487 | 5 | Jug kettle on its power base: a tapered body, lid and knob, a curved spout, a tall handle. |
| `coffee_maker` | 112 | 4 | Drip coffee maker: base plate, water tower at the back, hood over a round carafe. |
| `microwave` | 106 | 3 | Microwave or countertop oven: a rounded body, a door window and the control strip beside it. |
| `toaster` | 232 | 4 | Pop-up toaster: a rounded body, two slot rims on top and the lever on its side. |
| `cooker` | 144 | 8 | Free-standing cooker: body, four rings on the hob, splashback, oven door and handle. |
| `cooking_pot` | 106 | 3 | Saucepan or stock pot: round body, a lid knob and a long handle. |
| `frying_pan` | 68 | 2 | Frying pan or skillet: a shallow flared disc and a long handle. |
| `plate_stack` | 752 | 8 | A stack of four dinner plates: each with its foot ring, a shallow well and a flared rim. |
| `bowl` | 126 | 1 | A bowl: a foot ring and a wide flared body. |
| `pillow` | 158 | 1 | Pillow or cushion: plump in the middle, thinning to rounded edges all round. |
| `folded_stack` | 24 | 4 | Folded clothes, towels or a blanket: four soft layers, none quite squared up. |
| `bookcase` | 60 | 10 | Bookcase or open shelving: sides, back, three shelves and books on two of them. |
| `chest_of_drawers` | 72 | 12 | Chest of drawers, cupboard or bedside table: carcass, four drawer fronts, handles. |
| `desk` | 36 | 6 | Desk: top, a drawer pedestal on one side, two legs on the other. |
| `stool` | 258 | 7 | Stool or bar stool: a round padded seat on four splayed turned legs and a foot ring. |
| `ottoman` | 214 | 5 | Ottoman, pouf or bench: a plump padded block on four short turned feet. |
| `clothes_rail` | 574 | 17 | Clothes rail: two feet, two uprights, the rail, and coats hanging from it. |
| `framed_panel` | 30 | 5 | Mirror, picture, whiteboard or a door: a frame round a recessed panel, drawn lying flat. |
| `disc` | 126 | 1 | Clock, platter, frisbee, anything round and flat: a disc with a raised rim. |
| `monitor` | 74 | 3 | Monitor or flat-screen TV: a rounded foot, a neck and a thin screen with softened corners. |
| `printer` | 24 | 4 | Printer: body, scanner lid, output tray at the front and paper feed behind. |
| `helmet` | 213 | 2 | Helmet: a dome with a short peak at the front. |
| `shoe` | 424 | 9 | Trainer: a thick sole, the heel counter and collar, the toe box, the tongue and laces. |
| `tote_bag` | 230 | 3 | Tote or shopping bag: a soft body that widens to its top, and two strap handles. |
| `guitar` | 136 | 4 | Guitar or violin lying on its back: lower and upper bouts, neck and headstock. |
| `upright_vacuum` | 342 | 5 | Upright vacuum: a wide floor head, the motor body with its dust bin, the stick and handle. |
| `long_handle` | 152 | 4 | Broom or mop lying down: a long handle with its threaded collar, the head and its bristles. |
| `watering_can` | 374 | 5 | Watering can: an oval body with its filler, a long spout with the rose, an arched handle. |
| `power_drill` | 328 | 4 | Cordless drill: rounded motor housing, a round chuck, the grip and the battery pack. |
| `hand_tool` | 304 | 4 | Hammer lying flat: a shaped wooden handle and the steel head with its face and claw. |
| `mug` | 276 | 2 | Mug: a straight-sided body with a rolled foot, a coffee line below the rim, a D handle. |
| `wine_glass` | 382 | 1 | Wine glass: a round foot, a thin stem, and a tulip bowl. |
| `vase` | 446 | 1 | Vase: a foot ring, a full shoulder, a narrow neck and a flared lip. |
| `teapot` | 477 | 4 | Teapot: a squat round body, a lid with a knob, a curved spout and a looped handle. |
| `blender` | 439 | 5 | Blender: a motor base with a dial, a tapered jug with a lid, and the jug's handle. |
| `stand_mixer` | 556 | 6 | Stand mixer: a base, a column at the back, the tilting head over a steel bowl, the beater. |
| `air_fryer` | 320 | 4 | Air fryer: a rounded body, the basket drawer with its handle, a dial on the top panel. |
| `knife_block` | 76 | 6 | Knife block: a slanted block with five handles standing out of its top. |
| `dish_rack` | 436 | 11 | Dish drying rack: a drip tray, a wire rail round it, four plates standing in it. |
| `laundry_basket` | 478 | 1 | Laundry basket: an oval woven tub, ribbed round its sides, with a rolled rim. |
| `bucket` | 304 | 3 | Bucket: a tapered pail with a beaded rim and a wire bail with a grip. |
| `pedal_bin` | 361 | 4 | Pedal bin: a round body, a domed lid on a hinge at the back, the pedal at the front. |
| `clothes_iron` | 118 | 4 | Iron: a pointed soleplate, the body over it, a looped handle and the dial. |
| `ironing_board` | 90 | 6 | Ironing board folded for carrying: the padded board with its pointed end, legs folded under. |
| `pedestal_fan` | 534 | 8 | Standing fan: a round base, a pole, the motor housing and a caged three-blade head. |
| `oil_heater` | 992 | 16 | Oil-filled radiator: nine rounded fins in a row, a control box and castor feet. |
| `desk_lamp` | 338 | 4 | Desk lamp: a weighted round base, two jointed arms and a cone shade looking down. |
| `speaker` | 348 | 3 | Bookshelf speaker: a rounded cabinet with a woofer cone and a dome tweeter on the front. |
| `camera` | 534 | 5 | Camera: the body with its grip and viewfinder hump, and a lens with focus and zoom rings. |
| `headphones` | 402 | 3 | Over-ear headphones: a padded band arching over two cushioned ear cups. |
| `alarm_clock` | 378 | 6 | Twin-bell alarm clock: a round case facing forward, two bells, a hammer and two feet. |
| `book_stack` | 96 | 16 | A stack of four books of different sizes, spines and page edges showing. |
| `potted_plant` | 594 | 11 | Potted plant: a tapered pot with its rim, soil, and a full leafy crown. |
| `umbrella` | 274 | 3 | Folded umbrella: the pleated canopy tapering to its tip, the shaft and a crook handle. |
| `storage_bin` | 212 | 4 | Plastic storage box: a tub flaring to its rim, a clip-on lid and handles at each end. |
| `boot` | 260 | 4 | Tall boot: sole and heel, the foot, and the shaft rising from the back. |
| `hair_dryer` | 196 | 2 | Hair dryer: the barrel with its nozzle and back vent, and the handle angled below. |
| `skateboard` | 662 | 9 | Skateboard: a deck with both tails kicked up, two trucks and four wheels. |
| `kick_scooter` | 374 | 7 | Kick scooter: a deck on two wheels, the steering column and a T handlebar with grips. |
| `tennis_racket` | 328 | 5 | Racket lying flat: the oval frame round its string bed, the throat, and the grip. |
| `golf_bag` | 796 | 11 | Golf bag: a tall padded tube with a collar, a pocket, a strap and clubs standing in it. |
| `snowboard` | 174 | 7 | Snowboard lying flat: the board with raised tips at both ends and two bindings. |
| `lantern` | 462 | 8 | Camping lantern: a base, a glass chimney in a wire cage, a vented cap and a bail handle. |
| `gas_cylinder` | 332 | 3 | Gas bottle: a domed steel body on a foot ring, the valve and its protective shroud. |
| `stroller` | 728 | 10 | Folded stroller: two long frame tubes with hooked handles, the folded seat, four wheels. |
| `child_car_seat` | 482 | 6 | Child car seat: a moulded base, the seat, a tall back with side wings and a headrest. |
| `high_chair` | 282 | 7 | High chair: a seat with arms and a tray, on four splayed legs. |
| `office_chair` | 1158 | 18 | Office chair: a five-star base on castors, the gas lift, a padded seat, back and arms. |
| `bean_bag` | 207 | 1 | Bean bag: a heavy pear-shaped slump, wider and flatter at the bottom. |
| `side_table` | 346 | 3 | Round side table: a top with a rounded edge, a pedestal and a weighted foot. |
| `coffee_table` | 244 | 6 | Coffee table: a top with rounded edges, a lower shelf and four legs. |
| `filing_cabinet` | 288 | 16 | Filing cabinet: a tall carcass, four drawers, each with a handle and a label holder. |
| `robot_vacuum` | 282 | 3 | Robot vacuum: a low round body with a bevelled edge, the bumper and a sensor turret. |
| `dumbbell` | 522 | 5 | Dumbbell: a knurled handle between stacks of round weight plates. |
| `kettlebell` | 284 | 2 | Kettlebell: a round cast body on a flat base, and the thick handle over it. |
| `sewing_machine` | 440 | 6 | Sewing machine: the bed, the pillar, the arm over it, the head, needle and handwheel. |
| `paint_can` | 342 | 2 | Paint tin: a straight can with rolled rims, a lid and a wire handle. |
| `wheelbarrow` | 232 | 6 | Wheelbarrow: a flared steel tray, the wheel at the front, two handles and legs. |
| `bbq_grill` | 517 | 9 | Kettle barbecue: a round bowl, its domed lid with a handle, three legs and two wheels. |
| `pet_carrier` | 208 | 9 | Pet carrier: a rounded shell, the barred door on the front and a handle on top. |
| `dog_bed` | 478 | 2 | Round dog bed: a plump bolster ring around a sunken cushion. |
| `aquarium` | 178 | 11 | Fish tank: a base trim, glass walls in a frame, gravel, and the hood on top. |

## Compromises

- Round parts are faceted at 16–32 sides and marked `smooth`, which the app draws without edge lines so a curve shades as one surface. Sphere and dome poles are degenerate quads (one repeated vertex).
- `bicycle` wheels are solid discs rather than rings — a tyre ring costs 60+ quads per wheel.
- `folded_chair`, `ladder` and `plank_stack` are drawn lying as they are usually packed.
- One variant per family. Proportions are ratios of the measured box, so an unusually squat bottle stays a bottle.

## Regenerating

From the project root, in plain Python 3 or Blender 4.x headless:

```
python3 tools/geometry/families.py app/src/main/res/raw
blender -b -P tools/geometry/families.py -- app/src/main/res/raw
```

It checks every family (fills the box, 4 points per face, planar, at most 600 quads) and exits 1 if any fails. `preview.py` redraws `families_preview.png` from the JSON.

## In the app

`ui/render/FamilyMeshes.kt` loads these with `org.json`, stretches each to the item's measured box, and chooses the family (scanned voxels first, then the measured form, then the item's name). `IsometricCrate` shades each face from its normal. The meshes are display only: they never reach `ItemSpec.shape` or the solver.
