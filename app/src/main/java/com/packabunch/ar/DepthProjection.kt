package com.packabunch.ar

/** Pinhole calibration in depth-image pixels. ARCore camera looks along -Z, image Y points down. */
data class DepthProjection(val fx: Float, val fy: Float, val cx: Float, val cy: Float) {
    init { require(fx > 0 && fy > 0) }
    fun point(u: Int, v: Int, depthMm: Int): FloatArray {
        val z = depthMm / 1000f
        return floatArrayOf((u-cx)*z/fx, -(v-cy)*z/fy, -z)
    }
    companion object {
        fun scaled(fx:Float,fy:Float,cx:Float,cy:Float,imageWidth:Int,imageHeight:Int,depthWidth:Int,depthHeight:Int):DepthProjection {
            require(imageWidth>0 && imageHeight>0 && depthWidth>0 && depthHeight>0)
            val sx=depthWidth.toFloat()/imageWidth; val sy=depthHeight.toFloat()/imageHeight
            return DepthProjection(fx*sx,fy*sy,cx*sx,cy*sy)
        }
    }
}
