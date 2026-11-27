package com.eddyizm.tempus.util

/** Whether a response to a range request starts at the requested byte, as `Content-Range` shows. */
internal fun Map<String, List<String>>.hasContentRange(): Boolean =
    keys.any { "Content-Range".equals(it, ignoreCase = true) }
