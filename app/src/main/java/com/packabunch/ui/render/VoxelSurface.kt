package com.packabunch.ui.render

import com.packabunch.packing.*

data class SurfacePoint(val x: Float, val y: Float, val z: Float)
data class SurfaceFace(val points: List<SurfacePoint>, val side: Int)

/** Greedy meshing joins coplanar voxel faces into clean panels, preserving every boundary. */
fun voxelSurface(nx: Int, ny: Int, nz: Int, resolution: Int,
    occupied: (Int, Int, Int) -> Boolean): List<SurfaceFace> = buildList {
    val sizes=intArrayOf(nx,ny,nz)
    for(side in 0..5) {
        val axis=side/2; val positive=side%2==1
        val u=(axis+1)%3; val v=(axis+2)%3
        for(layer in 0 until sizes[axis]) {
            val visible=BooleanArray(sizes[u]*sizes[v])
            fun at(a:Int,b:Int)=a*sizes[v]+b
            for(a in 0 until sizes[u]) for(b in 0 until sizes[v]) {
                val p=IntArray(3); p[axis]=layer; p[u]=a; p[v]=b
                if(!occupied(p[0],p[1],p[2])) continue
                p[axis]+=if(positive)1 else -1
                visible[at(a,b)]=p[axis] !in 0 until sizes[axis] || !occupied(p[0],p[1],p[2])
            }
            for(a in 0 until sizes[u]) for(b in 0 until sizes[v]) {
                if(!visible[at(a,b)]) continue
                var width=1
                while(a+width<sizes[u] && visible[at(a+width,b)]) width++
                var height=1
                while(b+height<sizes[v] && (a until a+width).all { visible[at(it,b+height)] }) height++
                for(i in a until a+width) for(j in b until b+height) visible[at(i,j)]=false
                fun point(i:Int,j:Int):SurfacePoint {
                    val p=FloatArray(3)
                    p[axis]=(layer+if(positive)1 else 0)*resolution.toFloat()
                    p[u]=i*resolution.toFloat(); p[v]=j*resolution.toFloat()
                    return SurfacePoint(p[0],p[1],p[2])
                }
                add(SurfaceFace(listOf(point(a,b),point(a+width,b),point(a+width,b+height),point(a,b+height)),side))
            }
        }
    }
}

fun SurfacePoint.placed(placement: Placement): SurfacePoint {
    fun along(axis: Axis) = when(axis) { Axis.WIDTH -> x; Axis.DEPTH -> y; Axis.HEIGHT -> z }
    return SurfacePoint(along(placement.orientation.alongX)+placement.xMm,
        along(placement.orientation.alongY)+placement.yMm, along(placement.orientation.alongZ)+placement.zMm)
}
