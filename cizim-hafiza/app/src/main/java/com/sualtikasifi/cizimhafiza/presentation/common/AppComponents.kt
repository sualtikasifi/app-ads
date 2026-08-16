package com.sualtikasifi.cizimhafiza.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalTextStyle

// Stadium/pill shape used for every primary/secondary button and chip in the mockups.
val PillShape = RoundedCornerShape(50)

/** "tr"/"en" — matches the word pool's current language (see WordSeeder.currentLanguage),
 *  used for locale-correct first-letter capitalization of displayed words (String.capitalizeForWordLanguage). */
@Composable
fun currentWordLanguage(): String = LocalConfiguration.current.locales.get(0).language

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 1.dp,
            disabledElevation = 0.dp
        ),
        modifier = modifier.height(56.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        modifier = modifier.height(56.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
        }
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}

/** Small pill badge used for header stats and result-screen metrics (e.g. "Doğru: 13"). */
@Composable
fun StatPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        shape = PillShape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 2.dp,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/** Rounded selectable card — used for word-count choices and category filters. */
@Composable
fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 18.dp,
    verticalPadding: Dp = 12.dp,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    // Only chips that are sized by the caller (fillMaxWidth/weight, e.g. the
    // wordcount grid cells) should center-fill their text; plain wrap-content
    // chips (e.g. the mode row) must keep their original hug-the-label sizing.
    fillWidth: Boolean = false
) {
    Surface(
        onClick = onClick,
        shape = PillShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = if (selected) 3.dp else 1.5.dp,
        modifier = modifier
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            style = style,
            maxLines = maxLines,
            textAlign = if (fillWidth) TextAlign.Center else null,
            modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
        )
    }
}

@Composable
fun SelectableCountCard(
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 20.dp,
    textStyle: TextStyle = MaterialTheme.typography.headlineLarge
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = verticalPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$count",
                style = textStyle,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Circular countdown ring (Break screen "hazırlan" timer, Drawing screen per-word timer). */
@Composable
fun CircularCountdown(
    secondsLeft: Int,
    totalSeconds: Int,
    modifier: Modifier = Modifier.size(96.dp),
    ringColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    strokeWidth: Dp = 8.dp
) {
    val progress = if (totalSeconds == 0) 0f else secondsLeft.toFloat() / totalSeconds.toFloat()
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = strokeWidth.toPx())
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = stroke
            )
        }
        Text(text = "$secondsLeft", style = MaterialTheme.typography.headlineLarge, color = ringColor)
    }
}

/** Subtle repeating dot texture used behind menu/break screens and as "paper" behind the canvas. */
fun Modifier.dotGridBackground(
    dotColor: Color,
    spacing: Dp = 28.dp,
    radius: Dp = 1.5.dp
): Modifier = this.drawBehind {
    val spacingPx = spacing.toPx()
    val radiusPx = radius.toPx()
    var y = spacingPx / 2
    while (y < size.height) {
        var x = spacingPx / 2
        while (x < size.width) {
            drawCircle(color = dotColor, radius = radiusPx, center = Offset(x, y))
            x += spacingPx
        }
        y += spacingPx
    }
}
