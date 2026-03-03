package com.ybugmobile.vaktiva.ui.donation

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.manager.DonationProduct
import com.ybugmobile.vaktiva.domain.manager.PurchaseResult
import com.ybugmobile.vaktiva.ui.theme.GlassTheme
import com.ybugmobile.vaktiva.ui.theme.LocalGlassTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonateScreen(
    onBack: () -> Unit,
    viewModel: DonateViewModel = hiltViewModel()
) {
    val glassTheme = LocalGlassTheme.current
    val products by viewModel.donationProducts.collectAsState(initial = emptyList())
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.purchaseEvents.collect { result ->
            when (result) {
                is PurchaseResult.Success -> {
                    Toast.makeText(context, R.string.donate_thank_you, Toast.LENGTH_LONG).show()
                }
                is PurchaseResult.Error -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
                is PurchaseResult.UserCancelled -> {}
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.donate_title).uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        ),
                        color = Color.White.copy(alpha = 0.8f)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 60.dp),
        ) {
            // HERO SECTION: Elevated Visual with Premium Starburst and Stunning Badges
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .padding(top = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Premium Starburst Background
                    PremiumStarburst(
                        modifier = Modifier.size(340.dp),
                        color = Color(0xFFF87171)
                    )

                    // Multi-layered Glow
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .blur(80.dp)
                            .background(Color(0xFFF87171).copy(alpha = 0.15f), CircleShape)
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            // Main App Icon Container
                            Surface(
                                modifier = Modifier.size(120.dp),
                                shape = RoundedCornerShape(32.dp),
                                color = Color.White.copy(alpha = 0.12f),
                                border = BorderStroke(1.5.dp, Brush.linearGradient(
                                    listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.05f))
                                ))
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                    contentDescription = null,
                                    modifier = Modifier.padding(20.dp).clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            
                            // Floating Heart Indicator
                            Surface(
                                modifier = Modifier
                                    .size(38.dp)
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 10.dp, y = 10.dp),
                                shape = CircleShape,
                                color = Color(0xFFF87171),
                                border = BorderStroke(2.dp, Color.Black.copy(alpha = 0.3f)),
                                shadowElevation = 12.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Favorite,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Principle Badges - Designed to be "Stunning"
                            StunningInfoBadge(
                                text = "No ads.",
                                modifier = Modifier.align(Alignment.TopStart).offset(x = (-40).dp, y = (-20).dp),
                                rotation = -12f,
                                delayMillis = 0
                            )
                            StunningInfoBadge(
                                text = "No tracking.",
                                modifier = Modifier.align(Alignment.TopEnd).offset(x = 50.dp, y = 15.dp),
                                rotation = 10f,
                                delayMillis = 200
                            )
                            StunningInfoBadge(
                                text = "No data selling.",
                                modifier = Modifier.align(Alignment.BottomStart).offset(x = (-60).dp, y = 10.dp),
                                rotation = -6f,
                                delayMillis = 400
                            )
                            StunningInfoBadge(
                                text = "Just something honest and useful.",
                                isHighlight = true,
                                modifier = Modifier.align(Alignment.BottomCenter).offset(y = 65.dp),
                                delayMillis = 600
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(80.dp))
                        
                        Text(
                            text = stringResource(R.string.donate_header),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-1.5).sp
                            ),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }

            // THE MANIFESTO GLASS CARD
            item {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(36.dp),
                    color = Color.White.copy(alpha = 0.03f),
                    border = BorderStroke(1.dp, Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.02f))
                    ))
                ) {
                    Column(modifier = Modifier.padding(32.dp)) {
                        Text(
                            text = stringResource(R.string.settings_donate).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                            color = Color(0xFFF87171).copy(alpha = 0.9f),
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = stringResource(R.string.donate_desc),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                lineHeight = 28.sp,
                                letterSpacing = 0.2.sp
                            ),
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // INTERACTIVE SECTION
            item {
                Column(
                    modifier = Modifier
                        .padding(top = 48.dp, bottom = 24.dp)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_preferences).uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp),
                        color = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.padding(start = 12.dp)
                    )

                    CommunityRatingCard(onClick = { viewModel.onRateClick() })

                    if (products.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White.copy(alpha = 0.2f), strokeWidth = 2.dp)
                        }
                    } else {
                        products.forEach { product ->
                            PremiumSupportCard(
                                product = product,
                                glassTheme = glassTheme,
                                onClick = { viewModel.onDonateClick(product) }
                            )
                        }
                    }
                }
            }

            // FOOTER
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = stringResource(R.string.donate_footer),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    ),
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp)
                )
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

@Composable
private fun PremiumStarburst(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    val infiniteTransition = rememberInfiniteTransition(label = "starburst")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(60000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier.scale(pulse)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2
        val rayCount = 12
        val angleStep = (2 * PI / rayCount).toFloat()

        withTransform({
            rotate(rotation, center)
        }) {
            for (i in 0 until rayCount) {
                val angle = i * angleStep
                val x = center.x + cos(angle.toDouble()).toFloat() * radius
                val y = center.y + sin(angle.toDouble()).toFloat() * radius
                
                drawLine(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = 0.15f), Color.Transparent),
                        center = center,
                        radius = radius
                    ),
                    start = center,
                    end = Offset(x, y),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun StunningInfoBadge(
    text: String,
    modifier: Modifier = Modifier,
    isHighlight: Boolean = false,
    rotation: Float = 0f,
    delayMillis: Int = 0
) {
    val infiniteTransition = rememberInfiniteTransition(label = "badgeFloating")

    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, delayMillis = delayMillis, easing = SineWaveEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                rotationZ = rotation
                translationY = floatOffset
                scaleX = pulse
                scaleY = pulse
            },
        contentAlignment = Alignment.Center
    ) {

        Canvas(
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .height(48.dp)
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val outerRadius = size.minDimension / 2
            val innerRadius = outerRadius * 0.6f
            val points = 12

            val path = Path()

            for (i in 0 until points * 2) {
                val angle = (Math.PI / points * i).toFloat()
                val radius = if (i % 2 == 0) outerRadius else innerRadius

                val x = center.x + cos(angle) * radius
                val y = center.y + sin(angle) * radius

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            path.close()

            drawPath(
                path = path,
                brush = Brush.radialGradient(
                    colors = if (isHighlight) {
                        listOf(
                            Color(0xFFF87171),
                            Color(0xFFE11D48)
                        )
                    } else {
                        listOf(
                            Color(0xFFFF8A80),
                            Color(0xFFEF4444)
                        )
                    }
                )
            )
        }

        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (isHighlight) 12.sp else 10.sp,
                letterSpacing = 0.5.sp
            ),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

private val SineWaveEasing = Easing { fraction ->
    sin(fraction * PI.toFloat() * 2f) / 2f + 0.5f
}

@Composable
fun CommunityRatingCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFFFFD700).copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFFFD700).copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.rate_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.rate_desc),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun PremiumSupportCard(
    product: DonationProduct,
    glassTheme: GlassTheme,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = glassTheme.containerColor,
        border = BorderStroke(1.dp, glassTheme.borderColor)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("☕", fontSize = 24.sp)
                }
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (product.description.isNotEmpty()) {
                    Text(
                        text = product.description,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.4f),
                        maxLines = 1
                    )
                }
            }
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = product.price,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
            }
        }
    }
}
