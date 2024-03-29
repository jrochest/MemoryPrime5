package com.md


import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performGesture
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import kotlinx.coroutines.delay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class SpacedRepeaterActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<SpacedRepeaterActivity>()

    @Test
    fun setupTempTest() {

        val device = UiDevice.getInstance(getInstrumentation())

        device.findObject(UiSelector().textStartsWith("While using the app")).click()

       composeTestRule.onNodeWithContentDescription("Deck name").performTextInput("temp")
        composeTestRule.onNodeWithContentDescription("Save deck").performClick()

        composeTestRule.onNodeWithContentDescription("Create\nnote").performClick()

        composeTestRule.onNodeWithContentDescription("Tap to record question").performClick()

        Thread.sleep(1000)
        composeTestRule.onNodeWithContentDescription("Tap to stop recording question").performClick()
        Thread.sleep(100)

        composeTestRule.onNodeWithContentDescription("Tap to record answer").performClick()

        Thread.sleep(1000)
        composeTestRule.onNodeWithContentDescription("Tap to stop recording answer").performClick()
        Thread.sleep(1000)

        composeTestRule.onNodeWithContentDescription("Save note").performClick()


        Thread.sleep(10000)
    }
}
