package com.packabunch.ui.render

import android.content.Context
import androidx.annotation.RawRes
import com.packabunch.R
import com.packabunch.packing.Dimensions
import com.packabunch.packing.FormFamily
import com.packabunch.packing.ItemForm
import com.packabunch.packing.ItemSpec
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/**
 * The 408 generic item shapes — a bottle, a chair, a sofa, a kettle, a spoon, a towel — drawn in the plan and the packing
 * guide instead of a plain box, so a person can see which thing goes where.
 *
 * ### What these are, and are not
 *
 * Each family is one generic mesh (authored from scratch by `tools/geometry/families.py`, see
 * its README), stretched to the item's measured box. It is a *picture of a kind of thing*,
 * never a model of the person's own object, so it is display only:
 *
 * - it is never written to [ItemSpec.shape] or [ItemSpec.visualShape], and the solver never
 *   sees it — `effectiveShape` stays whatever was measured or the plain box;
 * - it always fills exactly the box the solver reserved, so the picture cannot show an item
 *   poking into space the solver kept for a neighbour;
 * - a real scan always wins: an item with scanned voxels is drawn from those.
 */
enum class GeometryFamily(@RawRes val raw: Int) {
    FLAT_RECTANGLE(R.raw.family_flat_rectangle),
    SLIM_SLAB(R.raw.family_slim_slab),
    SMALL_CARTON(R.raw.family_small_carton),
    UPRIGHT_CYLINDER(R.raw.family_upright_cylinder),
    LYING_CYLINDER(R.raw.family_lying_cylinder),
    BOTTLE(R.raw.family_bottle),
    TIGHT_ROLL(R.raw.family_tight_roll),
    SOFT_POUCH(R.raw.family_soft_pouch),
    CABLE_COIL(R.raw.family_cable_coil),
    THIN_BUNDLE(R.raw.family_thin_bundle),
    SHALLOW_TRAY(R.raw.family_shallow_tray),
    SUITCASE(R.raw.family_suitcase),
    DUFFEL_BAG(R.raw.family_duffel_bag),
    BACKPACK(R.raw.family_backpack),
    COOLER_BOX(R.raw.family_cooler_box),
    FOLDED_CHAIR(R.raw.family_folded_chair),
    YOGA_MAT(R.raw.family_yoga_mat),
    TOOLBOX(R.raw.family_toolbox),
    BALL(R.raw.family_ball),
    CRATE(R.raw.family_crate),
    APPLIANCE_SLAB(R.raw.family_appliance_slab),
    UPRIGHT_FRIDGE(R.raw.family_upright_fridge),
    MATTRESS(R.raw.family_mattress),
    PLANK_STACK(R.raw.family_plank_stack),
    LADDER(R.raw.family_ladder),
    BARREL(R.raw.family_barrel),
    BICYCLE(R.raw.family_bicycle),
    LAWNMOWER(R.raw.family_lawnmower),
    SOFA(R.raw.family_sofa),
    ARMCHAIR(R.raw.family_armchair),
    DINING_TABLE(R.raw.family_dining_table),
    CHAIR(R.raw.family_chair),
    WARDROBE(R.raw.family_wardrobe),
    BED_FRAME(R.raw.family_bed_frame),
    LAMP(R.raw.family_lamp),
    TV_STAND(R.raw.family_tv_stand),
    PLANT_POT(R.raw.family_plant_pot),
    PIANO(R.raw.family_piano),
    KETTLE(R.raw.family_kettle),
    COFFEE_MAKER(R.raw.family_coffee_maker),
    MICROWAVE(R.raw.family_microwave),
    TOASTER(R.raw.family_toaster),
    COOKER(R.raw.family_cooker),
    COOKING_POT(R.raw.family_cooking_pot),
    FRYING_PAN(R.raw.family_frying_pan),
    PLATE_STACK(R.raw.family_plate_stack),
    BOWL(R.raw.family_bowl),
    PILLOW(R.raw.family_pillow),
    FOLDED_STACK(R.raw.family_folded_stack),
    BOOKCASE(R.raw.family_bookcase),
    CHEST_OF_DRAWERS(R.raw.family_chest_of_drawers),
    DESK(R.raw.family_desk),
    STOOL(R.raw.family_stool),
    OTTOMAN(R.raw.family_ottoman),
    CLOTHES_RAIL(R.raw.family_clothes_rail),
    FRAMED_PANEL(R.raw.family_framed_panel),
    DISC(R.raw.family_disc),
    MONITOR(R.raw.family_monitor),
    PRINTER(R.raw.family_printer),
    HELMET(R.raw.family_helmet),
    SHOE(R.raw.family_shoe),
    TOTE_BAG(R.raw.family_tote_bag),
    GUITAR(R.raw.family_guitar),
    UPRIGHT_VACUUM(R.raw.family_upright_vacuum),
    LONG_HANDLE(R.raw.family_long_handle),
    WATERING_CAN(R.raw.family_watering_can),
    POWER_DRILL(R.raw.family_power_drill),
    HAND_TOOL(R.raw.family_hand_tool),
    // Modelled at real size from typical real proportions (see tools/geometry/families.py).
    MUG(R.raw.family_mug),
    WINE_GLASS(R.raw.family_wine_glass),
    VASE(R.raw.family_vase),
    TEAPOT(R.raw.family_teapot),
    BLENDER(R.raw.family_blender),
    STAND_MIXER(R.raw.family_stand_mixer),
    AIR_FRYER(R.raw.family_air_fryer),
    KNIFE_BLOCK(R.raw.family_knife_block),
    DISH_RACK(R.raw.family_dish_rack),
    LAUNDRY_BASKET(R.raw.family_laundry_basket),
    BUCKET(R.raw.family_bucket),
    PEDAL_BIN(R.raw.family_pedal_bin),
    CLOTHES_IRON(R.raw.family_clothes_iron),
    IRONING_BOARD(R.raw.family_ironing_board),
    PEDESTAL_FAN(R.raw.family_pedestal_fan),
    OIL_HEATER(R.raw.family_oil_heater),
    DESK_LAMP(R.raw.family_desk_lamp),
    SPEAKER(R.raw.family_speaker),
    CAMERA(R.raw.family_camera),
    HEADPHONES(R.raw.family_headphones),
    ALARM_CLOCK(R.raw.family_alarm_clock),
    BOOK_STACK(R.raw.family_book_stack),
    POTTED_PLANT(R.raw.family_potted_plant),
    UMBRELLA(R.raw.family_umbrella),
    STORAGE_BIN(R.raw.family_storage_bin),
    BOOT(R.raw.family_boot),
    HAIR_DRYER(R.raw.family_hair_dryer),
    SKATEBOARD(R.raw.family_skateboard),
    KICK_SCOOTER(R.raw.family_kick_scooter),
    TENNIS_RACKET(R.raw.family_tennis_racket),
    GOLF_BAG(R.raw.family_golf_bag),
    SNOWBOARD(R.raw.family_snowboard),
    LANTERN(R.raw.family_lantern),
    GAS_CYLINDER(R.raw.family_gas_cylinder),
    STROLLER(R.raw.family_stroller),
    CHILD_CAR_SEAT(R.raw.family_child_car_seat),
    HIGH_CHAIR(R.raw.family_high_chair),
    OFFICE_CHAIR(R.raw.family_office_chair),
    BEAN_BAG(R.raw.family_bean_bag),
    SIDE_TABLE(R.raw.family_side_table),
    COFFEE_TABLE(R.raw.family_coffee_table),
    FILING_CABINET(R.raw.family_filing_cabinet),
    ROBOT_VACUUM(R.raw.family_robot_vacuum),
    DUMBBELL(R.raw.family_dumbbell),
    KETTLEBELL(R.raw.family_kettlebell),
    SEWING_MACHINE(R.raw.family_sewing_machine),
    PAINT_CAN(R.raw.family_paint_can),
    WHEELBARROW(R.raw.family_wheelbarrow),
    BBQ_GRILL(R.raw.family_bbq_grill),
    PET_CARRIER(R.raw.family_pet_carrier),
    DOG_BED(R.raw.family_dog_bed),
    AQUARIUM(R.raw.family_aquarium),
    // BEGIN EVERYDAY (generated by tools/geometry/everyday.py)
    TUMBLER(R.raw.family_tumbler),
    PINT_GLASS(R.raw.family_pint_glass),
    SHOT_GLASS(R.raw.family_shot_glass),
    CHAMPAGNE_FLUTE(R.raw.family_champagne_flute),
    WHISKY_GLASS(R.raw.family_whisky_glass),
    TEACUP(R.raw.family_teacup),
    CUP(R.raw.family_cup),
    TRAVEL_MUG(R.raw.family_travel_mug),
    WATER_BOTTLE(R.raw.family_water_bottle),
    THERMOS_FLASK(R.raw.family_thermos_flask),
    WINE_BOTTLE(R.raw.family_wine_bottle),
    BEER_BOTTLE(R.raw.family_beer_bottle),
    SODA_CAN(R.raw.family_soda_can),
    MILK_JUG(R.raw.family_milk_jug),
    PITCHER(R.raw.family_pitcher),
    DECANTER(R.raw.family_decanter),
    GRAVY_BOAT(R.raw.family_gravy_boat),
    SUGAR_BOWL(R.raw.family_sugar_bowl),
    EGG_CUP(R.raw.family_egg_cup),
    RAMEKIN(R.raw.family_ramekin),
    SALAD_BOWL(R.raw.family_salad_bowl),
    SOUP_BOWL(R.raw.family_soup_bowl),
    SIDE_PLATE(R.raw.family_side_plate),
    DINNER_PLATE(R.raw.family_dinner_plate),
    CAKE_STAND(R.raw.family_cake_stand),
    BUTTER_DISH(R.raw.family_butter_dish),
    COOKIE_JAR(R.raw.family_cookie_jar),
    STORAGE_JAR(R.raw.family_storage_jar),
    SPICE_JAR(R.raw.family_spice_jar),
    PEPPER_MILL(R.raw.family_pepper_mill),
    OIL_BOTTLE(R.raw.family_oil_bottle),
    SAUCE_BOTTLE(R.raw.family_sauce_bottle),
    BABY_BOTTLE(R.raw.family_baby_bottle),
    SIPPY_CUP(R.raw.family_sippy_cup),
    ICE_BUCKET(R.raw.family_ice_bucket),
    CASSEROLE_DISH(R.raw.family_casserole_dish),
    WOK(R.raw.family_wok),
    COLANDER(R.raw.family_colander),
    MORTAR_AND_PESTLE(R.raw.family_mortar_and_pestle),
    MEASURING_JUG(R.raw.family_measuring_jug),
    COFFEE_POT(R.raw.family_coffee_pot),
    TROPHY(R.raw.family_trophy),
    CANDLE_HOLDER(R.raw.family_candle_holder),
    PILLAR_CANDLE(R.raw.family_pillar_candle),
    LAMPSHADE(R.raw.family_lampshade),
    TABLE_LAMP(R.raw.family_table_lamp),
    FLOOR_LAMP(R.raw.family_floor_lamp),
    GLOBE_LIGHT(R.raw.family_globe_light),
    URN(R.raw.family_urn),
    PLANTER(R.raw.family_planter),
    BIRD_BATH(R.raw.family_bird_bath),
    GARDEN_GNOME(R.raw.family_garden_gnome),
    FIRE_PIT(R.raw.family_fire_pit),
    POUFFE(R.raw.family_pouffe),
    DRUM_KIT_DRUM(R.raw.family_drum_kit_drum),
    HAT_BOX(R.raw.family_hat_box),
    CAKE_TIN_ROUND(R.raw.family_cake_tin_round),
    PAPER_BIN(R.raw.family_paper_bin),
    UMBRELLA_STAND(R.raw.family_umbrella_stand),
    COTTON_BUD_TUB(R.raw.family_cotton_bud_tub),
    SOAP_DISPENSER(R.raw.family_soap_dispenser),
    SHAMPOO_BOTTLE(R.raw.family_shampoo_bottle),
    LOTION_BOTTLE(R.raw.family_lotion_bottle),
    PERFUME_BOTTLE(R.raw.family_perfume_bottle),
    DEODORANT(R.raw.family_deodorant),
    TOOTHBRUSH_CUP(R.raw.family_toothbrush_cup),
    CREAM_JAR(R.raw.family_cream_jar),
    NAIL_POLISH(R.raw.family_nail_polish),
    TOILET_ROLL(R.raw.family_toilet_roll),
    BATH_BOMB(R.raw.family_bath_bomb),
    HAIR_SPRAY_CAN(R.raw.family_hair_spray_can),
    MOUTHWASH(R.raw.family_mouthwash),
    ELECTRIC_TOOTHBRUSH(R.raw.family_electric_toothbrush),
    PILL_BOTTLE(R.raw.family_pill_bottle),
    SANITISER(R.raw.family_sanitiser),
    SMART_SPEAKER(R.raw.family_smart_speaker),
    AIR_PURIFIER(R.raw.family_air_purifier),
    TOWER_FAN(R.raw.family_tower_fan),
    SPACE_HEATER(R.raw.family_space_heater),
    RICE_COOKER(R.raw.family_rice_cooker),
    SLOW_COOKER(R.raw.family_slow_cooker),
    ELECTRIC_KETTLE_JUG(R.raw.family_electric_kettle_jug),
    JUICER(R.raw.family_juicer),
    FOOD_PROCESSOR_BOWL(R.raw.family_food_processor_bowl),
    STAND_FAN_DESK(R.raw.family_stand_fan_desk),
    RECORD_PLAYER_SPEAKER(R.raw.family_record_player_speaker),
    WEBCAM_RING_LIGHT(R.raw.family_webcam_ring_light),
    BEDPAN_POTTY(R.raw.family_bedpan_potty),
    WATERING_GLOBE(R.raw.family_watering_globe),
    SIDEBOARD(R.raw.family_sideboard),
    DRESSER(R.raw.family_dresser),
    NIGHTSTAND(R.raw.family_nightstand),
    CUPBOARD(R.raw.family_cupboard),
    DISPLAY_CABINET(R.raw.family_display_cabinet),
    TALL_BOY(R.raw.family_tall_boy),
    SHOE_CABINET(R.raw.family_shoe_cabinet),
    KITCHEN_UNIT(R.raw.family_kitchen_unit),
    WALL_CABINET(R.raw.family_wall_cabinet),
    LOCKER(R.raw.family_locker),
    SAFE(R.raw.family_safe),
    HUTCH(R.raw.family_hutch),
    TV_CABINET_LOW(R.raw.family_tv_cabinet_low),
    CUBE_SHELF(R.raw.family_cube_shelf),
    BOOKSHELF_TALL(R.raw.family_bookshelf_tall),
    BATHROOM_VANITY(R.raw.family_bathroom_vanity),
    FILING_DRAWERS(R.raw.family_filing_drawers),
    PLAN_CHEST(R.raw.family_plan_chest),
    BLANKET_BOX(R.raw.family_blanket_box),
    TOOL_CHEST_TALL(R.raw.family_tool_chest_tall),
    DRINKS_CABINET(R.raw.family_drinks_cabinet),
    WASHSTAND(R.raw.family_washstand),
    PANTRY_CUPBOARD(R.raw.family_pantry_cupboard),
    JEWELLERY_ARMOIRE(R.raw.family_jewellery_armoire),
    DESK_HUTCH(R.raw.family_desk_hutch),
    CONSOLE_TABLE(R.raw.family_console_table),
    BISTRO_TABLE(R.raw.family_bistro_table),
    KITCHEN_TABLE(R.raw.family_kitchen_table),
    FOLDING_TABLE(R.raw.family_folding_table),
    WORKBENCH(R.raw.family_workbench),
    DRESSING_TABLE(R.raw.family_dressing_table),
    NESTING_TABLES(R.raw.family_nesting_tables),
    WRITING_DESK(R.raw.family_writing_desk),
    STANDING_DESK(R.raw.family_standing_desk),
    PEDESTAL_TABLE(R.raw.family_pedestal_table),
    OVAL_TABLE(R.raw.family_oval_table),
    BEDSIDE_TABLE_LEGS(R.raw.family_bedside_table_legs),
    SAWHORSE(R.raw.family_sawhorse),
    TV_TRAY_TABLE(R.raw.family_tv_tray_table),
    CHILDS_TABLE(R.raw.family_childs_table),
    DINING_CHAIR(R.raw.family_dining_chair),
    WINDSOR_CHAIR(R.raw.family_windsor_chair),
    LADDER_BACK_CHAIR(R.raw.family_ladder_back_chair),
    UPHOLSTERED_CHAIR(R.raw.family_upholstered_chair),
    ROCKING_CHAIR(R.raw.family_rocking_chair),
    CANTILEVER_CHAIR(R.raw.family_cantilever_chair),
    STACKING_CHAIR(R.raw.family_stacking_chair),
    GAMING_STOOL(R.raw.family_gaming_stool),
    BENCH(R.raw.family_bench),
    BENCH_BACK(R.raw.family_bench_back),
    KIDS_CHAIR(R.raw.family_kids_chair),
    BAR_CHAIR(R.raw.family_bar_chair),
    DIRECTORS_CHAIR(R.raw.family_directors_chair),
    BUNK_BED(R.raw.family_bunk_bed),
    COT(R.raw.family_cot),
    CAMP_BED(R.raw.family_camp_bed),
    HEADBOARD(R.raw.family_headboard),
    ROOM_SCREEN(R.raw.family_room_screen),
    COAT_HOOKS(R.raw.family_coat_hooks),
    MIRROR_STANDING(R.raw.family_mirror_standing),
    WALL_SHELF(R.raw.family_wall_shelf),
    CHAISE_LOUNGER(R.raw.family_chaise_lounger),
    HAMMOCK(R.raw.family_hammock),
    SPOON(R.raw.family_spoon),
    FORK(R.raw.family_fork),
    TABLE_KNIFE(R.raw.family_table_knife),
    CHEF_KNIFE(R.raw.family_chef_knife),
    LADLE(R.raw.family_ladle),
    SPATULA(R.raw.family_spatula),
    WOODEN_SPOON(R.raw.family_wooden_spoon),
    WHISK(R.raw.family_whisk),
    TONGS(R.raw.family_tongs),
    CHOPSTICKS(R.raw.family_chopsticks),
    PEELER(R.raw.family_peeler),
    GRATER_FLAT(R.raw.family_grater_flat),
    PIZZA_CUTTER(R.raw.family_pizza_cutter),
    TOOTHBRUSH(R.raw.family_toothbrush),
    HAIRBRUSH(R.raw.family_hairbrush),
    COMB(R.raw.family_comb),
    RAZOR(R.raw.family_razor),
    BACK_SCRUBBER(R.raw.family_back_scrubber),
    TOILET_BRUSH_LONG(R.raw.family_toilet_brush_long),
    SHOE_HORN(R.raw.family_shoe_horn),
    FLY_SWATTER(R.raw.family_fly_swatter),
    BACK_HOOK_UMBRELLA(R.raw.family_back_hook_umbrella),
    BASEBALL_BAT(R.raw.family_baseball_bat),
    PADDLE(R.raw.family_paddle),
    RAKE(R.raw.family_rake),
    SHOVEL(R.raw.family_shovel),
    HOE(R.raw.family_hoe),
    SCREWDRIVER(R.raw.family_screwdriver),
    SPANNER(R.raw.family_spanner),
    SAW(R.raw.family_saw),
    PLIERS(R.raw.family_pliers),
    PAINTBRUSH(R.raw.family_paintbrush),
    MOP_HEAD(R.raw.family_mop_head),
    FEATHER_DUSTER(R.raw.family_feather_duster),
    ICE_SCRAPER(R.raw.family_ice_scraper),
    TV_SCREEN(R.raw.family_tv_screen),
    DESKTOP_PC(R.raw.family_desktop_pc),
    GAMES_CONSOLE(R.raw.family_games_console),
    ROUTER(R.raw.family_router),
    SOUNDBAR(R.raw.family_soundbar),
    RADIO(R.raw.family_radio),
    PROJECTOR(R.raw.family_projector),
    RECORD_PLAYER(R.raw.family_record_player),
    HIFI_AMP(R.raw.family_hifi_amp),
    KEYBOARD_PC(R.raw.family_keyboard_pc),
    PRINTER_SMALL(R.raw.family_printer_small),
    SCANNER(R.raw.family_scanner),
    DEHUMIDIFIER(R.raw.family_dehumidifier),
    DISHWASHER_SLIM(R.raw.family_dishwasher_slim),
    CHEST_FREEZER(R.raw.family_chest_freezer),
    WATER_DISPENSER(R.raw.family_water_dispenser),
    BREAD_MAKER(R.raw.family_bread_maker),
    WAFFLE_MAKER(R.raw.family_waffle_maker),
    ELECTRIC_GRILL(R.raw.family_electric_grill),
    ICE_MAKER(R.raw.family_ice_maker),
    COFFEE_GRINDER(R.raw.family_coffee_grinder),
    STEAM_MOP_BASE(R.raw.family_steam_mop_base),
    WET_VACUUM(R.raw.family_wet_vacuum),
    SEWING_KIT_BOX(R.raw.family_sewing_kit_box),
    LAPTOP_OPEN(R.raw.family_laptop_open),
    TABLET_STAND(R.raw.family_tablet_stand),
    BABY_MONITOR(R.raw.family_baby_monitor),
    SMOKE_DETECTOR_BOX(R.raw.family_smoke_detector_box),
    DRONE(R.raw.family_drone),
    VR_HEADSET(R.raw.family_vr_headset),
    GAME_CONTROLLER(R.raw.family_game_controller),
    POWER_BANK(R.raw.family_power_bank),
    EXTERNAL_DRIVE(R.raw.family_external_drive),
    SAT_BOX(R.raw.family_sat_box),
    ELECTRIC_HEATER_PANEL(R.raw.family_electric_heater_panel),
    MICROWAVE_SMALL(R.raw.family_microwave_small),
    TOASTER_2SLICE(R.raw.family_toaster_2slice),
    KITCHEN_SCALE(R.raw.family_kitchen_scale),
    BATHROOM_SCALE(R.raw.family_bathroom_scale),
    HAIR_STRAIGHTENER(R.raw.family_hair_straightener),
    ELECTRIC_SHAVER(R.raw.family_electric_shaver),
    SEWING_MACHINE_SMALL(R.raw.family_sewing_machine_small),
    SAFE_BOX_SMALL(R.raw.family_safe_box_small),
    FIRST_AID_CASE(R.raw.family_first_aid_case),
    TISSUE_BOX(R.raw.family_tissue_box),
    LUNCH_BOX(R.raw.family_lunch_box),
    BREAD_BIN(R.raw.family_bread_bin),
    JEWELLERY_BOX(R.raw.family_jewellery_box),
    MAKEUP_CASE(R.raw.family_makeup_case),
    SHOEBOX(R.raw.family_shoebox),
    PIZZA_BOX(R.raw.family_pizza_box),
    WINE_CASE(R.raw.family_wine_case),
    PRINTER_PAPER(R.raw.family_printer_paper),
    ICE_PACK_COOLER_BAG(R.raw.family_ice_pack_cooler_bag),
    FOLDED_SHIRT(R.raw.family_folded_shirt),
    FOLDED_TROUSERS(R.raw.family_folded_trousers),
    FOLDED_JUMPER(R.raw.family_folded_jumper),
    FOLDED_TOWEL(R.raw.family_folded_towel),
    FOLDED_BLANKET(R.raw.family_folded_blanket),
    SOCKS(R.raw.family_socks),
    DRESS_HANGER(R.raw.family_dress_hanger),
    SUN_HAT(R.raw.family_sun_hat),
    CAP(R.raw.family_cap),
    BEANIE(R.raw.family_beanie),
    HANDBAG(R.raw.family_handbag),
    CLUTCH_BAG(R.raw.family_clutch_bag),
    BRIEFCASE(R.raw.family_briefcase),
    MESSENGER_BAG(R.raw.family_messenger_bag),
    SCHOOL_BAG(R.raw.family_school_bag),
    BELT(R.raw.family_belt),
    WATCH(R.raw.family_watch),
    SUNGLASSES(R.raw.family_sunglasses),
    GLOVES(R.raw.family_gloves),
    SANDAL(R.raw.family_sandal),
    HIGH_HEEL(R.raw.family_high_heel),
    WELLINGTON(R.raw.family_wellington),
    SUIT_CASE_SMALL(R.raw.family_suit_case_small),
    SOAP_BAR(R.raw.family_soap_bar),
    SOAP_DISH(R.raw.family_soap_dish),
    TOOTHPASTE(R.raw.family_toothpaste),
    SPONGE(R.raw.family_sponge),
    SHOWER_CADDY(R.raw.family_shower_caddy),
    MAKEUP_BAG(R.raw.family_makeup_bag),
    HAIRDRYER_DIFFUSER(R.raw.family_hairdryer_diffuser),
    BATH_MAT_ROLLED(R.raw.family_bath_mat_rolled),
    FIRST_AID_BAG(R.raw.family_first_aid_bag),
    NAPPY_PACK(R.raw.family_nappy_pack),
    LAUNDRY_HAMPER(R.raw.family_laundry_hamper),
    IRONING_BASKET_STOOL(R.raw.family_ironing_basket_stool),
    CLOTHES_AIRER(R.raw.family_clothes_airer),
    HAND_MIRROR(R.raw.family_hand_mirror),
    WALL_CLOCK(R.raw.family_wall_clock),
    FIRE_SCREEN(R.raw.family_fire_screen),
    DOLL_HOUSE(R.raw.family_doll_house),
    ROCKING_HORSE(R.raw.family_rocking_horse),
    TOY_CAR(R.raw.family_toy_car),
    TEDDY_BEAR(R.raw.family_teddy_bear),
    LEGO_BOX(R.raw.family_lego_box),
    KEYBOARD_PIANO(R.raw.family_keyboard_piano),
    VIOLIN_CASE(R.raw.family_violin_case),
    MICROPHONE_STAND(R.raw.family_microphone_stand),
    // END EVERYDAY
}

