package com.sualtikasifi.cizimhafiza.presentation.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.res.stringResource
import com.sualtikasifi.cizimhafiza.R
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.paint
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sualtikasifi.cizimhafiza.presentation.theme.AppTheme

/** Stadium/pill shape used for every primary/secondary button and chip. */
val PillShape = RoundedCornerShape(50)

/** "tr"/"en" — matches the word pool's current language (see WordSeeder.currentLanguage),
 *  used for locale-correct first-letter capitalization of displayed words (String.capitalizeForWordLanguage). */
@Composable
fun currentWordLanguage(): String = LocalConfiguration.current.locales.get(0).language

// ---------------------------------------------------------------------------
// Raised ("chunky") surfaces
// ---------------------------------------------------------------------------

/**
 * Draws a surface as a colored face sitting on a hard, darker bottom edge —
 * the app's one elevation idiom, replacing Material's blurred shadows.
 *
 * Layout is stable across the press: the full [raise] is always reserved as
 * bottom padding, and pressing only moves the face down INTO that reserved
 * strip (via [offset], which doesn't re-measure). So a pressed button never
 * nudges its neighbours.
 *
 * @param pressed drives the depress animation; pass a real interaction-source
 *   value for clickable surfaces and `false` for static ones.
 */
@Composable
fun Modifier.raisedSurface(
    face: Color,
    edge: Color,
    corner: Dp,
    raise: Dp = AppTheme.tokens.raise,
    pressed: Boolean = false,
    border: Color? = null
): Modifier {
    // 1.dp rather than 0.dp so a pressed surface still reads as a physical
    // object resting on something, not as a flat sticker.
    val depth by animateDpAsState(
        targetValue = if (pressed) 1.dp else raise,
        animationSpec = tween(70),
        label = "raised-depth"
    )
    val shape = RoundedCornerShape(corner)
    return this
        .padding(bottom = raise)
        .offset(y = raise - depth)
        .drawBehind {
            drawRoundRect(
                color = edge,
                topLeft = Offset(0f, depth.toPx()),
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(corner.toPx())
            )
        }
        .background(face, shape)
        .then(if (border != null) Modifier.border(1.5.dp, border, shape) else Modifier)
}

/**
 * Just the hard bottom edge of [raisedSurface], for containers that manage
 * their own face (e.g. the drawing canvas, which needs its own texture and a
 * state-colored border). Reserve [raise] as bottom padding before this.
 */
fun Modifier.hardEdge(edge: Color, raise: Dp, corner: Dp): Modifier = drawBehind {
    drawRoundRect(
        color = edge,
        topLeft = Offset(0f, raise.toPx()),
        size = size,
        cornerRadius = CornerRadius(corner.toPx())
    )
}

/** Non-interactive raised white card — the default container for content blocks. */
@Composable
fun RaisedCard(
    modifier: Modifier = Modifier,
    corner: Dp = 24.dp,
    face: Color = MaterialTheme.colorScheme.surface,
    edge: Color = AppTheme.tokens.edge,
    raise: Dp = AppTheme.tokens.raise,
    border: Color? = MaterialTheme.colorScheme.outline,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .raisedSurface(
                face = face,
                edge = edge,
                corner = corner,
                raise = raise,
                pressed = onClick != null && pressed,
                border = border
            )
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor, content = { content() })
    }
}

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

@Composable
private fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    face: Color,
    edge: Color,
    content: Color,
    modifier: Modifier,
    enabled: Boolean,
    icon: ImageVector?,
    border: Color?,
    height: Dp
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha = if (enabled) 1f else 0.42f
    Box(
        modifier = modifier
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .raisedSurface(
                face = face.copy(alpha = alpha),
                edge = edge.copy(alpha = alpha),
                corner = height / 2,
                pressed = pressed,
                border = border?.copy(alpha = alpha)
            )
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 22.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content.copy(alpha = alpha), modifier = Modifier.size(21.dp))
                Spacer(modifier = Modifier.width(9.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                color = content.copy(alpha = alpha),
                maxLines = 1
            )
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 58.dp
) = ChunkyButton(
    text = text,
    onClick = onClick,
    face = MaterialTheme.colorScheme.primary,
    edge = AppTheme.tokens.primaryEdge,
    content = MaterialTheme.colorScheme.onPrimary,
    modifier = modifier,
    enabled = enabled,
    icon = icon,
    border = null,
    height = height
)

