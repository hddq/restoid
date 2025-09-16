package app.restoid.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    val apkPath: String,
    val isSelected: Boolean = true
)