/**
 * Which family to draw, and how to lay it into the item's box.
 *
 * [axes] says which of the item's axes (0 width, 1 depth, 2 height) each of the mesh's own
 * axes lands on — so a book stood on its edge is drawn standing, and a roll whose length runs
 * along the depth is drawn lengthways, instead of being squashed across. [flipZ] turns the
 * mesh upside down: a flowerpot wider at the base.
 */
data class FamilyChoice(val family: GeometryFamily, val axes: List<Int> = listOf(0, 1, 2), val flipZ: Boolean = false)

object FamilyMeshes {

    /** The mesh as authored: quads in the 1000 mm box, with an outward normal per quad. */
    private class Canonical(val points: FloatArray, val normals: FloatArray, val smooth: BooleanArray, val parts: IntArray) {
        val faceCount get() = normals.size / 3
    }

    private val canonical = ConcurrentHashMap<GeometryFamily, Canonical>()

    private data class Key(val choice: FamilyChoice, val widthMm: Int, val depthMm: Int, val heightMm: Int)

    /** Sized meshes. Small and bounded — a plan has a few dozen distinct items at most. */
    private val sized = object : LinkedHashMap<Key, List<SurfaceFace>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, List<SurfaceFace>>?) = size > 128
    }

    /**
     * The family mesh for [item] in the item's own millimetres (width, depth, height, height
     * up), or null when nothing says what it is — which draws the plain box, as before.
     */
    fun surfaceFor(context: Context, item: ItemSpec): List<SurfaceFace>? {
        val choice = chooseFamily(item) ?: return null
        return surface(context, choice, item.dimensions)
    }

    fun surface(context: Context, choice: FamilyChoice, dimensions: Dimensions): List<SurfaceFace>? {
        val key = Key(choice, dimensions.widthMm, dimensions.depthMm, dimensions.heightMm)
        synchronized(sized) { sized[key] }?.let { return it }
        val mesh = canonical[choice.family] ?: load(context, choice.family)?.also { canonical[choice.family] = it } ?: return null
        val faces = scale(mesh, choice, dimensions)
        synchronized(sized) { sized[key] = faces }
        return faces
    }

    /**
     * Reads one family's JSON. A missing or malformed file draws the plain box rather than
     * crashing the plan: the picture is a nicety, the plan is the product.
     */
    private fun load(context: Context, family: GeometryFamily): Canonical? = runCatching {
        val text = context.resources.openRawResource(family.raw).bufferedReader().use { it.readText() }
        val json = JSONObject(text)
        val bounds = json.optJSONArray("bounds_mm")
        val bx = bounds?.optDouble(0, 1000.0)?.toFloat() ?: 1000f
        val by = bounds?.optDouble(1, 1000.0)?.toFloat() ?: 1000f
        val bz = bounds?.optDouble(2, 1000.0)?.toFloat() ?: 1000f
        val faces = json.getJSONArray("faces")
        val points = FloatArray(faces.length() * 12)
        val normals = FloatArray(faces.length() * 3)
        val smooth = BooleanArray(faces.length())
        val parts = IntArray(faces.length())
        for (f in 0 until faces.length()) {
            smooth[f] = faces.getJSONObject(f).optBoolean("smooth", false)
            parts[f] = faces.getJSONObject(f).optInt("part", -1)
            val quad = faces.getJSONObject(f).getJSONArray("points")
            require(quad.length() == 4) { "family ${family.name}: face $f has ${quad.length()} points" }
            for (v in 0 until 4) {
                val p = quad.getJSONArray(v)
                // Normalised to the unit box here, so scaling later is one multiply per axis.
                points[f * 12 + v * 3] = p.getDouble(0).toFloat() / bx
                points[f * 12 + v * 3 + 1] = p.getDouble(1).toFloat() / by
                points[f * 12 + v * 3 + 2] = p.getDouble(2).toFloat() / bz
            }
            // Newell's method: the true normal of a planar polygon, and still right for the
            // few quads that repeat a vertex (a sphere's poles), where a single cross product
            // of two edges would be zero. Counter-clockwise from outside makes it point out.
            var nx = 0f; var ny = 0f; var nz = 0f
            for (v in 0 until 4) {
                val a = f * 12 + v * 3; val b = f * 12 + ((v + 1) % 4) * 3
                nx += (points[a + 1] - points[b + 1]) * (points[a + 2] + points[b + 2])
                ny += (points[a + 2] - points[b + 2]) * (points[a] + points[b])
                nz += (points[a] - points[b]) * (points[a + 1] + points[b + 1])
            }
            val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-9f)
            normals[f * 3] = nx / len; normals[f * 3 + 1] = ny / len; normals[f * 3 + 2] = nz / len
        }
        Canonical(points, normals, smooth, parts)
    }.getOrNull()

    /**
     * Stretches the unit mesh to the item's box.
     *
     * Normals do not stretch like points: a slope on a mesh squashed flat must come out
     * flatter, so each component is divided by that axis's scale (the inverse transpose of a
     * scale) before it is normalised again.
     */
    private fun scale(mesh: Canonical, choice: FamilyChoice, dimensions: Dimensions): List<SurfaceFace> {
        val size = floatArrayOf(
            dimensions.widthMm.coerceAtLeast(1).toFloat(),
            dimensions.depthMm.coerceAtLeast(1).toFloat(),
            dimensions.heightMm.coerceAtLeast(1).toFloat(),
        )
        val axes = choice.axes
        return List(mesh.faceCount) { f ->
            val pts = List(4) { v ->
                val out = FloatArray(3)
                for (i in 0 until 3) {
                    var c = mesh.points[f * 12 + v * 3 + i]
                    if (i == 2 && choice.flipZ) c = 1f - c
                    out[axes[i]] = c * size[axes[i]]
                }
                SurfacePoint(out[0], out[1], out[2])
            }
            val n = FloatArray(3)
            for (i in 0 until 3) {
                var c = mesh.normals[f * 3 + i]
                if (i == 2 && choice.flipZ) c = -c
                n[axes[i]] = c / size[axes[i]]
            }
            val len = sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]).coerceAtLeast(1e-12f)
            // `side` is unused for a face with a normal; 5 keeps any code that still reads it
            // treating the face as a lid rather than a wall.
            SurfaceFace(pts, side = 5, normal = SurfacePoint(n[0] / len, n[1] / len, n[2] / len), smooth = mesh.smooth[f], part = mesh.parts[f])
        }
    }
}

