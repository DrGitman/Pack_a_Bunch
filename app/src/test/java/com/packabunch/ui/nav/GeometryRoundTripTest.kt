package com.packabunch.data.db

import com.packabunch.data.Project
import com.packabunch.packing.*
import com.packabunch.ui.render.*
import org.junit.Assert.*
import org.junit.Test

class GeometryRoundTripTest {
    @Test fun `scan item masks unknown cells opening and obstruction survive stored project`() {
        val grid = VoxelGrid(0,0,0,20,3,2,2,byteArrayOf(0,1,2,0,0,0,0,0,0,0,0,0))
        val scan = ScannedSpace(grid, listOf(Obstruction("shelf","Shelf",ObstructionKind.REMOVABLE,intArrayOf(4),false)),Opening(100,80))
        val mask = ItemShape.VoxelMask(20,2,2,1,booleanArrayOf(true,true,true,false))
        val project = Project("p","Scan",Space("s","Scan",Dimensions(60,40,40),scan=scan),
            listOf(ItemSpec("i","Object",mask.boundsMm,measurementSource=MeasurementSource.CAMERA_ESTIMATE,shape=mask,visualShape=mask)))
        val restored = StoredProject(project.toEntity(), project.toItemEntities(),null).toProject()
        assertEquals(project.request.revision(),restored.request.revision())
        assertEquals(scan.opening,restored.space.scan!!.opening)
        assertEquals(scan.obstructions,restored.space.scan!!.obstructions)
        assertEquals(Cell.UNKNOWN,restored.space.scan!!.baseGrid.cellAt(0,1,0))
        assertEquals(3,restored.items.single().visualShape!!.filledCells)
        assertFalse(restored.items.single().visualShape!!.isOccupied(1,1,0))
    }
    @Test fun `old rows without geometry remain typed boxes`() {
        val p = Project("p","Box",Space("s","Box",Dimensions(100,100,100)))
        val restored = StoredProject(p.toEntity(),emptyList(),null).toProject()
        assertNull(restored.space.scan)
    }
    @Test fun `surface removes shared interior faces and preserves concavity`() {
        val faces=voxelSurface(2,2,1,20) { x,y,_ -> !(x==1 && y==1) }
        assertTrue(faces.size < 14)
        val area=faces.sumOf { face ->
            fun distance(a:SurfacePoint,b:SurfacePoint)=kotlin.math.sqrt(((a.x-b.x)*(a.x-b.x)+(a.y-b.y)*(a.y-b.y)+(a.z-b.z)*(a.z-b.z)).toDouble())
            distance(face.points[0],face.points[1])*distance(face.points[1],face.points[2])
        }
        assertEquals(14.0*400,area,0.001)
        assertFalse(faces.any { f -> f.points.all { it.x > 20 && it.y > 20 } })
    }
    @Test fun `all six placement orientations map surfaces inside oriented bounds`() {
        val d=Dimensions(20,40,60)
        for(o in Orientation.entries) {
            val oriented=o.apply(d)
            val p=Placement("i","s",100,200,300,oriented.widthMm,oriented.depthMm,oriented.heightMm,o,0)
            val point=SurfacePoint(20f,40f,60f).placed(p)
            assertEquals(100f+oriented.widthMm,point.x)
            assertEquals(200f+oriented.depthMm,point.y)
            assertEquals(300f+oriented.heightMm,point.z)
        }
    }
}
