package com.geotagcv

import android.Manifest
import android.content.pm.ActivityInfo
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.android.material.textfield.TextInputLayout
import androidx.recyclerview.widget.RecyclerView
import com.dangiashish.GeoTagImage.ImageStyle
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityFeatureTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun resetSavedTemplate() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("geo_tag_photo_settings", 0)
            .edit()
            .remove("selected_image_style")
            .commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun closeActivity() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun templates_canSwitchAwayAndBackToDefaultFromTopAction() {
        onView(withId(R.id.navTemplates)).perform(click())
        onView(withId(R.id.btnDefaultTemplate)).check(matches(isDisplayed()))
        onView(withId(R.id.btnDefaultTemplate)).check(matches(not(isEnabled())))

        scenario.onActivity {
            it.findViewById<RecyclerView>(R.id.templatesList).scrollToPosition(1)
        }
        onView(
            allOf(
                withId(R.id.btnUseTemplate),
                isDescendantOfA(withTagValue(`is`(ImageStyle.LANDSCAPE.name)))
            )
        ).perform(click())

        onView(withId(R.id.navTemplates)).perform(click())
        onView(withId(R.id.btnDefaultTemplate)).check(matches(isEnabled()))
        onView(withId(R.id.btnDefaultTemplate)).perform(scrollTo(), click())

        onView(withId(R.id.navTemplates)).perform(click())
        onView(withId(R.id.btnDefaultTemplate)).check(matches(not(isEnabled())))
        onView(withText(R.string.template_active_classic)).perform(scrollTo())
            .check(matches(isDisplayed()))
    }

    @Test
    fun portraitCamera_reportsDefaultTemplateAndPortraitFraming() {
        scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        SystemClock.sleep(700)

        onView(withId(R.id.btnCapture)).perform(scrollTo(), click())
        onView(withText("Smart • Default template")).check(matches(isDisplayed()))
        onView(withSubstring("portrait 4:3 framing")).check(matches(isDisplayed()))
        pressBack()
    }

    @Test
    fun customLocationFields_acceptValidEditedMetadata() {
        onView(withId(R.id.customMetadataSwitch)).perform(scrollTo(), click())
        SystemClock.sleep(1_000)
        onView(withId(R.id.etCustomPlace)).perform(scrollTo(), replaceText("Edited place"))
        onView(withId(R.id.etCustomAddress)).perform(replaceText("Edited address"))
        onView(withId(R.id.etCustomLatitude)).perform(replaceText("13.041800"))
        onView(withId(R.id.etCustomLongitude)).perform(
            replaceText("80.234100"),
            closeSoftKeyboard()
        )

        onView(withId(R.id.etCustomPlace)).check(matches(withText("Edited place")))
        onView(withId(R.id.etCustomAddress)).check(matches(withText("Edited address")))
        scenario.onActivity { activity ->
            assertNull(activity.findViewById<TextInputLayout>(R.id.latitudeInputLayout).error)
            assertNull(activity.findViewById<TextInputLayout>(R.id.longitudeInputLayout).error)
        }
    }

    @Test
    fun primaryNavigation_opensEveryFeaturePage() {
        onView(withId(R.id.navTemplates)).perform(click())
        onView(withText(R.string.templates_title)).check(matches(isDisplayed()))
        onView(withId(R.id.navRecent)).perform(click())
        onView(withText(R.string.recent_title)).check(matches(isDisplayed()))
        onView(withId(R.id.navSaved)).perform(click())
        onView(withText(R.string.saved_photos_title)).check(matches(isDisplayed()))
        onView(withId(R.id.navCamera)).perform(click())
        onView(withId(R.id.btnCapture)).check(matches(isDisplayed()))
    }

    @Test
    fun applicationId_isCorrect() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.geotagcv", appContext.packageName)
    }
}
