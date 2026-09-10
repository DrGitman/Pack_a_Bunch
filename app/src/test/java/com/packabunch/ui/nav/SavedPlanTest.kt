package com.packabunch.data.db

import com.packabunch.data.Project
import com.packabunch.packing.*
import org.junit.Assert.*
import org.junit.Test

class SavedPlanTest {
    private fun pack(): Project {
        val p=Project("p","Pack",Space("s","Space",Dimensions(300,300,300)),listOf(ItemSpec("i","Item",Dimensions(100,100,100),quantity=2)))
        val result=PackingEngine.solve(p.request,SolveBudget(timeBudgetMillis=2000)) as SolveResult.Solved
        return p.copy(plan=result.plan,packedInstanceIds=setOf(result.plan.placements.first().instanceId))
    }
    @Test fun `exact positions and packing ticks survive save and load`() {
        val original=pack()
        val restored=StoredProject(original.toEntity(),original.toItemEntities(),original.toSummaryEntity()).toProject()
        assertEquals(original.plan,restored.plan)
        assertEquals(original.packedInstanceIds,restored.packedInstanceIds)
        assertTrue(PlanValidator.validate(restored.request,restored.plan!!).isValid)
    }
    @Test fun `corrupted placement is discarded before display`() {
        val p=pack()
        val broken=p.plan!!.copy(placements=p.plan.placements.map { it.copy(xMm=9000) })
        val summary=p.toSummaryEntity()!!.copy(planDetails=GeometryCodec.plan(broken))
        assertNull(StoredProject(p.toEntity(),p.toItemEntities(),summary).toProject().plan)
    }
    @Test fun `scan cells and collision masks participate in revision`() {
        val p=pack()
        fun scan(cell:Byte)=ScannedSpace(VoxelGrid(0,0,0,100,3,3,3,ByteArray(27) { if(it==0)cell else 0 }))
        assertNotEquals(p.copy(space=p.space.copy(scan=scan(0))).request.revision(),p.copy(space=p.space.copy(scan=scan(1))).request.revision())
        fun shaped(cell:Boolean)=p.items.first().copy(shape=ItemShape.VoxelMask(50,2,2,2,BooleanArray(8) { it!=0 || cell }))
        assertNotEquals(p.copy(items=listOf(shaped(true))).request.revision(),p.copy(items=listOf(shaped(false))).request.revision())
    }
}
