package com.cactuslauncher

import com.cactuslauncher.ui.initialMotionQuality
import java.lang.management.ManagementFactory

internal fun desktopMotionQuality() = initialMotionQuality(
    Runtime.getRuntime().availableProcessors(),
    runCatching { (ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean)?.totalMemorySize }.getOrNull(),
    (System.getProperty("skiko.renderApi") ?: System.getenv("SKIKO_RENDER_API")).equals("SOFTWARE", ignoreCase = true),
)
