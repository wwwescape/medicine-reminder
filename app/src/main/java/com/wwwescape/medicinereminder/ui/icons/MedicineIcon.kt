package com.wwwescape.medicinereminder.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wwwescape.medicinereminder.data.db.Medicine
import com.wwwescape.medicinereminder.data.db.MedicineShape

/** A medicine drawn in its shape and colors, [width] wide (height follows the art's 100:64). */
@Composable
fun MedicineIcon(
    shape: MedicineShape,
    color1: Int,
    color2: Int,
    modifier: Modifier = Modifier,
    width: Dp = 48.dp,
) {
    Canvas(modifier = modifier.width(width).aspectRatio(MedicineArt.ASPECT)) {
        drawIntoCanvas { MedicineArt.draw(it.nativeCanvas, shape, color1, color2, size.width, size.height) }
    }
}

@Composable
fun MedicineIcon(medicine: Medicine, modifier: Modifier = Modifier, width: Dp = 48.dp) =
    MedicineIcon(medicine.shape, medicine.color1, medicine.color2, modifier, width)

/** The swatches offered for a medicine's colors, roughly the colors real pills come in. */
val MedicineColors: List<Int> = listOf(
    0xFFF5F5F2, 0xFFEFE3C2, 0xFFFFF29A, 0xFFF5E43A, 0xFFFFC98B, 0xFFF7941D,
    0xFFF2600C, 0xFFD93A2B, 0xFF9E2A2B, 0xFFFBD3DF, 0xFFF29BB5, 0xFFD9468C,
    0xFFC9B3EA, 0xFF8E5BC6, 0xFF8EC9EE, 0xFF1E7FC9, 0xFF1D3F8F, 0xFF9FE3D0,
    0xFF19A99A, 0xFF9AD06A, 0xFF2E9E3E, 0xFFD7B98E, 0xFF8A5A2B, 0xFFA7A9AC,
    0xFF5E6166, 0xFF2B2B2B,
).map { it.toInt() }
