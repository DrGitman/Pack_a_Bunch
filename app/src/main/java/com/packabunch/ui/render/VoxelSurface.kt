package com.packabunch.ui.render

import com.packabunch.packing.*

data class SurfacePoint(val x: Float, val y: Float, val z: Float)
data class SurfaceFace(val points: List<SurfacePoint>, val side: Int)

/** Only exposed faces, not six cubes per solid voxel. Coordinates remain metric. */
fun voxelSurface(nx: Int, ny: Int, nz: Int, resolution: Int,
    occupied: (Int, Int, Int) -> Boolean): List<SurfaceFace> = buildList {
    val directions = arrayOf(intArrayOf(-1,0,0), intArrayOf(1,0,0), intArrayOf(0,-1,0),
        intArrayOf(0,1,0), intArrayOf(0,0,-1), intArrayOf(0,0,1))
    for (x in 0 until nx) for (y in 0 until ny) for (z in 0 until nz) {
        if (!occupied(x,y,z)) continue
        val a=x*resolution.toFloat(); val b=y*resolution.toFloat(); val c=z*resolution.toFloat()
        val r=resolution.toFloat()
        val corners=listOf(SurfacePoint(a,b,c), SurfacePoint(a+r,b,c), SurfacePoint(a+r,b+r,c), SurfacePoint(a,b+r,c),
            SurfacePoint(a,b,c+r), SurfacePoint(a+r,b,c+r), SurfacePoint(a+r,b+r,c+r), SurfacePoint(a,b+r,c+r))
        val faces=arrayOf(intArrayOf(0,3,7,4),intArrayOf(1,2,6,5),intArrayOf(0,1,5,4),
            intArrayOf(3,2,6,7),intArrayOf(0,1,2,3),intArrayOf(4,5,6,7))
        directions.forEachIndexed { side, d ->
            val i=x+d[0]; val j=y+d[1]; val k=z+d[2]
            if (i !in 0 until nx || j !in 0 until ny || k !in 0 until nz || !occupied(i,j,k))
                add(SurfaceFace(faces[side].map { corners[it] },side))
        }
    }
}

fun SurfacePoint.placed(placement: Placement): SurfacePoint {
    fun along(axis: Axis) = when(axis) { Axis.WIDTH -> x; Axis.DEPTH -> y; Axis.HEIGHT -> z }
    return SurfacePoint(along(placement.orientation.alongX)+placement.xMm,
        along(placement.orientation.alongY)+placement.yMm, along(placement.orientation.alongZ)+placement.zMm)
}
