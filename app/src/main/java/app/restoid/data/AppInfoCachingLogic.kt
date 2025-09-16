package app.restoid.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import androidx.core.graphics.drawable.toBitmap
import app.restoid.model.AppInfo
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream

@Serializable
data class CachedAppInfo(
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val apkPath: String,
    val iconBase64: String
)

fun CachedAppInfo.toAppInfo(context: Context): AppInfo {
    return AppInfo(
        name = this.name,
        packageName = this.packageName,
        versionName = this.versionName,
        versionCode = this.versionCode,
        apkPath = this.apkPath,
        icon = this.iconBase64.base64ToDrawable(context)
    )
}

fun AppInfo.toCachedAppInfo(): CachedAppInfo {
    return CachedAppInfo(
        name = this.name,
        packageName = this.packageName,
        versionName = this.versionName,
        versionCode = this.versionCode,
        apkPath = this.apkPath,
        iconBase64 = this.icon.toBase64()
    )
}

private fun Drawable.toBase64(): String {
    val bitmap = this.toBitmap(config = Bitmap.Config.ARGB_8888)
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
    val byteArray = outputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.DEFAULT)
}

private fun String.base64ToDrawable(context: Context): Drawable {
    val decodedString = Base64.decode(this, Base64.DEFAULT)
    val bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
    return BitmapDrawable(context.resources, bitmap)
}

