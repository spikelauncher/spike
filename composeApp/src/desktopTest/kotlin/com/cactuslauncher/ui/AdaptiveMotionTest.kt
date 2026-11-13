package com.cactuslauncher.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class AdaptiveMotionTest {
    private val good = List(32) { 16_666_667L }
    private val slow = List(32) { 33_333_334L }
    private fun seconds(value: Long) = value * 1_000_000_000L

    @Test fun initialModeUsesHardwareHintsAndHandlesUnknownMemory() {
        assertEquals(MotionQuality.Full, initialMotionQuality(16, 32L shl 30, false))
        assertEquals(MotionQuality.Balanced, initialMotionQuality(4, null, false))
        assertEquals(MotionQuality.Balanced, initialMotionQuality(16, 8L shl 30, false))
        assertEquals(MotionQuality.Reduced, initialMotionQuality(2, 32L shl 30, false))
        assertEquals(MotionQuality.Reduced, initialMotionQuality(16, 4L shl 30, false))
        assertEquals(MotionQuality.Reduced, initialMotionQuality(16, null, true))
        assertEquals(MotionQuality.Full, initialMotionQuality(16, 0, false))
        assertEquals(MotionQuality.Full, initialMotionQuality(16, null, false))
    }

    @Test fun sustainedStallsLowerQualityOneStepAtATime() {
        val controller = AdaptiveMotionController(MotionQuality.Full, 60)
        assertEquals(MotionQuality.Full, controller.record(slow, seconds(5)))
        assertEquals(MotionQuality.Balanced, controller.record(slow, seconds(10)))
        assertEquals(MotionQuality.Balanced, controller.record(slow, seconds(15)))
        assertEquals(MotionQuality.Reduced, controller.record(slow, seconds(20)))
        repeat(4) { assertEquals(MotionQuality.Reduced, controller.record(slow, seconds(25L + it * 5))) }
    }

    @Test fun isolatedHiccupsAndMixedWindowsDoNotCauseFlapping() {
        val controller = AdaptiveMotionController(MotionQuality.Full, 60)
        val hiccup = good.toMutableList().apply { this[2] = 100_000_000L }
        repeat(12) { index ->
            controller.record(if (index % 2 == 0) slow else good, seconds(index * 5L))
            assertEquals(MotionQuality.Full, controller.quality)
        }
        repeat(4) { assertEquals(MotionQuality.Full, controller.record(hiccup, seconds(100L + it * 5))) }
        controller.record(slow, seconds(130))
        controller.record(List(32) { if (it < 4) 33_333_334L else 16_666_667L }, seconds(135))
        assertEquals(MotionQuality.Full, controller.record(slow, seconds(140)))
    }

    @Test fun shortBurstsOfSevereStallsAlsoLowerQuality() {
        val controller = AdaptiveMotionController(MotionQuality.Full, 60)
        val burst = good.toMutableList().apply { this[3] = 100_000_000L; this[6] = 100_000_000L }
        assertEquals(MotionQuality.Full, controller.record(burst, seconds(5)))
        assertEquals(MotionQuality.Balanced, controller.record(burst, seconds(10)))
    }

    @Test fun recoveryRequiresSixHealthyWindowsAndFortyFiveSecondsPerStep() {
        val controller = AdaptiveMotionController(MotionQuality.Reduced, 60)
        repeat(6) { assertEquals(MotionQuality.Reduced, controller.record(good, seconds(5L + it * 5))) }
        assertEquals(MotionQuality.Balanced, controller.record(good, seconds(45)))
        repeat(6) { assertEquals(MotionQuality.Balanced, controller.record(good, seconds(50L + it * 5))) }
        assertEquals(MotionQuality.Full, controller.record(good, seconds(90)))
    }

    @Test fun interruptedAndInvalidWindowsAreIgnoredAndResetStreaks() {
        for (invalid in listOf(emptyList(), good.take(20), good.toMutableList().apply { this[4] = 1_000_000_000L }, good.toMutableList().apply { this[5] = 0L })) {
            val controller = AdaptiveMotionController(MotionQuality.Full, 60)
            controller.record(slow, seconds(5))
            assertEquals(MotionQuality.Full, controller.record(invalid, seconds(10)))
            assertEquals(MotionQuality.Full, controller.record(slow, seconds(15)))
        }
    }

    @Test fun frameBudgetTracksDisplayRefreshRateWithSafeFallback() {
        val fastDisplay = AdaptiveMotionController(MotionQuality.Full, 144)
        repeat(4) { assertEquals(MotionQuality.Full, fastDisplay.record(List(32) { 6_944_444L }, seconds(it * 5L))) }
        fastDisplay.record(good, seconds(25))
        assertEquals(MotionQuality.Balanced, fastDisplay.record(good, seconds(30)))
        for (rate in listOf(0, -1, 10000)) {
            val unknownDisplay = AdaptiveMotionController(MotionQuality.Full, rate)
            repeat(8) { assertEquals(MotionQuality.Full, unknownDisplay.record(good, seconds(it * 5L))) }
        }
        val slowDisplay = AdaptiveMotionController(MotionQuality.Full, 30)
        repeat(8) { assertEquals(MotionQuality.Full, slowDisplay.record(slow, seconds(it * 5L))) }
    }
}
