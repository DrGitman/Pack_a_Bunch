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
 * The ~38 generic item shapes — a bottle, a chair, a sofa — drawn in the plan and the packing
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
    private class Canonical(val points: FloatArray, val normals: FloatArray) {
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
        for (f in 0 until faces.length()) {
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
        Canonical(points, normals)
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
            SurfaceFace(pts, side = 5, normal = SurfacePoint(n[0] / len, n[1] / len, n[2] / len))
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
            family = if (named != null && named in ROUND) named else formFamily(measured.family)
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
    if (f in THIN_IN_HEIGHT) {
        val thinnest = (0..2).minBy { size[it] }
        if (thinnest != 2 && size[thinnest] * 1.2f < size[2]) swap(2, axes.indexOf(thinnest))
    }
    if (f in LONG_IN_WIDTH && size[axes[1]] > 1.15f * size[axes[0]]) swap(0, 1)
    if (f in LONG_IN_DEPTH && size[axes[0]] > 1.15f * size[axes[1]]) swap(0, 1)
    return FamilyChoice(f, axes.toList(), flipZ && f == GeometryFamily.PLANT_POT)
}

private val ROUND = setOf(
    GeometryFamily.UPRIGHT_CYLINDER, GeometryFamily.LYING_CYLINDER, GeometryFamily.BOTTLE,
    GeometryFamily.TIGHT_ROLL, GeometryFamily.CABLE_COIL, GeometryFamily.YOGA_MAT, GeometryFamily.BALL,
    GeometryFamily.BARREL, GeometryFamily.LAMP, GeometryFamily.PLANT_POT, GeometryFamily.DUFFEL_BAG,
)

private val FITTED_ROUND = setOf(
    GeometryFamily.UPRIGHT_CYLINDER, GeometryFamily.LYING_CYLINDER, GeometryFamily.BALL, GeometryFamily.PLANT_POT,
)

private val THIN_IN_HEIGHT = setOf(GeometryFamily.FLAT_RECTANGLE, GeometryFamily.SLIM_SLAB, GeometryFamily.MATTRESS)

private val LONG_IN_WIDTH = setOf(
    GeometryFamily.LYING_CYLINDER, GeometryFamily.TIGHT_ROLL, GeometryFamily.DUFFEL_BAG, GeometryFamily.YOGA_MAT,
    GeometryFamily.SOFT_POUCH, GeometryFamily.THIN_BUNDLE, GeometryFamily.LADDER, GeometryFamily.MATTRESS,
    GeometryFamily.BICYCLE, GeometryFamily.SOFA, GeometryFamily.PIANO, GeometryFamily.PLANK_STACK,
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
    val end = words.indexOfFirst { it in CONNECTORS }.let { if (it > 0) it else words.size }
    val head = words[end - 1]
    return WORDS[head] ?: WORDS[head.removeSuffix("s")] ?: WORDS[head.removeSuffix("es")]
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
    "board game" to GeometryFamily.FLAT_RECTANGLE, "picture frame" to GeometryFamily.FLAT_RECTANGLE,
    "photo frame" to GeometryFamily.FLAT_RECTANGLE,
    "tent poles" to GeometryFamily.THIN_BUNDLE, "garden hose" to GeometryFamily.CABLE_COIL,
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
    put(GeometryFamily.DINING_TABLE, "table", "desk")
    put(GeometryFamily.CHAIR, "chair", "stool")
    put(GeometryFamily.WARDROBE, "wardrobe", "armoire")
    put(GeometryFamily.BED_FRAME, "bed", "bedframe", "divan")
    put(GeometryFamily.LAMP, "lamp")
    put(GeometryFamily.PLANT_POT, "plant", "planter", "flowerpot", "pot", "cup", "mug", "tumbler", "bucket")
    put(GeometryFamily.PIANO, "piano")
}
