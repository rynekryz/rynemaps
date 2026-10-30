package com.rynekryz.rynemaps.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rynekryz.rynemaps.R

private fun symbolFamily(weight: Int): FontFamily = FontFamily(
    Font(
        R.font.material_symbols_rounded,
        FontWeight(weight),
        FontStyle.Normal,
        variationSettings = FontVariation.Settings(
            FontWeight(weight),
            FontStyle.Normal,
            FontVariation.Setting("FILL", 1f),
            FontVariation.Setting("GRAD", 0f),
            FontVariation.Setting("opsz", 40f),
        ),
    )
)

private const val EXTRA = 0.03f

@Composable
fun Symbol(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    weight: Int = 400,
) {
    val family = remember(weight) { symbolFamily(weight) }
    val fontSize = with(LocalDensity.current) { size.toSp() }
    val emPx = with(LocalDensity.current) { fontSize.toPx() }
    val measurer = rememberTextMeasurer()
    val layout = remember(name, family, fontSize, emPx, tint) {
        measurer.measure(
            AnnotatedString(name),
            TextStyle(
                fontFamily = family,
                fontSize = fontSize,
                fontFeatureSettings = "liga",
                color = tint,
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
    Canvas(modifier.size(size)) {
        val box = size.toPx()
        val dy = box / 2f + emPx * (0.5f - EXTRA) - layout.firstBaseline
        val dx = (box - layout.size.width) / 2f
        drawText(layout, topLeft = Offset(dx, dy))
    }
}
