package com.munch.reddit.theme

enum class PostCardStyle(val id: String, val displayName: String) {
    CardV1(id = "cardv1", displayName = "Card v1"),
    CardV2(id = "cardv2", displayName = "Card v2");

    companion object {
        fun fromId(id: String): PostCardStyle {
            return values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CardV1
        }
    }
}