/** Same silhouette as [PrimaryButton], white-faced — for the lesser of two adjacent actions. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 58.dp
) = ChunkyButton(
    text = text,
    onClick = onClick,
    face = MaterialTheme.colorScheme.surface,
    edge = AppTheme.tokens.edge,
    content = MaterialTheme.colorScheme.primary,
    modifier = modifier,
    enabled = enabled,
    icon = icon,
    border = MaterialTheme.colorScheme.primary,
    height = height
)

/** Teal-faced button — reserved for online/social actions (see the palette note in Color.kt). */
@Composable
fun SocialButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 58.dp
) = ChunkyButton(
    text = text,
    onClick = onClick,
    face = MaterialTheme.colorScheme.secondary,
    edge = AppTheme.tokens.secondaryEdge,
    content = MaterialTheme.colorScheme.onSecondary,
    modifier = modifier,
    enabled = enabled,
    icon = icon,
    border = null,
    height = height
)

/** Square raised icon button — back arrows, canvas tools, header actions. */
@Composable
fun RaisedIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    size: Dp = 46.dp,
    corner: Dp = 15.dp
) = RaisedIconButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    selected = selected,
    size = size,
    corner = corner
) { tint, iconSize ->
    Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
}

/** Same chrome as the [ImageVector] overload, for a hand-drawn icon (e.g. [EraserGlyph]) that isn't in Material's set. */
@Composable
fun RaisedIconButton(
    onClick: () -> Unit,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    size: Dp = 46.dp,
    corner: Dp = 15.dp,
    icon: @Composable (tint: Color, iconSize: Dp) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha = if (enabled) 1f else 0.35f
    val face = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClickLabel = contentDescription,
                onClick = onClick
            )
            .raisedSurface(
                face = face.copy(alpha = alpha),
                edge = AppTheme.tokens.edge.copy(alpha = alpha),
                corner = corner,
                raise = AppTheme.tokens.raiseSmall,
                pressed = pressed,
                border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            .size(size),
        contentAlignment = Alignment.Center
    ) {
        icon(tint.copy(alpha = alpha), size * 0.44f)
    }
}

/**
 * A block eraser, tilted like a real one, with a groove cut through it near
 * the bottom edge — Material's icon set has no eraser glyph (only the
 * unrelated "PhonelinkErase"), so this is hand-drawn. The groove is punched
 * with `BlendMode.Clear` inside a layer rather than drawn in a fixed
 * background color, so it reads correctly on both the white and tinted
 * (selected) button faces.
 */
@Composable
fun EraserGlyph(tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        // Captured once, in px, before `rotate {}` — DrawScope's own `size`
        // property would otherwise shadow the Dp parameter of the same name.
        val canvasPx = this.size.minDimension
        val bodyWidth = canvasPx * 0.52f
        val bodyHeight = canvasPx * 0.92f
        val left = (canvasPx - bodyWidth) / 2f
        val top = (canvasPx - bodyHeight) / 2f
        val corner = bodyWidth * 0.3f

        rotate(38f) {
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(Offset.Zero, this.size), Paint())
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(left, top),
                    size = Size(bodyWidth, bodyHeight),
                    cornerRadius = CornerRadius(corner)
                )
                drawLine(
                    color = Color.Black,
                    start = Offset(left - corner, top + bodyHeight * 0.66f),
                    end = Offset(left + bodyWidth + corner, top + bodyHeight * 0.66f),
                    strokeWidth = bodyWidth * 0.16f,
                    blendMode = BlendMode.Clear
                )
                canvas.restore()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Chips, pills, badges
// ---------------------------------------------------------------------------

/** Small pill badge for header stats and result metrics (e.g. "Doğru: 13"). */
@Composable
fun StatPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: ImageVector? = null
) {
    Box(
        modifier = modifier.raisedSurface(
            face = containerColor,
            edge = AppTheme.tokens.edge,
            corner = 22.dp,
            raise = 2.dp,
            border = MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge, color = contentColor, maxLines = 1)
        }
    }
}

/** Flat tinted badge — for inline metadata that shouldn't compete with raised controls. */
@Composable
fun TintedBadge(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        maxLines = 1,
        modifier = modifier
            .background(container, PillShape)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

/** Rounded selectable chip — word-count choices, category and mode filters. */
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
    // Only chips sized by the caller (fillMaxWidth/weight, e.g. the wordcount
    // grid cells) should center-fill their text; plain wrap-content chips
    // (e.g. the mode row) must keep their hug-the-label sizing.
    fillWidth: Boolean = false,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .raisedSurface(
                face = if (selected) accent else MaterialTheme.colorScheme.surface,
                edge = if (selected) accent.darken() else AppTheme.tokens.edge,
                corner = 26.dp,
                raise = AppTheme.tokens.raiseSmall,
                pressed = pressed,
                border = if (selected) null else MaterialTheme.colorScheme.outline
            )
    ) {
        Text(
            text = label,
            style = style,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
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
    verticalPadding: Dp = 18.dp,
    textStyle: TextStyle = MaterialTheme.typography.headlineLarge
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .raisedSurface(
                face = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                edge = if (selected) AppTheme.tokens.primaryEdge else AppTheme.tokens.edge,
                corner = 22.dp,
                pressed = pressed,
                border = if (selected) null else MaterialTheme.colorScheme.outline
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$count",
            style = textStyle,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(vertical = verticalPadding),
            textAlign = TextAlign.Center
        )
    }
}

