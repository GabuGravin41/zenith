package com.zenith.domain

import com.zenith.domain.model.MediaFile
import com.zenith.domain.model.MediaType
import java.io.File

object MediaScanner {
    private val WA_ROOTS = listOf(
        "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media",
        "/storage/emulated/0/WhatsApp/Media",
        "/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media",
        "/storage/emulated/0/WhatsApp Business/Media",
        "/sdcard/Android/media/com.whatsapp/WhatsApp/Media"
    )

    fun findWhatsAppRoot(): String? {
        for (root in WA_ROOTS) {
            val file = File(root)
            if (file.exists() && file.isDirectory) return root
        }
        return null
    }

    fun scanWhatsAppMedia(limitPerType: Int = 1000): Map<MediaType, List<MediaFile>> {
        val root = findWhatsAppRoot() ?: return emptyMap()
        val result = mutableMapOf<MediaType, MutableList<MediaFile>>()

        for (type in MediaType.values()) {
            result[type] = mutableListOf()
            for (folderName in type.folderNames) {
                val dir = File(root, folderName)
                scanDirectoryIterative(dir, type, result[type]!!, limitPerType)
            }
        }
        return result
    }

    private fun scanDirectoryIterative(startDir: File, type: MediaType, list: MutableList<MediaFile>, limit: Int) {
        if (!startDir.exists() || !startDir.isDirectory) return

        val stack = mutableListOf<File>()
        stack.add(startDir)

        while (stack.isNotEmpty() && list.size < limit) {
            val dir = stack.removeAt(stack.size - 1)
            val files = try { dir.listFiles() } catch (e: Exception) { null } ?: continue

            for (file in files) {
                if (file.name.startsWith(".")) continue
                if (list.size >= limit) break

                if (file.isDirectory) {
                    stack.add(file)
                } else {
                    val folder = when {
                        file.parentFile?.name?.contains("Sent", ignoreCase = true) == true -> "Sent"
                        file.parentFile?.name?.contains("Private", ignoreCase = true) == true -> "Private"
                        else -> "Received"
                    }
                    list.add(
                        MediaFile(
                            path = file.absolutePath,
                            name = file.name,
                            size = file.length(),
                            modificationTime = file.lastModified(),
                            type = type,
                            folder = folder
                        )
                    )
                }
            }
        }
    }

    fun deleteFiles(paths: List<String>): Pair<Int, Int> {
        var deleted = 0
        var failed = 0
        for (path in paths) {
            try {
                val file = File(path)
                if (file.exists() && file.delete()) {
                    deleted++
                } else {
                    failed++
                }
            } catch (e: Exception) {
                failed++
            }
        }
        return Pair(deleted, failed)
    }

    fun findLargeFiles(minSizeMb: Int = 10, limit: Int = 50): List<MediaFile> {
        val root = findWhatsAppRoot() ?: return emptyList()
        val minSize = minSizeMb * 1024L * 1024L
        val largeFiles = mutableListOf<MediaFile>()
        
        for (type in MediaType.values()) {
            for (folderName in type.folderNames) {
                val dir = File(root, folderName)
                scanLargeFilesIterative(dir, type, minSize, largeFiles, limit)
            }
        }
        return largeFiles.sortedByDescending { it.size }.take(limit)
    }

    private fun scanLargeFilesIterative(startDir: File, type: MediaType, minSize: Long, list: MutableList<MediaFile>, limit: Int) {
        if (!startDir.exists() || !startDir.isDirectory) return
        
        val stack = mutableListOf<File>()
        stack.add(startDir)

        while (stack.isNotEmpty() && list.size < limit) {
            val dir = stack.removeAt(stack.size - 1)
            val files = try { dir.listFiles() } catch (e: Exception) { null } ?: continue

            for (file in files) {
                if (file.name.startsWith(".")) continue
                
                if (file.isDirectory) {
                    stack.add(file)
                } else if (file.length() >= minSize) {
                    val folder = when {
                        file.parentFile?.name?.contains("Sent", ignoreCase = true) == true -> "Sent"
                        file.parentFile?.name?.contains("Private", ignoreCase = true) == true -> "Private"
                        else -> "Received"
                    }
                    list.add(
                        MediaFile(
                            path = file.absolutePath,
                            name = file.name,
                            size = file.length(),
                            modificationTime = file.lastModified(),
                            type = type,
                            folder = folder
                        )
                    )
                    if (list.size >= limit) break
                }
            }
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = listOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
