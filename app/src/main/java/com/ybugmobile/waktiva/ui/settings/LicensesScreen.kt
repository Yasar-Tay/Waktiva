package com.ybugmobile.waktiva.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.licenses_title).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        ),
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            LicenseSection(
                title = stringResource(R.string.license_legal_info),
                content = "${stringResource(R.string.license_app_copyright)}\n\n${stringResource(R.string.license_app_mit)}\n\n${stringResource(R.string.license_app_warranty)}"
            )

            LicenseSection(
                title = stringResource(R.string.license_third_party_services),
                content = "${stringResource(R.string.license_aladhan_api)}\n\n${stringResource(R.string.license_aladhan_desc)}\n\n${stringResource(R.string.license_data_privacy)}"
            )

            LicenseSection(
                title = stringResource(R.string.license_open_source_libs),
                content = "${stringResource(R.string.license_libs_intro)}\n\n" +
                        "• ${stringResource(R.string.license_lib_androidx)}\n" +
                        "• ${stringResource(R.string.license_lib_compose)}\n" +
                        "• ${stringResource(R.string.license_lib_kotlin)}\n" +
                        "• ${stringResource(R.string.license_lib_hilt)}\n" +
                        "• ${stringResource(R.string.license_lib_room)}\n" +
                        "• ${stringResource(R.string.license_lib_datastore)}\n" +
                        "• ${stringResource(R.string.license_lib_work)}\n" +
                        "• ${stringResource(R.string.license_lib_media3)}\n" +
                        "• ${stringResource(R.string.license_lib_location)}\n" +
                        "• ${stringResource(R.string.license_lib_retrofit)}\n" +
                        "• ${stringResource(R.string.license_lib_okhttp)}\n" +
                        "• ${stringResource(R.string.license_lib_gson)}\n" +
                        "• ${stringResource(R.string.license_lib_adhan)}\n" +
                        "• ${stringResource(R.string.license_lib_suncalc)}\n" +
                        "• ${stringResource(R.string.license_lib_maplibre)}\n" +
                        "• ${stringResource(R.string.license_lib_osmdroid)}\n" +
                        "• ${stringResource(R.string.license_lib_accompanist)}\n" +
                        "• ${stringResource(R.string.license_lib_billing)}\n\n" +
                        stringResource(R.string.license_libs_property)
            )

            LicenseSection(
                title = stringResource(R.string.license_adhan_audio),
                content = "${stringResource(R.string.license_adhan_reciter)}\n\n${stringResource(R.string.license_adhan_permission)}\n\n${stringResource(R.string.license_adhan_restrictions)}"
            )

            LicenseSection(
                title = stringResource(R.string.license_location_permissions),
                content = "${stringResource(R.string.license_location_usage)}\n" +
                        "• ${stringResource(R.string.license_loc_usage_1)}\n" +
                        "• ${stringResource(R.string.license_loc_usage_2)}\n" +
                        "• ${stringResource(R.string.license_loc_usage_3)}\n\n" +
                        stringResource(R.string.license_loc_processing)
            )

            LicenseSection(
                title = stringResource(R.string.license_no_ads),
                content = "• ${stringResource(R.string.license_no_ads_1)}\n" +
                        "• ${stringResource(R.string.license_no_ads_2)}\n" +
                        "• ${stringResource(R.string.license_no_ads_3)}\n" +
                        "• ${stringResource(R.string.license_no_ads_4)}"
            )

            LicenseSection(
                title = stringResource(R.string.license_source_code),
                content = stringResource(R.string.license_github_link)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LicenseSection(title: String, content: String) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            color = Color.White.copy(alpha = 0.1f),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