// -- choosing a family -----------------------------------------------------------------------------

/**
 * The family to draw for [item], or null for the plain box.
 *
 * In this order: the form a scan measured (a scanned can is a cylinder whatever it is called),
 * then the item's name, then the rough form the name suggests. When a name and a measurement
 * disagree — something called "ball" that measured square — the measurement wins, because the
 * picture must never contradict what the camera saw.
 */
fun chooseFamily(item: ItemSpec): FamilyChoice? {
    val measured = item.form
    val named = familyFromName(item.name)
    val family: GeometryFamily
    var flipZ = false
    when {
        measured != null && measured.family != FormFamily.BOX -> {
            family = if (named != null && (named in ROUND || named in EVERYDAY_ROUND)) named else formFamily(measured.family)
            if (measured.family == FormFamily.TAPERED && family == GeometryFamily.PLANT_POT) flipZ = measured.topRatio < 1f
        }
        measured != null -> {
            // Measured square-ish. Simple solids the fitter recognises reliably would have come
            // back round, so a "ball" that measured square stays a box; anything else — a chair,
            // a bag — measures irregular and is drawn as what it is called.
            family = named?.takeIf { it !in FITTED_ROUND } ?: return null
        }
        named != null -> family = named
        else -> {
            val guessed = ItemForm.guess(item.name) ?: return null
            if (guessed.family == FormFamily.BOX) return null
            family = formFamily(guessed.family)
            if (guessed.family == FormFamily.TAPERED) flipZ = guessed.topRatio < 1f
        }
    }
    return arrange(family, item.dimensions, flipZ)
}

