package app.restoid.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val icon: Drawable,
    val apkPaths: List<String>,
    val isSelected: Boolean = true
)
