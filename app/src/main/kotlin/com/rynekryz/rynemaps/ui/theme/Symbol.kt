package com.rynekryz.rynemaps.ui.theme

import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rynekryz.rynemaps.R

private fun symbolFamily(weight: Int, opsz: Float): FontFamily = FontFamily(
    Font(
        R.font.material_symbols_rounded,
        FontWeight(weight),
        FontStyle.Normal,
        variationSettings = FontVariation.Settings(
            FontWeight(weight),
            FontStyle.Normal,
            FontVariation.Setting("FILL", 1f),
            FontVariation.Setting("GRAD", 0f),
            FontVariation.Setting("opsz", opsz.coerceIn(20f, 48f)),
        ),
    )
)

@Composable
fun Symbol(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    weight: Int = 400,
) {
    val family = remember(weight, size) { symbolFamily(weight, size.value) }
    val sp = with(LocalDensity.current) { size.toSp() }
    Text(
        text = name,
        modifier = modifier.size(size),
        style = TextStyle(
            fontFamily = family,
            fontSize = sp,
            lineHeight = sp,
            color = tint,
            textAlign = TextAlign.Center,
            fontFeatureSettings = "liga",
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}
