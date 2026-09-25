package com.ybugmobile.waktiva.ui.theme.clouds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Recipes: what the sky looks like for each weather condition
// ---------------------------------------------------------------------------

internal enum class CloudTone { FAIR, OVERCAST, SNOW, RAIN, STORM, FOG }

/**
 * One depth layer: how many clouds of which kind, where their bases sit, their width and
 * aspect ratio. [base] is a fraction of the sky band's height (see [Sky]); for [lowLying]
 * layers (fog) it is a fraction of the whole screen height instead. [width] is a fraction
 * of [Sky.unit]. [count] is for a portrait screen and grows with wider screens.
 */
internal class LayerRecipe(
    val count: Int,
    val kind: CloudKind,
    val base: ClosedFloatingPointRange<Float>,
    val width: ClosedFloatingPointRange<Float>,
    val aspect: ClosedFloatingPointRange<Float>,
    val lowLying: Boolean = false
)

internal class CloudRecipe(val tone: CloudTone, val layers: List<LayerRecipe>)

private val Fair = listOf(
    LayerRecipe(4, CloudKind.STRATUS, 0.2f..0.55f, 0.3f..0.42f, 5f..6.5f),
    LayerRecipe(5, CloudKind.STRATUS, 0.45f..0.8f, 0.42f..0.58f, 4.5f..6f),
    LayerRecipe(3, CloudKind.STRATUS, 0.7f..0.95f, 0.55f..0.7f, 4f..5.5f)
)
private val FewFair = listOf(
    LayerRecipe(3, CloudKind.STRATUS, 0.25f..0.6f, 0.28f..0.4f, 5f..6.5f),
    LayerRecipe(2, CloudKind.STRATUS, 0.55f..0.9f, 0.4f..0.55f, 4.5f..6f)
)
private val Overcast = listOf(
    LayerRecipe(6, CloudKind.STRATUS, 0.2f..0.5f, 0.6f..0.85f, 6.5f..9f),
    LayerRecipe(6, CloudKind.STRATUS, 0.45f..0.8f, 0.5f..0.75f, 5f..7f),
    LayerRecipe(5, CloudKind.STRATUS, 0.7f..0.95f, 0.4f..0.55f, 4.5f..6f)
)
private val Rain = listOf(
    LayerRecipe(6, CloudKind.NIMBUS, 0.3f..0.55f, 0.75f..1.0f, 5f..7f),
    LayerRecipe(6, CloudKind.NIMBUS, 0.55f..0.85f, 0.55f..0.8f, 4.5f..6f),
    LayerRecipe(5, CloudKind.STRATUS, 0.75f..0.95f, 0.4f..0.55f, 4.5f..6f)
)
private val Storm = listOf(
    LayerRecipe(6, CloudKind.NIMBUS, 0.3f..0.55f, 0.85f..1.1f, 6f..8f),
    LayerRecipe(6, CloudKind.NIMBUS, 0.55f..0.85f, 0.6f..0.85f, 4.5f..6f),
    LayerRecipe(5, CloudKind.NIMBUS, 0.75f..0.95f, 0.4f..0.55f, 4f..5.5f)
)
private val Fog = listOf(
    LayerRecipe(3, CloudKind.FOG, 0.5f..0.66f, 1.0f..1.3f, 9f..12f, lowLying = true),
    LayerRecipe(3, CloudKind.FOG, 0.62f..0.82f, 1.1f..1.4f, 9f..12f, lowLying = true),
    LayerRecipe(3, CloudKind.FOG, 0.76f..0.97f, 1.2f..1.5f, 8f..11f, lowLying = true)
)

