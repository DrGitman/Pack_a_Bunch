package com.packabunch.packing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ItemFormTest {
    @Test fun `names propose a form by whole word only`() {
        assertEquals(FormFamily.TAPERED, ItemForm.guess("Coffee cup")?.family)
        assertEquals(FormFamily.CYLINDER, ItemForm.guess("Water bottle")?.family)
        assertEquals(FormFamily.SPHERE, ItemForm.guess("Football")?.family)
        assertEquals(FormFamily.BOX, ItemForm.guess("Rusks box")?.family)
        assertNull(ItemForm.guess("Cupboard"))
        assertNull(ItemForm.guess("Boxing gloves"))
        assertNull(ItemForm.guess(""))
    }

    @Test fun `forms survive storage and junk does not`() {
        val f = ItemForm(FormFamily.TAPERED, 1.4f)
        assertEquals(f, ItemForm.decode(f.encode()))
        assertNull(ItemForm.decode("TEAPOT:1"))
        assertNull(ItemForm.decode(null))
        assertEquals(ItemForm(FormFamily.SPHERE, 1f), ItemForm.decode("SPHERE:99"))
    }

    @Test fun `a form never changes the plan revision`() {
        val space = space(600, 400, 300)
        val a = item("a", 100, 100, 100)
        val r1 = PackingRequest(space, listOf(a)).revision()
        val r2 = PackingRequest(space, listOf(a.copy(form = ItemForm(FormFamily.SPHERE)))).revision()
        assertEquals(r1, r2)
    }

    @Test fun `a scanned cup keeps its taper`() {
        val fit = FittedObject(0f, 0f, 0f, 84f, 84f, 95f, ShapeFamily.TAPERED, 42f, 30f, observedAxes = Axis.entries.toSet(), pointCount = 1)
        val form = ItemForm.fromFit(fit)
        assertEquals(FormFamily.TAPERED, form.family)
        assertEquals(1.4f, form.topRatio, 0.01f)
    }
}
