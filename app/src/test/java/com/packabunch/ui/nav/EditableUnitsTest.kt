package com.packabunch.ui.nav

import com.packabunch.ui.format.*
import org.junit.Assert.assertEquals
import org.junit.Test

class EditableUnitsTest {
    @Test fun `every stored millimetre through 50 metres survives both editable unit formats`() {
        for (mm in 1..50_000) for (unit in LengthUnit.entries) {
            assertEquals("$mm mm in $unit", mm, parseLengthToMm(formatEditableLength(mm, unit), unit))
        }
    }

    @Test fun `repeated toggles do not accumulate rounding error`() {
        var mm = 400
        repeat(100) {
            mm = requireNotNull(parseLengthToMm(formatEditableLength(mm, LengthUnit.INCHES), LengthUnit.INCHES))
            mm = requireNotNull(parseLengthToMm(formatEditableLength(mm, LengthUnit.CENTIMETRES), LengthUnit.CENTIMETRES))
        }
        assertEquals(400, mm)
    }
}