internal fun cloudRecipe(condition: WeatherCondition): CloudRecipe? = when (condition) {
    WeatherCondition.CLEAR, WeatherCondition.UNKNOWN -> null
    WeatherCondition.MAINLY_CLEAR -> CloudRecipe(CloudTone.FAIR, FewFair)
    WeatherCondition.PARTLY_CLOUDY -> CloudRecipe(CloudTone.FAIR, Fair)
    WeatherCondition.OVERCAST -> CloudRecipe(CloudTone.OVERCAST, Overcast)
    WeatherCondition.FOGGY -> CloudRecipe(CloudTone.FOG, Fog)
    WeatherCondition.DRIZZLE, WeatherCondition.FREEZING_DRIZZLE -> CloudRecipe(CloudTone.OVERCAST, Rain)
    WeatherCondition.RAINY, WeatherCondition.HEAVY_RAIN,
    WeatherCondition.RAIN_SHOWERS, WeatherCondition.FREEZING_RAIN -> CloudRecipe(CloudTone.RAIN, Rain)
    WeatherCondition.SNOWY, WeatherCondition.HEAVY_SNOW,
    WeatherCondition.SNOW_GRAINS, WeatherCondition.SNOW_SHOWERS -> CloudRecipe(CloudTone.SNOW, Overcast)
    WeatherCondition.THUNDERSTORM, WeatherCondition.THUNDERSTORM_HAIL -> CloudRecipe(CloudTone.STORM, Storm)
}

private fun palette(r: Long, b: Long, s: Long, h: Long) = CloudPalette(r.toInt(), b.toInt(), s.toInt(), h.toInt())

internal fun cloudPalette(tone: CloudTone, isDay: Boolean): CloudPalette = when (tone) {
    CloudTone.FAIR -> if (isDay) palette(0xFFFFFFFF, 0xFFEEF2F7, 0xFFAEBBCC, 0xFFFFFFFF) else palette(0xFF5F6B8C, 0xFF2B3452, 0xFF151B2D, 0xFF8793B3)
    CloudTone.OVERCAST -> if (isDay) palette(0xFFE1E6ED, 0xFFC0C8D3, 0xFF8A95A6, 0xFFF3F5F8) else palette(0xFF4B5572, 0xFF262D44, 0xFF131929, 0xFF6B7695)
    CloudTone.SNOW -> if (isDay) palette(0xFFEEF1F5, 0xFFD5DBE3, 0xFFA3ADBB, 0xFFFFFFFF) else palette(0xFF55607C, 0xFF2D354D, 0xFF181E2F, 0xFF76819F)
    CloudTone.RAIN -> if (isDay) palette(0xFFA7B0BD, 0xFF838C9A, 0xFF525C69, 0xFFC3CAD4) else palette(0xFF3E4762, 0xFF1F263A, 0xFF0F131E, 0xFF58627F)
    CloudTone.STORM -> if (isDay) palette(0xFF78818F, 0xFF4F5866, 0xFF272D36, 0xFF99A2B0) else palette(0xFF343C55, 0xFF1A2034, 0xFF0B0F18, 0xFF4B5573)
    CloudTone.FOG -> if (isDay) palette(0xFFE3E7ED, 0xFFD3D9E2, 0xFFC2C9D4, 0xFFF0F2F5) else palette(0xFF3A4257, 0xFF2C3346, 0xFF222839, 0xFF474F66)
}

// ---------------------------------------------------------------------------
// Layout: where each cloud starts and how fast it drifts
// ---------------------------------------------------------------------------

/**
 * The part of the screen clouds live in: the top 37.5% of a portrait screen, a bit more of a
 * landscape one where the day circle sits lower. Clouds fade out towards the band's bottom
 * (see [SkyFade]) so the sky blends softly into the day circle.
 * Sizes and speeds scale with the screen's short side, so clouds keep their portrait size in
 * landscape and more of them fill the extra width, instead of each one growing huge.
 */
internal class Sky(val width: Float, val height: Float) {
    val unit = min(width, height)
    val bottom = height * if (width < height) PortraitSky else LandscapeSky
    /** How many portrait-screen widths fit across. */
    val spread = width / unit
}

