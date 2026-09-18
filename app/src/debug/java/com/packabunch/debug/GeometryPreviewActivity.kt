package com.packabunch.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.packabunch.packing.*
import com.packabunch.ui.render.IsometricCrate
import com.packabunch.ui.theme.*
import com.packabunch.data.db.GeometryCodec

/** Debug-only geometry fixture; never a claim that an emulator performed a scan. */
class GeometryPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val nx=30; val ny=20; val nz=15
        val grid=VoxelGrid(0,0,0,20,nx,ny,nz,ByteArray(nx*ny*nz) { index ->
            val x=index/(ny*nz); val y=index/nz%ny; val z=index%nz
            if(y==ny-1 || x==nx-1 || x<3 && y in 7..12 && z<5) 1 else 0
        })
        val scan=GeometryCodec.scan(GeometryCodec.scan(ScannedSpace(grid)))!!
        val bottle=ItemShape.VoxelMask(20,5,5,10,BooleanArray(250) { index ->
            val x=index/50; val y=index/10%5; val z=index%10
            val radius=if(z<7) 2.4 else 1.2
            (x-2)*(x-2)+(y-2)*(y-2) <= radius*radius
        })
        val chair=ItemShape.VoxelMask(20,8,8,10,BooleanArray(640) { index ->
            val x=index/80; val y=index/10%8; val z=index%10
            z==4 || y==7 && z>=4 || z<4 && (x==0 || x==7) && (y==0 || y==7)
        })
        val items=listOf(ItemSpec("b","Bottle",bottle.boundsMm,visualShape=GeometryCodec.shape(GeometryCodec.shape(bottle))),
            ItemSpec("c","Chair",chair.boundsMm,visualShape=GeometryCodec.shape(GeometryCodec.shape(chair))))
        val placements=items.mapIndexed { i,item -> Placement(item.id,item.id,120+i*200,100,0,
            item.dimensions.widthMm,item.dimensions.depthMm,item.dimensions.heightMm,Orientation.WIDTH_DEPTH_HEIGHT,i) }
        val fixtureSpace=Space("s","Fixture",Dimensions(600,400,300),scan=scan)
        val solved=PackingEngine.solve(PackingRequest(fixtureSpace,items),SolveBudget.unlimited()) as SolveResult.Solved
        val fixture=com.packabunch.data.Project("cloud-geometry-fixture","Geometry fixture",fixtureSpace,items,
            solved.plan,1L,solved.plan.placements.take(1).map { it.instanceId }.toSet())
        val document=com.packabunch.data.cloud.CloudPackCodec.encode(fixture,"00000000-0000-0000-0000-000000000001")
        document.getJSONObject("pack").put("updated_at","1970-01-01T00:00:00.001Z")
        val restored=com.packabunch.data.cloud.CloudPackCodec.decode(document)
        check(restored.request.revision()==fixture.request.revision())
        check(restored.currentPlan==fixture.currentPlan)
        check(restored.packedInstanceIds==fixture.packedInstanceIds)
        check(restored.items.all { it.visualShape!=null })
        java.io.File(filesDir,"cloud-geometry-fixture.json").writeText(document.toString())
        setContent { PackABunchTheme {
            androidx.compose.material3.Surface { Column(Modifier.fillMaxSize().padding(20.dp).statusBarsPadding()) {
                Text("Geometry test fixture", color=TextPrimary)
                Text("Cloud format round trip passed · not a camera scan", color=TextSecondary)
                IsometricCrate(restored.space,restored.currentPlan!!.placements,
                    Modifier.fillMaxWidth().weight(1f),items=restored.items,itemColorFor={itemColor(if(it.specId=="b")0 else 1)},animateEntrance=false)
                Text("1 Bottle    2 Chair", color=TextPrimary)
                Text("Observed surfaces inside an irregular mapped space",color=TextSecondary)
            } }
        } }
    }
}
