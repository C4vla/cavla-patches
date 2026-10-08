package app.cavla.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    // Placeholder kept so the template example patch compiles; removed once real patches land.
    val COMPATIBILITY_EXAMPLE = Compatibility(
        name = "XYZ app",
        packageName = "com.example.app",
        apkFileType = ApkFileType.APK,
        appIconColor = 0xFF0045,
        targets = listOf(
            AppTarget(version = "1.0.0"),
        ),
    )

    val COMPATIBILITY_WOLT = Compatibility(
        name = "Wolt",
        packageName = "com.wolt.android",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x00C2E8,
        targets = listOf(
            AppTarget(version = "26.40.1"),
        ),
    )
}