internal const val PortraitSky = 0.375f
internal const val LandscapeSky = 0.525f

/**
 * Cloud opacity down the sky band (0 = top of the screen, 1 = the band's bottom): solid in
 * the upper part, then thinning out to nothing so the lower clouds melt into the day circle.
 */
internal val SkyFade = arrayOf(0f to 1f, 0.4f to 1f, 0.7f to 0.4f, 1f to 0f)

/** Bottom of the band the clouds fade out towards, or null when the recipe is low-lying fog. */
internal fun skyFadeBottom(recipe: CloudRecipe, viewportWidth: Float, viewportHeight: Float): Float? =
    if (recipe.layers.all { it.lowLying }) null else Sky(viewportWidth, viewportHeight).bottom

/** Drift speed per layer (far, mid, near), in [Sky.unit]s per second. */
private val LayerSpeed = floatArrayOf(2f / 230f, 4f / 230f, 7f / 230f)
private val LayerAlpha = floatArrayOf(0.55f, 0.8f, 0.95f)
private val FogAlpha = floatArrayOf(0.28f, 0.32f, 0.38f)

internal class CloudPlacement(
    val kind: CloudKind,
    val layer: Int,
    val seed: Int,
    val width: Float,
    val height: Float,
    /** Start position of the cloud's left edge. */
    val startX: Float,
    val top: Float,
    /** Pixels per second. */
    val speed: Float,
    /** Opacity, before any fade. */
    val alpha: Float
)

private fun ClosedFloatingPointRange<Float>.at(t: Float) = start + (endInclusive - start) * t

/**
 * Lays each layer's clouds out across the sky: stratified heights, spread start positions
 * and an individual speed, so they drift apart instead of moving as a block. Deterministic.
 */
internal fun layoutClouds(recipe: CloudRecipe, viewportWidth: Float, viewportHeight: Float, seed: Int = 7919): List<CloudPlacement> {
    val sky = Sky(viewportWidth, viewportHeight)
    val rnd = Random(seed)
    val placements = mutableListOf<CloudPlacement>()
    recipe.layers.forEachIndexed { layer, spec ->
        val count = max(1, (spec.count * sky.spread).roundToInt())
        val bands = List(count) { k -> (k + 0.2f + 0.6f * rnd.nextFloat()) / count }.shuffled(rnd)
        val floor = if (spec.lowLying) viewportHeight else sky.bottom
        repeat(count) { k ->
            val width = spec.width.at(rnd.nextFloat()) * sky.unit
            val height = width / spec.aspect.at(rnd.nextFloat())
            val alpha = if (spec.kind == CloudKind.FOG) FogAlpha[layer] else LayerAlpha[layer]
            placements += CloudPlacement(
                kind = spec.kind,
                layer = layer,
                seed = 1000 + layer * 131 + k * 97,
                width = width,
                height = height,
                startX = (k + 0.15f + 0.7f * rnd.nextFloat()) / count * (viewportWidth + width) - width,
                top = spec.base.at(bands[k]) * floor - height,
                speed = LayerSpeed[layer] * sky.unit * (0.7f + 0.6f * rnd.nextFloat()),
                alpha = alpha * (0.7f + 0.3f * rnd.nextFloat())
            )
        }
    }
    return placements
}

/**
 * Left edge of a cloud [seconds] after start, wrapping from the right edge back to the left.
 * The wrap happens only once the sprite's soft margin (one cloud height each side) is off screen,
 * so no faint edge pops in or out.
 */
internal fun CloudPlacement.leftAt(seconds: Float, viewportWidth: Float): Float {
    val margin = height
    val span = viewportWidth + width + 2 * margin
    val travelled = (startX + width + margin + seconds * speed) % span
    return (if (travelled < 0) travelled + span else travelled) - width - margin
}

// ---------------------------------------------------------------------------
// Scene: placements with their rendered sprites
// ---------------------------------------------------------------------------

