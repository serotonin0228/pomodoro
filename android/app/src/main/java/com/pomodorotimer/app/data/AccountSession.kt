package com.pomodorotimer.app.data

import org.json.JSONObject

internal fun accountFromSession(raw: String?): String? = raw?.let {
    runCatching { JSONObject(it).getString("userId").takeIf(String::isNotBlank) }.getOrNull()
}