private fun formFamily(form: FormFamily): GeometryFamily = when (form) {
    FormFamily.CYLINDER -> GeometryFamily.UPRIGHT_CYLINDER
    FormFamily.TAPERED -> GeometryFamily.PLANT_POT
    FormFamily.SPHERE -> GeometryFamily.BALL
    FormFamily.BOX -> GeometryFamily.SMALL_CARTON
}

/**
 * Lays the family into the item's box the way the item is proportioned: a tube longer than it
 * is tall lies down, a rolled mat stood on end stands up, a book whose thinnest side is its
 * depth is drawn standing on its edge, and a sofa or ladder runs along the box's longer side.
 */
private fun arrange(family: GeometryFamily, d: Dimensions, flipZ: Boolean): FamilyChoice {
    val size = intArrayOf(d.widthMm, d.depthMm, d.heightMm)
    val w = d.widthMm.toFloat(); val dp = d.depthMm.toFloat(); val h = d.heightMm.toFloat()
    val longest = maxOf(w, dp); val shortest = minOf(w, dp)
    var f = family
    when (f) {
        GeometryFamily.UPRIGHT_CYLINDER -> if (h < 0.8f * longest && longest > 1.4f * shortest) f = GeometryFamily.LYING_CYLINDER
        GeometryFamily.LYING_CYLINDER, GeometryFamily.TIGHT_ROLL, GeometryFamily.YOGA_MAT ->
            if (h > 1.3f * longest) f = GeometryFamily.UPRIGHT_CYLINDER
        else -> Unit
    }
    val axes = intArrayOf(0, 1, 2)
    fun swap(a: Int, b: Int) { val t = axes[a]; axes[a] = axes[b]; axes[b] = t }
    if (f in THIN_IN_HEIGHT || f in EVERYDAY_THIN) {
        val thinnest = (0..2).minBy { size[it] }
        if (thinnest != 2 && size[thinnest] * 1.2f < size[2]) swap(2, axes.indexOf(thinnest))
    }
    if ((f in LONG_IN_WIDTH || f in EVERYDAY_LONG) && size[axes[1]] > 1.15f * size[axes[0]]) swap(0, 1)
    if (f in LONG_IN_DEPTH && size[axes[0]] > 1.15f * size[axes[1]]) swap(0, 1)
    return FamilyChoice(f, axes.toList(), flipZ && f == GeometryFamily.PLANT_POT)
}