internal class SceneCloud(val placement: CloudPlacement, val sprite: CloudSprite, val litSprite: CloudSprite?)

/** [fadeBottom]: where the clouds have faded out completely, or null for fog, which never fades. */
internal class CloudScene(val clouds: List<SceneCloud>, val viewportWidth: Float, val fadeBottom: Float?)

/** Sprites are rendered at half resolution; the clouds are soft, so nothing is lost. */
private const val SpriteResolution = 0.5f

/** Lightning-lit copies only show for a moment, so they can be coarser. */
private const val LitSpriteResolution = 0.35f

internal fun buildCloudScene(condition: WeatherCondition, isDay: Boolean, width: Float, height: Float): CloudScene? {
    val recipe = cloudRecipe(condition) ?: return null
    val palette = cloudPalette(recipe.tone, isDay)
    val storm = recipe.tone == CloudTone.STORM
    val clouds = layoutClouds(recipe, width, height).map { p ->
        SceneCloud(
            placement = p,
            sprite = buildCloudSprite(p.kind, p.seed, p.width, p.height, palette, lit = false, SpriteResolution),
            litSprite = if (storm) {
                buildCloudSprite(p.kind, p.seed, p.width, p.height, palette, lit = true, LitSpriteResolution)
            } else null
        )
    }
    return CloudScene(clouds, width, skyFadeBottom(recipe, width, height))
}

/** The scene for this weather and viewport, rendered off the main thread; null until ready. */
@Composable
internal fun rememberCloudScene(condition: WeatherCondition, isDay: Boolean, width: Float, height: Float): CloudScene? {
    val scene = produceState<CloudScene?>(null, condition, isDay, width, height) {
        value = null
        value = withContext(Dispatchers.Default) { buildCloudScene(condition, isDay, width, height) }
    }
    return scene.value
}

/** Seconds since this composable entered the screen, updated every frame. */
@Composable
internal fun rememberSceneClock(): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> seconds.floatValue = (now - start) / 1_000_000_000f }
        }
    }
    return seconds
}

/**
 * Draws the drifting clouds, fading out down the sky band. [flash] (0..1) lights storm clouds
 * from within; [fade] (0..1) fades the whole sky in when a new scene is ready.
 */
internal fun DrawScope.drawClouds(scene: CloudScene, seconds: Float, flash: Float, fade: Float) {
    val bottom = scene.fadeBottom
    if (bottom == null) {
        drawSprites(scene, seconds, flash, fade)
        return
    }
    // Draw the clouds into a layer, then fade that layer out towards the band's bottom.
    val band = Size(size.width, bottom)
    drawContext.canvas.saveLayer(Rect(Offset.Zero, band), Paint())
    drawSprites(scene, seconds, flash, fade)
    drawRect(
        brush = Brush.verticalGradient(
            *SkyFade.map { (at, alpha) -> at to Color.Black.copy(alpha = alpha) }.toTypedArray(),
            startY = 0f,
            endY = bottom
        ),
        size = band,
        blendMode = BlendMode.DstIn
    )
    drawContext.canvas.restore()
}

private fun DrawScope.drawSprites(scene: CloudScene, seconds: Float, flash: Float, fade: Float) {
    for (cloud in scene.clouds) {
        val p = cloud.placement
        val left = p.leftAt(seconds, scene.viewportWidth)
        val sprite = cloud.sprite
        val offset = IntOffset((left - sprite.pad).roundToInt(), (p.top - sprite.pad).roundToInt())
        val size = IntSize(sprite.width.roundToInt(), sprite.height.roundToInt())
        drawImage(sprite.image, dstOffset = offset, dstSize = size, alpha = p.alpha * fade)
        if (flash > 0f && cloud.litSprite != null) {
            drawImage(cloud.litSprite.image, dstOffset = offset, dstSize = size, alpha = flash * fade)
        }
    }
}