/** Circular tinted well behind an icon — gives list rows and headers a focal point. */
@Composable
fun IconWell(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier.size(size).background(container, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

// ---------------------------------------------------------------------------
// Screen chrome
// ---------------------------------------------------------------------------


/**
 * The floating back button (plus any trailing action) every screen now places
 * over its own full-bleed background, in place of a title bar.
 *
 * The position lives here rather than at each call site because it has to be
 * identical on every screen and it has to clear the status bar: the app draws
 * edge-to-edge (see MainActivity.enableEdgeToEdge), so a button placed at a
 * plain 16dp from the top of an un-inset container lands *on* the notification
 * bar. [statusBarsPadding] handles that, and the 24dp/20dp offset after it
 * matches what the online lobby already used.
 *
 * Place it as the last child of a screen's root Box with
 * `Modifier.align(Alignment.TopStart)` so it draws over the content, and give
 * that content [TopActionsClearance] of extra top spacing so nothing renders
 * underneath it.
 */
@Composable
fun ScreenTopActions(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RaisedIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.cd_back),
            onClick = onBack
        )
        if (trailing != null) {
            Spacer(modifier = Modifier.weight(1f))
            trailing()
        }
    }
}

/**
 * How much top spacing a screen's own content needs to clear
 * [ScreenTopActions] — its 20dp offset plus the button's own footprint.
 * Applies on top of the Scaffold inset the content already carries.
 */
val TopActionsClearance = 76.dp

/** Small uppercase-ish section label above a group of content. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/**
 * The page surface: the "Karalak" doodle-collage paper, washed with a warm
 * translucent gradient. Applied to a screen's root container (which fills
 * the window), so every screen shares the same background. The wash keeps
 * the illustration itself faint enough that cards and text placed on top
 * (which were designed against a flat cream fill) stay legible — a full-
 * strength collage under real UI content read as noise rather than texture.
 */
@Composable
fun Modifier.screenBackground(): Modifier {
    val top = MaterialTheme.colorScheme.background
    val bottom = AppTheme.tokens.backgroundDeep
    return this
        .paint(painterResource(R.drawable.bg_karalak), contentScale = ContentScale.Crop)
        // Veil strength comes from the palette: the collage is ink on white
        // paper, so on a dark page it needs a much heavier veil or it glares
        // through as a lit panel (see AppTokens.backgroundVeilTop).
        .background(
            Brush.verticalGradient(
                listOf(
                    top.copy(alpha = AppTheme.tokens.backgroundVeilTop),
                    bottom.copy(alpha = AppTheme.tokens.backgroundVeilBottom)
                )
            )
        )
}

/**
 * The app's own text input, replacing Material's OutlinedTextField everywhere.
 *
 * Two things made the Material field wrong here. Its floating label notches a
 * gap into the top border and sits *in* that gap, which — on an opaque,
 * pill-shaped field over page artwork — read as a stray white shelf with text
 * balanced on it rather than as part of the control, and left a filled field
 * and an empty one looking like two different components. And its hairline
 * border all but vanished once the page behind it became a textured collage.
 *
 * So: the label is a fixed line *above* the field that never moves, and the
 * field is the same raised white card as every other surface in the app —
 * hard bottom edge included — with the focus state shown by the border
 * turning primary rather than by anything moving.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    centered: Boolean = false,
    textStyle: TextStyle? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null,
    corner: Dp = 20.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val resolvedStyle = (textStyle ?: MaterialTheme.typography.bodyLarge).copy(
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start
    )

    Column(modifier = modifier.alpha(if (enabled) 1f else 0.5f)) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = resolvedStyle,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interaction,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .raisedSurface(
                    face = MaterialTheme.colorScheme.surface,
                    edge = AppTheme.tokens.edge,
                    corner = corner,
                    raise = AppTheme.tokens.raiseSmall,
                    border = if (focused) MaterialTheme.colorScheme.primary else AppTheme.tokens.edge
                )
                .padding(horizontal = 18.dp, vertical = 15.dp),
            decorationBox = { field ->
                Box(contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            style = resolvedStyle,
                            color = AppTheme.tokens.textFaint,
                            modifier = if (centered) Modifier.fillMaxWidth() else Modifier
                        )
                    }
                    field()
                }
            }
        )
    }
}

/** Subtle repeating dot texture — the page surface, and "paper" behind the canvas. */
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