private val ROUND = setOf(
    GeometryFamily.UPRIGHT_CYLINDER, GeometryFamily.LYING_CYLINDER, GeometryFamily.BOTTLE,
    GeometryFamily.TIGHT_ROLL, GeometryFamily.CABLE_COIL, GeometryFamily.YOGA_MAT, GeometryFamily.BALL,
    GeometryFamily.BARREL, GeometryFamily.LAMP, GeometryFamily.PLANT_POT, GeometryFamily.DUFFEL_BAG,
    GeometryFamily.KETTLE, GeometryFamily.COOKING_POT, GeometryFamily.FRYING_PAN, GeometryFamily.PLATE_STACK,
    GeometryFamily.BOWL, GeometryFamily.DISC, GeometryFamily.HELMET, GeometryFamily.STOOL,
    GeometryFamily.MUG, GeometryFamily.WINE_GLASS, GeometryFamily.VASE, GeometryFamily.TEAPOT, GeometryFamily.BUCKET,
    GeometryFamily.PEDAL_BIN, GeometryFamily.LAUNDRY_BASKET, GeometryFamily.GAS_CYLINDER, GeometryFamily.PAINT_CAN,
    GeometryFamily.BEAN_BAG, GeometryFamily.KETTLEBELL, GeometryFamily.ROBOT_VACUUM, GeometryFamily.SIDE_TABLE,
    GeometryFamily.POTTED_PLANT, GeometryFamily.LANTERN,
)

private val FITTED_ROUND = setOf(
    GeometryFamily.UPRIGHT_CYLINDER, GeometryFamily.LYING_CYLINDER, GeometryFamily.BALL, GeometryFamily.PLANT_POT,
)

private val THIN_IN_HEIGHT = setOf(
    GeometryFamily.FLAT_RECTANGLE, GeometryFamily.SLIM_SLAB, GeometryFamily.MATTRESS,
    GeometryFamily.FRAMED_PANEL, GeometryFamily.DISC, GeometryFamily.GUITAR,
    GeometryFamily.IRONING_BOARD, GeometryFamily.TENNIS_RACKET, GeometryFamily.SNOWBOARD, GeometryFamily.BOOK_STACK,
)

private val LONG_IN_WIDTH = setOf(
    GeometryFamily.LYING_CYLINDER, GeometryFamily.TIGHT_ROLL, GeometryFamily.DUFFEL_BAG, GeometryFamily.YOGA_MAT,
    GeometryFamily.SOFT_POUCH, GeometryFamily.THIN_BUNDLE, GeometryFamily.LADDER, GeometryFamily.MATTRESS,
    GeometryFamily.BICYCLE, GeometryFamily.SOFA, GeometryFamily.PIANO, GeometryFamily.PLANK_STACK,
    GeometryFamily.LONG_HANDLE, GeometryFamily.HAND_TOOL, GeometryFamily.SHOE, GeometryFamily.GUITAR,
    GeometryFamily.POWER_DRILL, GeometryFamily.CLOTHES_RAIL, GeometryFamily.PILLOW, GeometryFamily.FRYING_PAN,
    GeometryFamily.IRONING_BOARD, GeometryFamily.UMBRELLA, GeometryFamily.SKATEBOARD, GeometryFamily.KICK_SCOOTER,
    GeometryFamily.TENNIS_RACKET, GeometryFamily.SNOWBOARD, GeometryFamily.DUMBBELL, GeometryFamily.HAIR_DRYER,
    GeometryFamily.WHEELBARROW, GeometryFamily.OIL_HEATER, GeometryFamily.COFFEE_TABLE, GeometryFamily.STAND_MIXER,
)

private val LONG_IN_DEPTH = setOf(GeometryFamily.BED_FRAME)

/**
 * The family an item's name points to, or null.
 *
 * Deliberately narrow, because a kettle drawn as a chair is worse than a kettle drawn as a box:
 *
 * - only the thing itself counts — the *last* word, or the word before "of"/"for"/"with" —
 *   so a "table lamp" is a lamp, a "box of books" a box, and a "chair cushion" nothing;
 * - whole words only, so a "cupboard" is not a cup and "boxing gloves" not a box;
 * - two-word names that mean something else ("sleeping bag", "tv stand") are matched first;
 * - anything not listed is null and draws the plain box.
 */
