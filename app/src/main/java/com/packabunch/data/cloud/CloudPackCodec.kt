package com.packabunch.data.cloud

import com.packabunch.data.Project
import com.packabunch.data.db.GeometryCodec
import com.packabunch.packing.*
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID

/** Relational wire adapter. Original identities survive so input revisions stay meaningful. */
object CloudPackCodec {
    val tables = listOf("pack_geometry", "pack_spaces", "pack_openings", "pack_obstructions", "pack_items",
        "pack_plans", "pack_plan_instances", "pack_placements", "pack_unplaced", "pack_progress")
    fun cloudId(owner: String, id: String): String = UUID.nameUUIDFromBytes("$owner/pack/$id".toByteArray()).toString()
    private fun id(pack: String, kind: String, local: String) = UUID.nameUUIDFromBytes("$pack/$kind/$local".toByteArray()).toString()
    private fun obj(vararg pairs: Pair<String, Any?>) = JSONObject().apply { pairs.forEach { put(it.first, it.second ?: JSONObject.NULL) } }
    private fun dims(d: Dimensions) = arrayOf("width_mm" to d.widthMm, "depth_mm" to d.depthMm, "height_mm" to d.heightMm)
    private fun dimensions(j: JSONObject) = Dimensions(j.getInt("width_mm"), j.getInt("depth_mm"), j.getInt("height_mm"))
    private fun hex(bytes: ByteArray) = "\\x" + bytes.joinToString("") { "%02x".format(it) }
    private fun bytes(hex: String): ByteArray {
        require(hex.startsWith("\\x") && hex.length <= 33_554_434 && hex.length % 2 == 0)
        return hex.substring(2).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    private fun rows(doc: JSONObject, table: String): List<JSONObject> = doc.getJSONArray(table).let { a -> List(a.length()) { a.getJSONObject(it) } }

    fun encode(project: Project, owner: String): JSONObject {
        val pack = cloudId(owner, project.id)
        val doc = obj("pack" to obj("name" to project.name, "client_id" to project.id))
        tables.forEach { doc.put(it, JSONArray()) }
        fun add(table: String, row: JSONObject) { doc.getJSONArray(table).put(row.put("pack_id", pack)) }
        fun geometry(kind: String, local: String, payload: ByteArray?): String? {
            if (payload == null) return null
            val gid = id(pack, kind, local)
            add("pack_geometry", obj("id" to gid, "kind" to kind, "codec_version" to 1, "payload" to hex(payload)))
            return gid
        }
        val space = project.space
        val scan = space.scan
        val scanId = geometry("space_scan", space.id, GeometryCodec.scan(scan?.copy(obstructions=emptyList(), opening=null)))
        add("pack_spaces", obj(*dims(space.dimensions), "client_id" to space.id, "name" to space.name,
            "edge_gap_mm" to space.edgeGapMm, "measurement_source" to space.measurementSource.name,
            "scan_geometry_id" to scanId, "unknown_is_solid" to (scan?.unknownIsSolid ?: true)))
        scan?.opening?.let { add("pack_openings", obj("width_mm" to it.widthMm, "height_mm" to it.heightMm, "measurement_source" to it.source.name)) }
        scan?.obstructions?.forEach { o ->
            val buffer = ByteBuffer.allocate(4 + 4 * o.cellIndices.size).putInt(1)
            o.cellIndices.forEach { buffer.putInt(it) }
            add("pack_obstructions", obj("id" to id(pack,"obstruction",o.id), "client_id" to o.id,
                "label" to o.label, "kind" to o.kind.name, "included" to o.includedInPack,
                "geometry_id" to geometry("obstruction_mask",o.id,buffer.array())))
        }
        project.items.forEachIndexed { index, item ->
            add("pack_items",obj(*dims(item.dimensions), "id" to id(pack,"item",item.id), "client_id" to item.id,
                "name" to item.name, "position" to index, "quantity" to item.quantity, "keep_upright" to item.keepUpright,
                "may_support_items" to item.maySupportItems, "measurement_source" to item.measurementSource.name,
                "collision_geometry_id" to geometry("collision_mask",item.id,GeometryCodec.shape(item.shape)),
                "visual_geometry_id" to geometry("visual_surface",item.id,GeometryCodec.shape(item.visualShape))))
        }
        project.currentPlan?.takeIf { PlanValidator.validate(project.request,it).isValid }?.let { plan ->
            val pid = id(pack,"plan",plan.inputRevision)
            add("pack_plans", obj("id" to pid,"input_revision" to plan.inputRevision,"solver_version" to plan.solverVersion,
                "strategy" to plan.strategy,"stopped_on_time_budget" to plan.stoppedOnTimeBudget,
                "usable_volume_mm3" to plan.metrics.usableVolumeMm3,"created_at" to Instant.ofEpochMilli(project.updatedAtMillis).toString()))
            fun instance(instance: String, spec: String, outcome: String) {
                add("pack_plan_instances",obj("plan_id" to pid,"instance_id" to instance,"item_id" to id(pack,"item",spec),"outcome" to outcome))
            }
            plan.placements.forEach { p ->
                instance(p.instanceId,p.specId,"placed")
                add("pack_placements",obj("plan_id" to pid,"instance_id" to p.instanceId,"outcome" to "placed",
                    "sequence_index" to p.sequenceIndex,"x_mm" to p.xMm,"y_mm" to p.yMm,"z_mm" to p.zMm,
                    "oriented_width_mm" to p.orientedWidthMm,"oriented_depth_mm" to p.orientedDepthMm,
                    "oriented_height_mm" to p.orientedHeightMm,"orientation" to p.orientation.name))
                if(p.instanceId in project.packedInstanceIds) add("pack_progress",obj("plan_id" to pid,"instance_id" to p.instanceId,"packed_at" to Instant.ofEpochMilli(project.updatedAtMillis).toString()))
            }
            plan.unplaced.forEach { u ->
                instance(u.instanceId,u.specId,"unplaced")
                add("pack_unplaced",obj("plan_id" to pid,"instance_id" to u.instanceId,"outcome" to "unplaced","reason" to u.reason.name))
            }
        }
        return doc
    }

    fun decode(doc: JSONObject): Project {
        val pack = doc.getJSONObject("pack")
        val geometries = rows(doc,"pack_geometry").associateBy { it.getString("id") }
        fun geometry(row: JSONObject, field: String, kind: String): ByteArray? {
            if(row.isNull(field)) return null
            val g = geometries.getValue(row.getString(field))
            require(g.getInt("codec_version")==1 && g.getString("kind")==kind)
            return bytes(g.getString("payload"))
        }
        val s = rows(doc,"pack_spaces").single()
        var scan = GeometryCodec.scan(geometry(s,"scan_geometry_id","space_scan"))
        if(scan!=null) {
            val size = scan.baseGrid.countX * scan.baseGrid.countY * scan.baseGrid.countZ
            val opening = rows(doc,"pack_openings").singleOrNull()?.let { Opening(it.getInt("width_mm"),it.getInt("height_mm"),MeasurementSource.valueOf(it.getString("measurement_source"))) }
            val obstructions = rows(doc,"pack_obstructions").map { o ->
                val b = ByteBuffer.wrap(requireNotNull(geometry(o,"geometry_id","obstruction_mask")))
                require(b.int==1 && b.remaining()%4==0 && b.remaining()/4<=size)
                val indices = IntArray(b.remaining()/4) { b.int.also { require(it in 0 until size) } }
                Obstruction(o.getString("client_id"),o.getString("label"),ObstructionKind.valueOf(o.getString("kind")),indices,o.getBoolean("included"))
            }
            scan = scan.copy(obstructions=obstructions,opening=opening,unknownIsSolid=s.getBoolean("unknown_is_solid"))
        }
        val itemRows = rows(doc,"pack_items").sortedBy { it.getInt("position") }
        val items = itemRows.map { i -> ItemSpec(i.getString("client_id"),i.getString("name"),dimensions(i),i.getInt("quantity"),
            i.getBoolean("keep_upright"),i.getBoolean("may_support_items"),MeasurementSource.valueOf(i.getString("measurement_source")),
            GeometryCodec.shape(geometry(i,"collision_geometry_id","collision_mask")),GeometryCodec.shape(geometry(i,"visual_geometry_id","visual_surface"))) }
        var project = Project(pack.getString("client_id"),pack.getString("name"),
            Space(s.getString("client_id"),s.getString("name"),dimensions(s),s.getInt("edge_gap_mm"),MeasurementSource.valueOf(s.getString("measurement_source")),scan),
            // Postgres writes the offset as "+00:00"; Instant.parse only accepts a bare "Z".
            items,updatedAtMillis=java.time.OffsetDateTime.parse(pack.getString("updated_at")).toInstant().toEpochMilli())
        val planRow = rows(doc,"pack_plans").singleOrNull() ?: return project
        val ids = itemRows.associate { it.getString("id") to it.getString("client_id") }
        val instances = rows(doc,"pack_plan_instances").associate { it.getString("instance_id") to ids.getValue(it.getString("item_id")) }
        val placements = rows(doc,"pack_placements").map { p -> Placement(p.getString("instance_id"),instances.getValue(p.getString("instance_id")),
            p.getInt("x_mm"),p.getInt("y_mm"),p.getInt("z_mm"),p.getInt("oriented_width_mm"),p.getInt("oriented_depth_mm"),p.getInt("oriented_height_mm"),Orientation.valueOf(p.getString("orientation")),p.getInt("sequence_index")) }.sortedBy { it.sequenceIndex }
        val unplaced = rows(doc,"pack_unplaced").map { u -> val spec = instances.getValue(u.getString("instance_id")); UnplacedInstance(u.getString("instance_id"),spec,items.single{it.id==spec}.name,UnplacedReason.valueOf(u.getString("reason"))) }
        val volume = placements.sumOf { it.volumeMm3 }
        val bounds = if(placements.isEmpty()) 0L else
            (placements.maxOf { it.xMm+it.orientedWidthMm }-placements.minOf { it.xMm }).toLong() *
            (placements.maxOf { it.yMm+it.orientedDepthMm }-placements.minOf { it.yMm }) *
            (placements.maxOf { it.zMm+it.orientedHeightMm }-placements.minOf { it.zMm })
        val plan = PackingPlan(project.space.id,planRow.getString("input_revision"),planRow.getInt("solver_version"),placements,unplaced,
            PackingMetrics(placements.size,project.pieceCount,volume,project.space.usableVolumeMm3,bounds),planRow.getString("strategy"),planRow.getBoolean("stopped_on_time_budget"))
        // Corrupt or stale cloud plans must never become a displayed arrangement.
        if(plan.inputRevision==project.request.revision() && PlanValidator.validate(project.request,plan).isValid) {
            project=project.copy(plan=plan,packedInstanceIds=rows(doc,"pack_progress").map { it.getString("instance_id") }.toSet().intersect(placements.map { it.instanceId }.toSet()))
        }
        return project
    }
}
