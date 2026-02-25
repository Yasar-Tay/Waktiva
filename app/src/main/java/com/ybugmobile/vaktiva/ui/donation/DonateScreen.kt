package com.ybugmobile.vaktiva.ui.donation

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
            // HERO SECTION: Visual Focal Point
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                ) {
                    // Soft organic glow
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .align(Alignment.Center)
                            .blur(80.dp)
                            .background(Color(0xFFF87171).copy(alpha = 0.15f), CircleShape)
                    )

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.donate_header),
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-1).sp
                            ),
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // PHILOSOPHY BADGES: Broken down values for visual design
            item {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PhilosophyBadge("No Ads", true)
                    PhilosophyBadge("No Tracking")
                    PhilosophyBadge("Privacy First", true)
                    PhilosophyBadge("No Data Selling")
                    PhilosophyBadge("Ethical Build")
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // THE MANIFESTO: Description in a styled Glass Card
            item {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(36.dp),
                    color = Color.White.copy(alpha = 0.03f),
                    border = BorderStroke(1.dp, Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.02f))
                    ))
                ) {
                    Column(modifier = Modifier.padding(32.dp)) {
                        Text(
                            text = stringResource(R.string.settings_donate).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                            color = Color(0xFFF87171).copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = stringResource(R.string.donate_desc),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                lineHeight = 28.sp,
                                letterSpacing = 0.3.sp
                            ),
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            // CONTRIBUTION SECTION
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

                    // Community Rating Card
                    CommunityRatingCard(onClick = { viewModel.onRateClick() })

                    if (products.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(100.dp),
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

            // FOOTER: Personal touch
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
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun PhilosophyBadge(text: String, highlighted: Boolean = false) {
    Surface(
        modifier = Modifier.padding(horizontal = 4.dp),
        shape = CircleShape,
        color = if (highlighted) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (highlighted) Color.White else Color.White.copy(alpha = 0.6f),
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal
        )
    }
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