// ---------------------------------------------------------------------------
// Countdown
// ---------------------------------------------------------------------------

/** Circular countdown ring (Break screen "hazırlan" timer, Drawing/Guess per-word timer). */
@Composable
fun CircularCountdown(
    secondsLeft: Int,
    totalSeconds: Int,
    modifier: Modifier = Modifier.size(64.dp),
    ringColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    strokeWidth: Dp = 6.dp,
    textStyle: TextStyle = MaterialTheme.typography.headlineSmall
) {
    val progress = if (totalSeconds <= 0) 0f else (secondsLeft.toFloat() / totalSeconds).coerceIn(0f, 1f)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val inset = strokeWidth.toPx() / 2f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth.toPx())
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
        Text(text = "$secondsLeft", style = textStyle, color = ringColor)
    }
}

/** Flat horizontal progress bar used for level/word progress. */
@Composable
fun ProgressTrack(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(track, PillShape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .fillMaxSize()
                .background(color, PillShape)
        )
    }
}

// ---------------------------------------------------------------------------

/** Darkens a color toward its own hue — used to derive an edge from any face. */
internal fun Color.darken(factor: Float = 0.72f): Color =
    Color(red = red * factor, green = green * factor, blue = blue * factor, alpha = alpha)

/** Shared min-height for touch targets, per the 44dp accessibility floor. */
internal fun Modifier.minTouchTarget(): Modifier = this.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)

@Composable
internal fun ProvideBodyStyle(content: @Composable () -> Unit) =
    ProvideTextStyle(MaterialTheme.typography.bodyMedium, content)


/**
 * What a list shows when it is legitimately empty.
 *
 * These used to be a lone muted sentence dropped where the rows would have
 * been, which on a textured page read as a caption someone forgot to
 * delete — and, worse, looked identical to a list that had failed to load.
 * A card with a mark on it says "there is nothing here yet" deliberately.
 */
@Composable
fun EmptyState(
    emoji: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable ColumnScope.() -> Unit)? = null
) {
    RaisedCard(corner = 24.dp, face = AppTheme.tokens.cardWarm, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            ) {
                Text(text = emoji, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (action != null) {
                Spacer(modifier = Modifier.height(16.dp))
                action()
            }
        }
    }
}

/**
 * Card-shaped placeholders shown while a list is still loading.
 *
 * Without these a slow network left the page blank, which is exactly what a
 * failed load and an empty list also look like — three different states,
 * one appearance. Deliberately static rather than shimmering: this app's
 * surfaces are hard-edged and matte, and a sweeping gradient would be the
 * only animated gloss in it.
 */
@Composable
fun LoadingRows(count: Int = 3, height: Dp = 64.dp, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(count) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        RoundedCornerShape(20.dp)
                    )
            )
        }
    }
}

/**
 * The in-game top bar, shared by the drawing and guessing screens.
 *
 * Both screens used to lay their own chrome out as a flat row of whatever
 * each happened to need — back button, a logo disc, a match clock, a word
 * counter, a countdown ring — in different orders and with different gaps,
 * so the two halves of one round looked like two different games. This puts
 * the same three zones in the same places every time:
 *
 *  - left: the way out;
 *  - centre: where you are in the match (clock and word count read as one
 *    fact, so they share one plate rather than floating separately);
 *  - right: controls and the turn's countdown.
 *
 * The app's logo is deliberately not here. A player looking at this screen
 * is already inside the app, and in-game chrome is worth more as breathing
 * room than as branding.
 */
@Composable
fun GameTopBar(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    matchClock: String? = null,
    progressLabel: String? = null,
    musicEnabled: Boolean? = null,
    onToggleMusic: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (onBack != null) {
            RaisedIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                onClick = onBack,
                size = 42.dp
            )
        }

        if (matchClock != null || progressLabel != null) {
            RaisedCard(corner = 16.dp, raise = 3.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (matchClock != null) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = matchClock,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (matchClock != null && progressLabel != null) {
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (progressLabel != null) {
                        Text(
                            text = progressLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Reachable mid-round on purpose: the soundtrack is the one setting a
        // player is most likely to want changed *while* playing, and making
        // them leave a timed round to reach Settings is not an option.
        if (musicEnabled != null && onToggleMusic != null) {
            RaisedIconButton(
                icon = if (musicEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = stringResource(
                    if (musicEnabled) R.string.cd_music_on else R.string.cd_music_off
                ),
                onClick = onToggleMusic,
                size = 42.dp
            )
        }

        if (trailing != null) trailing()
    }
}
