package app.restoid.model

import android.graphics.drawable.Drawable
import android.os.Build

data class AppInfo(
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val icon: Drawable,
    val apkPath: String,
    val isSelected: Boolean = true
)