fun familyFromName(name: String): GeometryFamily? {
    val words = name.lowercase()
        .replace(Regex("\\(.*?\\)"), " ")
        .split(Regex("[^a-z]+"))
        .filter { it.length > 1 }
    if (words.isEmpty()) return null
    val text = " " + words.joinToString(" ") + " "
    for ((phrase, family) in PHRASES) if (" $phrase " in text) return family
    // The everyday library (EverydayFamilies.kt) is only asked after the first one has had its say.
    for ((phrase, family) in EVERYDAY_PHRASES) if (" $phrase " in text) return family
    val end = words.indexOfFirst { it in CONNECTORS }.let { if (it > 0) it else words.size }
    val head = words[end - 1]
    return WORDS[head] ?: WORDS[head.removeSuffix("s")] ?: WORDS[head.removeSuffix("es")]
        ?: EVERYDAY_WORDS[head] ?: EVERYDAY_WORDS[head.removeSuffix("s")] ?: EVERYDAY_WORDS[head.removeSuffix("es")]
}

private val CONNECTORS = setOf("of", "for", "with", "and")

private val PHRASES: List<Pair<String, GeometryFamily>> = listOf(
    "bed frame" to GeometryFamily.BED_FRAME, "bunk bed" to GeometryFamily.BED_FRAME,
    "tv stand" to GeometryFamily.TV_STAND, "tv unit" to GeometryFamily.TV_STAND, "tv cabinet" to GeometryFamily.TV_STAND,
    "tv bench" to GeometryFamily.TV_STAND, "media console" to GeometryFamily.TV_STAND,
    "washing machine" to GeometryFamily.APPLIANCE_SLAB, "tumble dryer" to GeometryFamily.APPLIANCE_SLAB,
    "washer dryer" to GeometryFamily.APPLIANCE_SLAB,
    "yoga mat" to GeometryFamily.YOGA_MAT, "exercise mat" to GeometryFamily.YOGA_MAT, "camping mat" to GeometryFamily.YOGA_MAT,
    "sleeping mat" to GeometryFamily.YOGA_MAT, "foam mat" to GeometryFamily.YOGA_MAT, "roll mat" to GeometryFamily.YOGA_MAT,
    "sleeping bag" to GeometryFamily.TIGHT_ROLL,
    "folding chair" to GeometryFamily.FOLDED_CHAIR, "camping chair" to GeometryFamily.FOLDED_CHAIR,
    "camp chair" to GeometryFamily.FOLDED_CHAIR, "deck chair" to GeometryFamily.FOLDED_CHAIR,
    "beach chair" to GeometryFamily.FOLDED_CHAIR,
    "cool box" to GeometryFamily.COOLER_BOX, "ice chest" to GeometryFamily.COOLER_BOX,
    "tool box" to GeometryFamily.TOOLBOX, "tool chest" to GeometryFamily.TOOLBOX, "tackle box" to GeometryFamily.TOOLBOX,
    "lawn mower" to GeometryFamily.LAWNMOWER,
    "plant pot" to GeometryFamily.PLANT_POT, "flower pot" to GeometryFamily.PLANT_POT,
    "wash bag" to GeometryFamily.SOFT_POUCH, "toiletry bag" to GeometryFamily.SOFT_POUCH,
    "sponge bag" to GeometryFamily.SOFT_POUCH, "makeup bag" to GeometryFamily.SOFT_POUCH,
    "pencil case" to GeometryFamily.SOFT_POUCH,
    "gym bag" to GeometryFamily.DUFFEL_BAG, "duffel bag" to GeometryFamily.DUFFEL_BAG, "duffle bag" to GeometryFamily.DUFFEL_BAG,
    "sports bag" to GeometryFamily.DUFFEL_BAG, "kit bag" to GeometryFamily.DUFFEL_BAG,
    "board game" to GeometryFamily.FLAT_RECTANGLE, "picture frame" to GeometryFamily.FRAMED_PANEL,
    "photo frame" to GeometryFamily.FRAMED_PANEL, "wall art" to GeometryFamily.FRAMED_PANEL,
    "tent poles" to GeometryFamily.THIN_BUNDLE, "garden hose" to GeometryFamily.CABLE_COIL,
    // Kitchen. Several of these end in a word that means something else on its own — a
    // watering "can", a coffee "maker", a baking "pan" — so they have to be matched whole.
    "coffee maker" to GeometryFamily.COFFEE_MAKER, "coffee machine" to GeometryFamily.COFFEE_MAKER,
    "espresso machine" to GeometryFamily.COFFEE_MAKER, "toaster oven" to GeometryFamily.MICROWAVE,
    "air fryer" to GeometryFamily.AIR_FRYER, "mini oven" to GeometryFamily.MICROWAVE,
    "frying pan" to GeometryFamily.FRYING_PAN, "baking pan" to GeometryFamily.SHALLOW_TRAY,
    "baking sheet" to GeometryFamily.SHALLOW_TRAY, "baking tray" to GeometryFamily.SHALLOW_TRAY,
    "roasting pan" to GeometryFamily.SHALLOW_TRAY, "roasting tin" to GeometryFamily.SHALLOW_TRAY,
    "cake tin" to GeometryFamily.SHALLOW_TRAY, "ice cube tray" to GeometryFamily.SHALLOW_TRAY,
    "dish rack" to GeometryFamily.DISH_RACK, "drying rack" to GeometryFamily.DISH_RACK,
    "stock pot" to GeometryFamily.COOKING_POT, "cooking pot" to GeometryFamily.COOKING_POT,
    "sauce pan" to GeometryFamily.COOKING_POT, "dutch oven" to GeometryFamily.COOKING_POT,
    "slow cooker" to GeometryFamily.COOKING_POT, "rice cooker" to GeometryFamily.COOKING_POT,
    "pressure cooker" to GeometryFamily.COOKING_POT, "mixing bowl" to GeometryFamily.BOWL,
    "cutting board" to GeometryFamily.SLIM_SLAB, "chopping board" to GeometryFamily.SLIM_SLAB,
    "rolling pin" to GeometryFamily.LYING_CYLINDER, "paper towel" to GeometryFamily.LYING_CYLINDER,
    "paper towels" to GeometryFamily.LYING_CYLINDER, "toilet roll" to GeometryFamily.LYING_CYLINDER,
    "hand mixer" to GeometryFamily.POWER_DRILL, "lazy susan" to GeometryFamily.DISC,
    "mini fridge" to GeometryFamily.UPRIGHT_FRIDGE, "wine cooler" to GeometryFamily.UPRIGHT_FRIDGE,
    // Rooms.
    "chest of drawers" to GeometryFamily.CHEST_OF_DRAWERS, "bedside table" to GeometryFamily.CHEST_OF_DRAWERS,
    "bedside cabinet" to GeometryFamily.CHEST_OF_DRAWERS, "filing cabinet" to GeometryFamily.FILING_CABINET,
    "shoe rack" to GeometryFamily.BOOKCASE, "shelving unit" to GeometryFamily.BOOKCASE,
    "coat rack" to GeometryFamily.CLOTHES_RAIL, "coat stand" to GeometryFamily.CLOTHES_RAIL,
    "hat stand" to GeometryFamily.CLOTHES_RAIL, "clothes rail" to GeometryFamily.CLOTHES_RAIL,
    "clothes rack" to GeometryFamily.CLOTHES_RAIL, "garment rack" to GeometryFamily.CLOTHES_RAIL,
    "ironing board" to GeometryFamily.IRONING_BOARD, "bean bag" to GeometryFamily.BEAN_BAG,
    "bed sheets" to GeometryFamily.FOLDED_STACK, "bath mat" to GeometryFamily.FOLDED_STACK,
    "wall clock" to GeometryFamily.DISC, "room divider" to GeometryFamily.FRAMED_PANEL,
    "flat screen" to GeometryFamily.MONITOR, "laundry basket" to GeometryFamily.LAUNDRY_BASKET,
    "air conditioner" to GeometryFamily.APPLIANCE_SLAB,
    // Cleaning, tools, garden.
    "vacuum cleaner" to GeometryFamily.UPRIGHT_VACUUM, "watering can" to GeometryFamily.WATERING_CAN,
    "hair dryer" to GeometryFamily.HAIR_DRYER, "glue gun" to GeometryFamily.POWER_DRILL,
    "heat gun" to GeometryFamily.POWER_DRILL, "leaf blower" to GeometryFamily.POWER_DRILL,
    "power drill" to GeometryFamily.POWER_DRILL, "electric screwdriver" to GeometryFamily.POWER_DRILL,
    "tape measure" to GeometryFamily.SMALL_CARTON, "first aid kit" to GeometryFamily.TOOLBOX,
    "cleaning caddy" to GeometryFamily.TOOLBOX, "sewing box" to GeometryFamily.TOOLBOX,
    "extension cord" to GeometryFamily.CABLE_COIL, "extension lead" to GeometryFamily.CABLE_COIL,
    "fire extinguisher" to GeometryFamily.BOTTLE, "dish soap" to GeometryFamily.BOTTLE,
    "trash can" to GeometryFamily.PEDAL_BIN, "waste bin" to GeometryFamily.PEDAL_BIN,
    "hockey stick" to GeometryFamily.LONG_HANDLE, "walking stick" to GeometryFamily.LONG_HANDLE,
    "fishing rod" to GeometryFamily.LONG_HANDLE, "curtain rod" to GeometryFamily.LONG_HANDLE,
    "golf club" to GeometryFamily.LONG_HANDLE, "step stool" to GeometryFamily.STOOL,
    "bar stool" to GeometryFamily.STOOL,
    "stand mixer" to GeometryFamily.STAND_MIXER, "food mixer" to GeometryFamily.STAND_MIXER,
    "knife block" to GeometryFamily.KNIFE_BLOCK, "wine glass" to GeometryFamily.WINE_GLASS, "wine glasses" to GeometryFamily.WINE_GLASS,
    "desk lamp" to GeometryFamily.DESK_LAMP, "reading lamp" to GeometryFamily.DESK_LAMP,
    "alarm clock" to GeometryFamily.ALARM_CLOCK, "storage box" to GeometryFamily.STORAGE_BIN,
    "storage bin" to GeometryFamily.STORAGE_BIN, "plastic box" to GeometryFamily.STORAGE_BIN,
    "golf bag" to GeometryFamily.GOLF_BAG, "golf clubs" to GeometryFamily.GOLF_BAG,
    "gas bottle" to GeometryFamily.GAS_CYLINDER, "gas cylinder" to GeometryFamily.GAS_CYLINDER,
    "car seat" to GeometryFamily.CHILD_CAR_SEAT, "high chair" to GeometryFamily.HIGH_CHAIR,
    "office chair" to GeometryFamily.OFFICE_CHAIR, "desk chair" to GeometryFamily.OFFICE_CHAIR,
    "gaming chair" to GeometryFamily.OFFICE_CHAIR, "swivel chair" to GeometryFamily.OFFICE_CHAIR,
    "side table" to GeometryFamily.SIDE_TABLE, "end table" to GeometryFamily.SIDE_TABLE,
    "coffee table" to GeometryFamily.COFFEE_TABLE, "robot vacuum" to GeometryFamily.ROBOT_VACUUM,
    "sewing machine" to GeometryFamily.SEWING_MACHINE, "paint can" to GeometryFamily.PAINT_CAN,
    "paint tin" to GeometryFamily.PAINT_CAN, "pet carrier" to GeometryFamily.PET_CARRIER,
    "cat carrier" to GeometryFamily.PET_CARRIER, "dog bed" to GeometryFamily.DOG_BED,
    "pet bed" to GeometryFamily.DOG_BED, "cat bed" to GeometryFamily.DOG_BED,
    "fish tank" to GeometryFamily.AQUARIUM, "potted plant" to GeometryFamily.POTTED_PLANT,
    "house plant" to GeometryFamily.POTTED_PLANT, "coffee mug" to GeometryFamily.MUG,
    "ski boots" to GeometryFamily.BOOT, "rain boots" to GeometryFamily.BOOT,
    // Personal.
    "shopping bag" to GeometryFamily.TOTE_BAG, "tote bag" to GeometryFamily.TOTE_BAG,
    "laptop bag" to GeometryFamily.TOTE_BAG, "bass guitar" to GeometryFamily.GUITAR,
    "hard hat" to GeometryFamily.HELMET, "games console" to GeometryFamily.SMALL_CARTON,
    "can opener" to GeometryFamily.HAND_TOOL, "box cutter" to GeometryFamily.HAND_TOOL,
    "toilet brush" to GeometryFamily.LONG_HANDLE, "bottled water" to GeometryFamily.BOTTLE,
    "smoke alarm" to GeometryFamily.DISC, "oven mitt" to GeometryFamily.SOFT_POUCH,
    "oven mitts" to GeometryFamily.SOFT_POUCH,
)

