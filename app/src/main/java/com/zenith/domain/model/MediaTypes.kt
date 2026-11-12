package com.zenith.domain.model

enum class MediaType(val label: String, val icon: String, val color: String, val folderNames: List<String>) {
    IMAGES("Images", "image", "#3B82F6", listOf("WhatsApp Images")),
    VIDEOS("Videos", "video", "#8B5CF6", listOf("WhatsApp Video")),
    AUDIO("Audio", "music", "#F59E0B", listOf("WhatsApp Audio")),
    VOICE_NOTES("Voice Notes", "mic", "#10B981", listOf("WhatsApp Voice Notes")),
    DOCUMENTS("Documents", "file-text", "#EF4444", listOf("WhatsApp Documents")),
    GIFS("GIFs", "film", "#EC4899", listOf("WhatsApp Animated Gifs")),
    STICKERS("Stickers", "smile", "#F97316", listOf("WhatsApp Stickers"))
}

data class MediaFile(
    val path: String,
    val name: String,
    val size: Long,
    val modificationTime: Long,
    val type: MediaType,
    val folder: String = "Received"
)
