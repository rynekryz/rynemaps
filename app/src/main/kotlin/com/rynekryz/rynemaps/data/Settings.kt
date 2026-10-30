package com.rynekryz.rynemaps.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import org.json.JSONObject
import java.io.File

enum class UnitsMode(val label: String) { Auto("Auto"), Km("Km"), Miles("Mi") }

enum class ThemeMode(val label: String) { System("System"), Light("Light"), Dark("Dark") }

data class Settings(
    val name: String = "Ryne",
    val voice: Boolean = true,
    val keepScreenOn: Boolean = true,
    val units: UnitsMode = UnitsMode.Auto,
    val theme: ThemeMode = ThemeMode.System,
    val avatarVersion: Int = 0,
)

class SettingsStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("rymaps", Context.MODE_PRIVATE)
    private val avatarDir = File(context.filesDir, "avatar").apply { mkdirs() }
    val avatarFile = File(avatarDir, "avatar.png")

    fun load(): Settings {
        val o = runCatching { JSONObject(prefs.getString("settings", "{}") ?: "{}") }.getOrNull() ?: return Settings()
        val units = runCatching { UnitsMode.valueOf(o.getString("units")) }.getOrDefault(UnitsMode.Auto)
        val theme = runCatching { ThemeMode.valueOf(o.getString("theme")) }.getOrDefault(ThemeMode.System)
        return Settings(
            name = o.optString("name", "Ryne"),
            voice = o.optBoolean("voice", true),
            keepScreenOn = o.optBoolean("keepScreenOn", true),
            units = units,
            theme = theme,
        )
    }

    fun save(settings: Settings) {
        val o = JSONObject()
            .put("name", settings.name)
            .put("voice", settings.voice)
            .put("keepScreenOn", settings.keepScreenOn)
            .put("units", settings.units.name)
            .put("theme", settings.theme.name)
        prefs.edit().putString("settings", o.toString()).apply()
    }

    fun setAvatar(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val full = ImageDecoder.decodeBitmap(source) { d, info, _ ->
            val max = maxOf(info.size.width, info.size.height)
            d.setTargetSampleSize(max / 1024 + 1)
        }
        val side = minOf(full.width, full.height)
        val x = (full.width - side) / 2
        val y = (full.height - side) / 2
        val square = Bitmap.createScaledBitmap(
            Bitmap.createBitmap(full, x, y, side, side), 512, 512, true,
        )
        avatarFile.outputStream().use { square.compress(Bitmap.CompressFormat.PNG, 90, it) }
        return square
    }

    fun readAvatar(): Bitmap? = runCatching { BitmapFactory.decodeFile(avatarFile.path) }.getOrNull()

    fun removeAvatar() {
        avatarFile.delete()
    }
}