private val WORDS: Map<String, GeometryFamily> = buildMap {
    fun put(family: GeometryFamily, vararg words: String) = words.forEach { put(it, family) }
    put(GeometryFamily.FLAT_RECTANGLE, "book", "notebook", "notepad", "diary", "folder", "binder", "magazine")
    put(GeometryFamily.SLIM_SLAB, "phone", "smartphone", "tablet", "ipad", "laptop", "kindle", "ereader")
    put(GeometryFamily.SMALL_CARTON, "box", "carton", "parcel", "package")
    put(GeometryFamily.UPRIGHT_CYLINDER, "can", "tin", "jar", "candle", "canister", "flask", "thermos", "tube")
    put(GeometryFamily.BOTTLE, "bottle")
    put(GeometryFamily.TIGHT_ROLL, "rug", "carpet", "sleepingbag")
    put(GeometryFamily.SOFT_POUCH, "pouch", "washbag")
    put(GeometryFamily.CABLE_COIL, "cable", "hose", "coil")
    put(GeometryFamily.THIN_BUNDLE, "cutlery", "poles", "rods")
    put(GeometryFamily.SHALLOW_TRAY, "tray", "organiser", "organizer")
    put(GeometryFamily.SUITCASE, "suitcase", "luggage")
    put(GeometryFamily.DUFFEL_BAG, "duffel", "duffle", "holdall")
    put(GeometryFamily.BACKPACK, "backpack", "rucksack", "knapsack", "daypack", "schoolbag")
    put(GeometryFamily.COOLER_BOX, "cooler", "coolbox", "esky")
    put(GeometryFamily.TOOLBOX, "toolbox")
    put(GeometryFamily.BALL, "ball", "football", "basketball", "volleyball", "globe")
    put(GeometryFamily.CRATE, "crate")
    put(GeometryFamily.APPLIANCE_SLAB, "washer", "dryer", "dishwasher")
    put(GeometryFamily.UPRIGHT_FRIDGE, "fridge", "refrigerator", "freezer")
    put(GeometryFamily.MATTRESS, "mattress")
    put(GeometryFamily.PLANK_STACK, "plank", "timber", "lumber", "floorboards", "decking")
    put(GeometryFamily.LADDER, "ladder", "stepladder")
    put(GeometryFamily.BARREL, "barrel", "keg", "cask", "drum")
    put(GeometryFamily.BICYCLE, "bicycle", "bike", "ebike")
    put(GeometryFamily.LAWNMOWER, "lawnmower", "mower")
    put(GeometryFamily.SOFA, "sofa", "couch", "settee", "loveseat")
    put(GeometryFamily.ARMCHAIR, "armchair", "recliner")
    put(GeometryFamily.DINING_TABLE, "table")
    put(GeometryFamily.CHAIR, "chair", "highchair")
    put(GeometryFamily.WARDROBE, "wardrobe", "armoire")
    put(GeometryFamily.BED_FRAME, "bed", "bedframe", "divan")
    put(GeometryFamily.LAMP, "lamp")
    put(GeometryFamily.PLANT_POT, "plant", "planter", "flowerpot", "pot", "cup", "mug", "tumbler", "bucket")
    put(GeometryFamily.PIANO, "piano")

    // Kitchen.
    put(GeometryFamily.KETTLE, "kettle", "teapot")
    put(GeometryFamily.COFFEE_MAKER, "coffeemaker", "percolator")
    put(GeometryFamily.MICROWAVE, "microwave")
    put(GeometryFamily.TOASTER, "toaster")
    put(GeometryFamily.COOKER, "cooker", "stove", "oven", "range", "hob")
    put(GeometryFamily.COOKING_POT, "saucepan", "stockpot", "casserole", "crockpot")
    put(GeometryFamily.FRYING_PAN, "pan", "skillet", "wok", "frypan", "griddle")
    put(GeometryFamily.PLATE_STACK, "plate", "dish", "dishes", "crockery", "dinnerware", "saucer", "platter")
    put(GeometryFamily.BOWL, "bowl", "colander", "sieve")
    put(GeometryFamily.HAND_TOOL, "spoon", "fork", "knife", "knives", "ladle", "spatula", "whisk", "tongs")
    put(GeometryFamily.BOTTLE, "vase", "blender", "extinguisher", "spray", "cleaner", "detergent", "bleach",
        "shampoo", "soap", "sunscreen", "lotion", "repellent", "disinfectant")
    put(GeometryFamily.UPRIGHT_CYLINDER, "glass", "bin", "paint", "battery", "batteries")
    put(GeometryFamily.LYING_CYLINDER, "flashlight", "torch")
    // Bedroom and living room.
    put(GeometryFamily.PILLOW, "pillow", "cushion", "beanbag")
    put(GeometryFamily.FOLDED_STACK, "blanket", "duvet", "comforter", "quilt", "throw", "towel", "towels",
        "sheets", "bedding", "linen", "linens", "clothes", "clothing", "laundry", "shirt", "tshirt", "blouse",
        "sweater", "jumper", "hoodie", "cardigan", "trousers", "pants", "jeans", "shorts", "skirt", "dress",
        "jacket", "coat", "socks", "underwear", "pyjamas", "pajamas", "uniform", "curtains", "curtain")
    put(GeometryFamily.BOOKCASE, "bookcase", "bookshelf", "bookshelves", "shelf", "shelves", "shelving")
    put(GeometryFamily.CHEST_OF_DRAWERS, "drawers", "dresser", "cupboard", "cabinet", "sideboard",
        "nightstand", "credenza", "bureau", "tallboy", "commode")
    put(GeometryFamily.DESK, "desk", "workstation")
    put(GeometryFamily.STOOL, "stool", "barstool")
    put(GeometryFamily.OTTOMAN, "ottoman", "pouf", "pouffe", "footstool", "bench")
    put(GeometryFamily.FRAMED_PANEL, "mirror", "picture", "painting", "artwork", "frame", "whiteboard",
        "blackboard", "chalkboard", "corkboard", "noticeboard", "canvas", "poster", "door", "window", "headboard")
    put(GeometryFamily.DISC, "clock", "frisbee", "record", "vinyl", "cymbal")
    put(GeometryFamily.CRATE, "basket", "hamper")
    put(GeometryFamily.LAMP, "fan")
    put(GeometryFamily.BED_FRAME, "crib", "cot", "cradle")
    put(GeometryFamily.MATTRESS, "futon")
    put(GeometryFamily.WARDROBE, "closet")
    // Office and electronics.
    put(GeometryFamily.MONITOR, "monitor", "screen", "tv", "television", "telly", "computer", "imac")
    put(GeometryFamily.PRINTER, "printer", "scanner", "copier")
    put(GeometryFamily.SLIM_SLAB, "keyboard", "chromebook", "macbook")
    put(GeometryFamily.SMALL_CARTON, "speaker", "router", "modem", "console", "playstation", "xbox", "shoebox")
    // Personal.
    put(GeometryFamily.HELMET, "helmet", "hardhat")
    put(GeometryFamily.SHOE, "shoe", "boot", "trainer", "sneaker", "sandal", "slipper", "heels", "loafer")
    put(GeometryFamily.TOTE_BAG, "bag", "handbag", "purse", "tote", "satchel")
    put(GeometryFamily.SOFT_POUCH, "wallet")
    put(GeometryFamily.SUITCASE, "briefcase", "trolley")
    put(GeometryFamily.GUITAR, "guitar", "violin", "ukulele", "viola", "cello", "banjo", "mandolin")
    // Cleaning, tools and garden.
    put(GeometryFamily.UPRIGHT_VACUUM, "vacuum", "hoover")
    put(GeometryFamily.LONG_HANDLE, "broom", "mop", "rake", "shovel", "spade", "hoe", "umbrella", "duster",
        "squeegee", "pole", "rod", "stick", "bat", "racket", "racquet", "oar", "paddle")
    put(GeometryFamily.POWER_DRILL, "drill", "sander", "jigsaw", "grinder", "hairdryer", "blower")
    put(GeometryFamily.HAND_TOOL, "hammer", "spanner", "wrench", "screwdriver", "pliers", "mallet", "axe",
        "hatchet", "chisel", "trowel", "saw", "secateurs", "scissors")
    put(GeometryFamily.CABLE_COIL, "cord", "rope", "lead", "wire")
    put(GeometryFamily.TOOLBOX, "caddy", "kit")
    put(GeometryFamily.BARREL, "butt")
    // Desk and stationery.
    put(GeometryFamily.LYING_CYLINDER, "pen", "pencil", "marker", "highlighter")
    put(GeometryFamily.HAND_TOOL, "toothbrush", "hairbrush", "brush")
    put(GeometryFamily.SLIM_SLAB, "ruler")
    put(GeometryFamily.FLAT_RECTANGLE, "paper", "ream", "envelope", "envelopes")
    put(GeometryFamily.DISC, "tape")
    put(GeometryFamily.BOTTLE, "glue")
    put(GeometryFamily.COOLER_BOX, "container", "tupperware")

    // Real-size shapes. Later entries win, so these replace the rougher stand-ins above: a
    // cup is drawn as a mug with a handle now, not a flower pot.
    put(GeometryFamily.MUG, "mug", "cup", "tumbler", "beaker")
    put(GeometryFamily.WINE_GLASS, "goblet", "flute")
    put(GeometryFamily.VASE, "vase", "urn")
    put(GeometryFamily.TEAPOT, "teapot")
    put(GeometryFamily.BLENDER, "blender", "smoothie", "liquidiser", "liquidizer")
    put(GeometryFamily.STAND_MIXER, "mixer", "kitchenaid")
    put(GeometryFamily.AIR_FRYER, "airfryer")
    put(GeometryFamily.BUCKET, "bucket", "pail")
    put(GeometryFamily.PEDAL_BIN, "bin", "dustbin", "wastebasket", "trashcan")
    put(GeometryFamily.LAUNDRY_BASKET, "basket", "hamper")
    put(GeometryFamily.CLOTHES_IRON, "iron")
    put(GeometryFamily.PEDESTAL_FAN, "fan")
    put(GeometryFamily.OIL_HEATER, "heater", "radiator")
    put(GeometryFamily.SPEAKER, "speaker", "speakers", "subwoofer", "soundbar")
    put(GeometryFamily.CAMERA, "camera", "dslr", "camcorder")
    put(GeometryFamily.HEADPHONES, "headphones", "headset", "earmuffs")
    put(GeometryFamily.BOOK_STACK, "books")
    put(GeometryFamily.POTTED_PLANT, "plant", "houseplant", "succulent", "cactus", "fern", "orchid")
    put(GeometryFamily.UMBRELLA, "umbrella", "parasol")
    put(GeometryFamily.STORAGE_BIN, "tub", "container", "tote")
    put(GeometryFamily.BOOT, "boot", "wellies", "wellington", "wellingtons")
    put(GeometryFamily.HAIR_DRYER, "hairdryer", "blowdryer")
    put(GeometryFamily.SKATEBOARD, "skateboard", "longboard")
    put(GeometryFamily.KICK_SCOOTER, "scooter")
    put(GeometryFamily.TENNIS_RACKET, "racket", "racquet")
    put(GeometryFamily.SNOWBOARD, "snowboard", "skis", "ski", "wakeboard")
    put(GeometryFamily.LANTERN, "lantern")
    put(GeometryFamily.GAS_CYLINDER, "propane", "butane")
    put(GeometryFamily.STROLLER, "stroller", "pram", "pushchair", "buggy")
    put(GeometryFamily.BEAN_BAG, "beanbag")
    put(GeometryFamily.ROBOT_VACUUM, "roomba")
    put(GeometryFamily.DUMBBELL, "dumbbell", "dumbbells", "barbell", "weights")
    put(GeometryFamily.KETTLEBELL, "kettlebell")
    put(GeometryFamily.WHEELBARROW, "wheelbarrow", "barrow")
    put(GeometryFamily.BBQ_GRILL, "bbq", "barbecue", "barbeque", "grill")
    put(GeometryFamily.AQUARIUM, "aquarium", "terrarium", "vivarium")
    put(GeometryFamily.HIGH_CHAIR, "highchair")
}
