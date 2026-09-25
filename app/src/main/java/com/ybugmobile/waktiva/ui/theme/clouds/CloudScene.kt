package com.ybugmobile.waktiva.ui.theme.clouds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Recipes: what the sky looks like for each weather condition
// ---------------------------------------------------------------------------

internal enum class CloudTone { FAIR, OVERCAST, SNOW, RAIN, STORM, FOG }

/** One depth layer: how many clouds of which kind, their vertical band, width and aspect ratio. */
internal class LayerRecipe(
    val count: Int,
    val kind: CloudKind,
    val top: ClosedFloatingPointRange<Float>,
    val width: ClosedFloatingPointRange<Float>,
    val aspect: ClosedFloatingPointRange<Float>
)

internal class CloudRecipe(val tone: CloudTone, val layers: List<LayerRecipe>)

private val Fair = listOf(
    LayerRecipe(3, CloudKind.CUMULUS, 0.03f..0.2f, 0.18f..0.26f, 2.6f..3.2f),
    LayerRecipe(3, CloudKind.CUMULUS, 0.12f..0.38f, 0.26f..0.36f, 2.3f..2.9f),
    LayerRecipe(2, CloudKind.CUMULUS, 0.28f..0.52f, 0.36f..0.46f, 2.1f..2.6f)
)
private val FewFair = listOf(
    LayerRecipe(2, CloudKind.CUMULUS, 0.03f..0.2f, 0.16f..0.22f, 2.6f..3.2f),
    LayerRecipe(1, CloudKind.CUMULUS, 0.16f..0.36f, 0.24f..0.32f, 2.3f..2.9f)
)
private val Overcast = listOf(
    LayerRecipe(4, CloudKind.STRATUS, -0.02f..0.2f, 0.6f..0.85f, 6.5f..9f),
    LayerRecipe(4, CloudKind.STRATUS, 0.1f..0.36f, 0.5f..0.75f, 5f..7f),
    LayerRecipe(3, CloudKind.CUMULUS, 0.28f..0.55f, 0.3f..0.42f, 2.2f..2.8f)
)
private val Rain = listOf(
    LayerRecipe(4, CloudKind.NIMBUS, -0.04f..0.12f, 0.75f..1.0f, 5f..7f),
    LayerRecipe(4, CloudKind.NIMBUS, 0.06f..0.3f, 0.55f..0.8f, 4.5f..6f),
    LayerRecipe(3, CloudKind.CUMULUS, 0.24f..0.5f, 0.28f..0.38f, 2.3f..2.9f)
)
private val Storm = listOf(
    LayerRecipe(4, CloudKind.NIMBUS, -0.05f..0.1f, 0.85f..1.1f, 6f..8f),
    LayerRecipe(1, CloudKind.TOWER, 0.02f..0.04f, 0.9f..0.95f, 2.7f..2.9f),
    LayerRecipe(3, CloudKind.CUMULUS, 0.3f..0.52f, 0.28f..0.4f, 2.2f..2.8f)
)
private val Fog = listOf(
    LayerRecipe(3, CloudKind.FOG, 0.44f..0.6f, 1.0f..1.3f, 9f..12f),
    LayerRecipe(3, CloudKind.FOG, 0.56f..0.76f, 1.1f..1.4f, 9f..12f),
    LayerRecipe(3, CloudKind.FOG, 0.7f..0.92f, 1.2f..1.5f, 8f..11f)
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

/** Drift speed per layer (far, mid, near), in viewport widths per second. */
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
 * Lays each layer's clouds out across the viewport: stratified heights, spread start positions
 * and an individual speed, so they drift apart instead of moving as a block. Deterministic.
 */
internal fun layoutClouds(recipe: CloudRecipe, viewportWidth: Float, viewportHeight: Float, seed: Int = 7919): List<CloudPlacement> {
    val rnd = Random(seed)
    val placements = mutableListOf<CloudPlacement>()
    recipe.layers.forEachIndexed { layer, spec ->
        val bands = List(spec.count) { k -> (k + 0.2f + 0.6f * rnd.nextFloat()) / spec.count }.shuffled(rnd)
        repeat(spec.count) { k ->
            val width = spec.width.at(rnd.nextFloat()) * viewportWidth
            val height = width / spec.aspect.at(rnd.nextFloat())
            val base = if (spec.kind == CloudKind.FOG) FogAlpha[layer] else LayerAlpha[layer]
            placements += CloudPlacement(
                kind = spec.kind,
                layer = layer,
                seed = 1000 + layer * 131 + k * 97,
                width = width,
                height = height,
                startX = (k + 0.15f + 0.7f * rnd.nextFloat()) / spec.count * (viewportWidth + width) - width,
                top = spec.top.at(bands[k]) * viewportHeight,
                speed = LayerSpeed[layer] * viewportWidth * (0.7f + 0.6f * rnd.nextFloat()),
                alpha = base * (0.7f + 0.3f * rnd.nextFloat())
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

internal class CloudScene(val clouds: List<SceneCloud>, val viewportWidth: Float)

/** Sprites are rendered at half resolution; the clouds are soft, so nothing is lost. */
private const val SpriteResolution = 0.5f

internal fun buildCloudScene(condition: WeatherCondition, isDay: Boolean, width: Float, height: Float): CloudScene? {
    val recipe = cloudRecipe(condition) ?: return null
    val palette = cloudPalette(recipe.tone, isDay)
    val storm = recipe.tone == CloudTone.STORM
    val clouds = layoutClouds(recipe, width, height).map { p ->
        SceneCloud(
            placement = p,
            sprite = buildCloudSprite(p.kind, p.seed, p.width, p.height, palette, lit = false, SpriteResolution),
            litSprite = if (storm && p.kind != CloudKind.CUMULUS) {
                buildCloudSprite(p.kind, p.seed, p.width, p.height, palette, lit = true, SpriteResolution)
            } else null
        )
    }
    return CloudScene(clouds, width)
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
 * Draws the drifting clouds. [flash] (0..1) lights storm clouds from within;
 * [fade] (0..1) fades the whole sky in when a new scene is ready.
 */
internal fun DrawScope.drawClouds(scene: CloudScene, seconds: Float, flash: Float, fade: Float) {
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
