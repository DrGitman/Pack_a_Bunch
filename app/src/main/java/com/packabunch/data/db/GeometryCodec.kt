package com.packabunch.data.db

import com.packabunch.packing.*
import java.io.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Versioned local geometry payloads. Corruption fails visibly rather than becoming a box. */
object GeometryCodec {
    fun plan(plan: PackingPlan): ByteArray = encode {
        writeInt(plan.placements.size)
        plan.placements.forEach { p ->
            writeUTF(p.instanceId); writeUTF(p.specId)
            writeInt(p.xMm); writeInt(p.yMm); writeInt(p.zMm)
            writeInt(p.orientedWidthMm); writeInt(p.orientedDepthMm); writeInt(p.orientedHeightMm)
            writeUTF(p.orientation.name); writeInt(p.sequenceIndex)
        }
        writeInt(plan.unplaced.size)
        plan.unplaced.forEach { p -> writeUTF(p.instanceId); writeUTF(p.specId); writeUTF(p.name); writeUTF(p.reason.name) }
    }
    fun restorePlan(bytes: ByteArray, shell: PackingPlan): PackingPlan = decode(bytes) {
        val placements = List(readInt().also { require(it in 0..10_000) }) {
            Placement(readUTF(),readUTF(),readInt(),readInt(),readInt(),readInt(),readInt(),readInt(),
                Orientation.valueOf(readUTF()),readInt())
        }
        val unplaced = List(readInt().also { require(it in 0..10_000) }) {
            UnplacedInstance(readUTF(),readUTF(),readUTF(),UnplacedReason.valueOf(readUTF()))
        }
        shell.copy(placements=placements,unplaced=unplaced)
    }
    private fun encode(block: DataOutputStream.() -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(GZIPOutputStream(bytes)).use { it.writeInt(1); it.block() }
        return bytes.toByteArray()
    }
    private fun <T> decode(bytes: ByteArray, block: DataInputStream.() -> T): T =
        DataInputStream(GZIPInputStream(ByteArrayInputStream(bytes))).use {
            require(it.readInt() == 1) { "Unsupported saved geometry version" }
            it.block()
        }
    private fun DataInputStream.count(): Int = readInt().also { require(it in 1..2_000_000) }
    fun shape(shape: ItemShape?): ByteArray? = (shape as? ItemShape.VoxelMask)?.let { mask -> encode {
        writeInt(mask.resolutionMm); writeInt(mask.countX); writeInt(mask.countY); writeInt(mask.countZ)
        for (x in 0 until mask.countX) for (y in 0 until mask.countY) for (z in 0 until mask.countZ)
            writeBoolean(mask.isOccupied(x, y, z))
    } }
    fun shape(bytes: ByteArray?): ItemShape.VoxelMask? = bytes?.let { decode(it) {
        val r = count(); val x = count(); val y = count(); val z = count()
        require(x.toLong() * y * z <= 2_000_000)
        ItemShape.VoxelMask(r, x, y, z, BooleanArray(x * y * z) { readBoolean() })
    } }
    fun scan(scan: ScannedSpace?): ByteArray? = scan?.let { encode {
        val g = it.baseGrid
        writeInt(g.originXMm); writeInt(g.originYMm); writeInt(g.originZMm)
        writeInt(g.resolutionMm); writeInt(g.countX); writeInt(g.countY); writeInt(g.countZ)
        for (x in 0 until g.countX) for (y in 0 until g.countY) for (z in 0 until g.countZ)
            writeByte(g.cellAt(x, y, z).ordinal)
        writeBoolean(it.unknownIsSolid)
        writeBoolean(it.opening != null)
        it.opening?.let { o -> writeInt(o.widthMm); writeInt(o.heightMm); writeUTF(o.source.name) }
        writeInt(it.obstructions.size)
        it.obstructions.forEach { o ->
            writeUTF(o.id); writeUTF(o.label); writeUTF(o.kind.name); writeBoolean(o.includedInPack)
            writeInt(o.cellIndices.size); o.cellIndices.forEach { n -> writeInt(n) }
        }
    } }
    fun scan(bytes: ByteArray?): ScannedSpace? = bytes?.let { decode(it) {
        val ox = readInt(); val oy = readInt(); val oz = readInt()
        val r = count(); val x = count(); val y = count(); val z = count()
        require(x.toLong() * y * z <= 2_000_000)
        val cells = ByteArray(x * y * z) { readByte().also { require(it in 0..2) } }
        val grid = VoxelGrid(ox, oy, oz, r, x, y, z, cells)
        val unknown = readBoolean()
        val opening = if (readBoolean()) Opening(readInt(), readInt(), MeasurementSource.valueOf(readUTF())) else null
        val n = readInt().also { require(it in 0..10000) }
        val obstructions = List(n) {
            val id = readUTF(); val label = readUTF(); val kind = ObstructionKind.valueOf(readUTF()); val included = readBoolean()
            val size = readInt().also { require(it in 0..cells.size) }
            Obstruction(id, label, kind, IntArray(size) { readInt().also { require(it in cells.indices) } }, included)
        }
        ScannedSpace(grid, obstructions, opening, unknown)
    } }
}
