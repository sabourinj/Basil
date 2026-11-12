package com.basil.grocyscanner.data

import android.content.SharedPreferences

object GeminiConfig {
    // Used until a model is picked in Settings. Keep the nightly parent Apps
    // Script on the same model so both name parents the same way.
    const val DEFAULT_MODEL = "gemini-3.8-flash"

    // Google's alias for its newest Flash model. It moves without notice
    // (sometimes to a preview model), so it's offered but never the default.
    const val LATEST_FLASH_ALIAS = "gemini-flash-latest"

    const val PREF_KEY = "GEMINI_MODEL"

    // The model every Gemini call in Basil uses, foreground and background
    fun model(prefs: SharedPreferences): String =
        prefs.getString(PREF_KEY, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL
}
