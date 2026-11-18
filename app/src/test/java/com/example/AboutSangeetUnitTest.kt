package com.example

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.sangeet.ui.screens.AboutSangeetScreen
import com.example.sangeet.ui.screens.readActualAppMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class AboutSangeetUnitTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readActualAppMetadata_matchesBuildConfigAndManifestAppName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val metadata = readActualAppMetadata(context)

        assertEquals("SANGEET", metadata.appName)
        assertEquals(BuildConfig.VERSION_NAME, metadata.versionName)
        assertEquals(BuildConfig.VERSION_CODE.toLong(), metadata.versionCode)
    }

    @Test
    fun aboutSangeetScreen_displaysExactDeveloperIdentityAndSections() {
        var navigatedBack = false

        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AboutSangeetScreen(onNavigateBack = { navigatedBack = true })
            }
        }
        composeRule.waitForIdle()

        // 1. Application header, logo, name, and real metadata
        composeRule.onNodeWithTag("about_app_logo").assertIsDisplayed()
        composeRule.onNodeWithTag("about_app_name").assertIsDisplayed()
        composeRule.onNodeWithText("SANGEET").assertIsDisplayed()
        composeRule.onNodeWithTag("about_version_name").assertIsDisplayed()
        composeRule.onNodeWithTag("about_version_code").assertIsDisplayed()
        composeRule.onNodeWithText(BuildConfig.VERSION_NAME).assertIsDisplayed()
        composeRule.onNodeWithText(BuildConfig.VERSION_CODE.toString()).assertIsDisplayed()

        // 2. Exact developer profile identity (case- and formatting-exact)
        composeRule.onNodeWithTag("about_sangeet_list").performScrollToNode(hasTestTag("about_developer_card"))
        composeRule.onNodeWithTag("about_developer_card").assertIsDisplayed()
        composeRule.onNodeWithTag("about_developer_artwork").assertIsDisplayed()
        composeRule.onNodeWithText("🩺 Dr. M. Ganeswar").assertIsDisplayed()
        composeRule.onNodeWithText("drmganeswar").assertIsDisplayed()
        composeRule.onNodeWithText("(BAMS final year)").assertIsDisplayed()

        // 3. Privacy, Open-Source Licenses (expandable), and Copyright sections
        composeRule.onNodeWithTag("about_sangeet_list").performScrollToNode(hasText("PRIVACY INFORMATION", ignoreCase = true))
        composeRule.onNodeWithText("PRIVACY INFORMATION", ignoreCase = true).assertIsDisplayed()

        composeRule.onNodeWithTag("about_sangeet_list").performScrollToNode(hasTestTag("about_licenses_toggle"))
        composeRule.onNodeWithTag("about_licenses_toggle").assertIsDisplayed()
        composeRule.onNodeWithTag("about_licenses_toggle").performClick()
        composeRule.onNodeWithTag("about_sangeet_list").performScrollToNode(hasText("AndroidX Jetpack Compose & Material 3"))
        composeRule.onNodeWithText("AndroidX Jetpack Compose & Material 3").assertIsDisplayed()

        composeRule.onNodeWithTag("about_sangeet_list").performScrollToNode(hasText("COPYRIGHT INFORMATION", ignoreCase = true))
        composeRule.onNodeWithText("COPYRIGHT INFORMATION", ignoreCase = true).assertIsDisplayed()

        // Back navigation button
        composeRule.onNodeWithTag("about_sangeet_back_btn").performClick()
        assertTrue(navigatedBack)
    }
}
