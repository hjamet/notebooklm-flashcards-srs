package com.notebooklm.flashcards.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.documentfile.provider.DocumentFile
import java.io.File

sealed class StorageAccessMode {
    data class Direct(val vaultDir: File) : StorageAccessMode()
    data class Saf(val treeUri: Uri) : StorageAccessMode()
    object None : StorageAccessMode()
}

data class DiscoveredFile(
    val fileName: String,
    val relativePath: String,
    val readText: () -> String
)

class SafStorageManager(private val context: Context) {

    companion object {
        const val DEFAULT_VAULT_PATH = "/sdcard/Documents/VoiceNotes"
        const val PREF_NAME = "storage_prefs"
        const val KEY_SAF_URI = "saf_tree_uri"
    }

    /**
     * Determines current storage access mode based on permissions and stored preferences.
     */
    fun getAccessMode(): StorageAccessMode {
        // 1. Try Direct Storage Access via MANAGE_EXTERNAL_STORAGE or standard external storage
        if (hasDirectStorageAccess()) {
            val vaultDir = getDirectVaultDirectory()
            if (vaultDir.exists()) {
                return StorageAccessMode.Direct(vaultDir)
            }
        }

        // 2. Try SAF Tree URI fallback
        val savedSafUri = getSavedSafUri()
        if (savedSafUri != null) {
            val docFile = DocumentFile.fromTreeUri(context, savedSafUri)
            if (docFile != null && docFile.canRead()) {
                return StorageAccessMode.Saf(savedSafUri)
            }
        }

        return StorageAccessMode.None
    }

    fun hasDirectStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    fun getDirectVaultDirectory(): File {
        val primaryStorage = Environment.getExternalStorageDirectory()
        val voiceNotes = File(primaryStorage, "Documents/VoiceNotes")
        if (!voiceNotes.exists()) {
            voiceNotes.mkdirs()
        }
        return voiceNotes
    }

    fun saveSafUri(uri: Uri) {
        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SAF_URI, uri.toString())
            .apply()
    }

    fun getSavedSafUri(): Uri? {
        val uriStr = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SAF_URI, null) ?: return null
        return try {
            Uri.parse(uriStr)
        } catch (e: Exception) {
            null
        }
    }

    fun createManageStorageIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }

    /**
     * Scan vault for all flashcard CSV files.
     */
    fun findCsvFiles(mode: StorageAccessMode): List<DiscoveredFile> {
        val results = mutableListOf<DiscoveredFile>()

        when (mode) {
            is StorageAccessMode.Direct -> {
                scanDirDirect(mode.vaultDir, mode.vaultDir, results)
            }
            is StorageAccessMode.Saf -> {
                val treeDoc = DocumentFile.fromTreeUri(context, mode.treeUri)
                if (treeDoc != null && treeDoc.isDirectory) {
                    scanDirSaf(treeDoc, "", results)
                }
            }
            StorageAccessMode.None -> {}
        }

        return results
    }

    private fun scanDirDirect(dir: File, baseDir: File, out: MutableList<DiscoveredFile>) {
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.isDirectory) {
                scanDirDirect(f, baseDir, out)
            } else if (f.isFile && f.name.endsWith(".csv", ignoreCase = true)) {
                val relPath = f.relativeTo(baseDir).path.replace('\\', '/')
                out.add(
                    DiscoveredFile(
                        fileName = f.name,
                        relativePath = relPath,
                        readText = { f.readText(Charsets.UTF_8) }
                    )
                )
            }
        }
    }

    private fun scanDirSaf(dir: DocumentFile, currentRelPath: String, out: MutableList<DiscoveredFile>) {
        val files = dir.listFiles()
        for (f in files) {
            val name = f.name ?: continue
            val relPath = if (currentRelPath.isEmpty()) name else "$currentRelPath/$name"
            if (f.isDirectory) {
                scanDirSaf(f, relPath, out)
            } else if (f.isFile && name.endsWith(".csv", ignoreCase = true)) {
                out.add(
                    DiscoveredFile(
                        fileName = name,
                        relativePath = relPath,
                        readText = {
                            context.contentResolver.openInputStream(f.uri)?.use { stream ->
                                stream.bufferedReader(Charsets.UTF_8).readText()
                            } ?: ""
                        }
                    )
                )
            }
        }
    }

    /**
     * Reads flashcards-srs-data.json content with fallback to internal storage.
     */
    fun readSrsJson(mode: StorageAccessMode): String? {
        return when (mode) {
            is StorageAccessMode.Direct -> {
                val flashcardsDir = File(mode.vaultDir, "Flashcards")
                val jsonFile = File(flashcardsDir, "flashcards-srs-data.json")
                if (jsonFile.exists()) jsonFile.readText(Charsets.UTF_8) else readInternalSrsJson()
            }
            is StorageAccessMode.Saf -> {
                val treeDoc = DocumentFile.fromTreeUri(context, mode.treeUri)
                val flashcardsDir = treeDoc?.findFile("Flashcards") ?: treeDoc
                val jsonDoc = flashcardsDir?.findFile("flashcards-srs-data.json")
                if (jsonDoc != null) {
                    context.contentResolver.openInputStream(jsonDoc.uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: readInternalSrsJson()
                } else {
                    readInternalSrsJson()
                }
            }
            StorageAccessMode.None -> readInternalSrsJson()
        }
    }

    /**
     * Writes flashcards-srs-data.json atomically using a .tmp file.
     * Also writes to internal storage as fallback.
     */
    fun writeSrsJsonAtomically(mode: StorageAccessMode, jsonContent: String): Boolean {
        // Always write to internal storage as a backup
        writeInternalSrsJson(jsonContent)

        return when (mode) {
            is StorageAccessMode.Direct -> {
                val flashcardsDir = File(mode.vaultDir, "Flashcards")
                if (!flashcardsDir.exists()) flashcardsDir.mkdirs()

                val tmpFile = File(flashcardsDir, "flashcards-srs-data.json.tmp")
                val targetFile = File(flashcardsDir, "flashcards-srs-data.json")

                try {
                    tmpFile.writeText(jsonContent, Charsets.UTF_8)
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    val renamed = tmpFile.renameTo(targetFile)
                    if (!renamed) {
                        tmpFile.copyTo(targetFile, overwrite = true)
                        tmpFile.delete()
                    }
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            is StorageAccessMode.Saf -> {
                val treeDoc = DocumentFile.fromTreeUri(context, mode.treeUri) ?: return true
                var flashcardsDir = treeDoc.findFile("Flashcards")
                if (flashcardsDir == null || !flashcardsDir.isDirectory) {
                    flashcardsDir = treeDoc.createDirectory("Flashcards") ?: treeDoc
                }

                var targetDoc = flashcardsDir.findFile("flashcards-srs-data.json")
                if (targetDoc == null) {
                    targetDoc = flashcardsDir.createFile("application/json", "flashcards-srs-data.json")
                }

                if (targetDoc == null) return true

                try {
                    context.contentResolver.openOutputStream(targetDoc.uri, "rwt")?.use { out ->
                        out.write(jsonContent.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    true
                }
            }
            StorageAccessMode.None -> true
        }
    }

    fun writeCsvToVault(mode: StorageAccessMode, fileName: String, csvContent: String): Boolean {
        val safeFileName = if (fileName.endsWith(".csv", ignoreCase = true)) fileName else "$fileName.csv"
        return when (mode) {
            is StorageAccessMode.Direct -> {
                try {
                    val csvFile = File(mode.vaultDir, safeFileName)
                    csvFile.writeText(csvContent, Charsets.UTF_8)
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            is StorageAccessMode.Saf -> {
                try {
                    val treeDoc = DocumentFile.fromTreeUri(context, mode.treeUri) ?: return false
                    var doc = treeDoc.findFile(safeFileName)
                    if (doc == null) {
                        doc = treeDoc.createFile("text/csv", safeFileName)
                    }
                    if (doc != null) {
                        context.contentResolver.openOutputStream(doc.uri, "rwt")?.use { out ->
                            out.write(csvContent.toByteArray(Charsets.UTF_8))
                            out.flush()
                        }
                        true
                    } else false
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            StorageAccessMode.None -> false
        }
    }

    private fun readInternalSrsJson(): String? {
        val internalFile = File(context.filesDir, "flashcards-srs-data.json")
        return if (internalFile.exists()) internalFile.readText(Charsets.UTF_8) else null
    }

    private fun writeInternalSrsJson(jsonContent: String): Boolean {
        return try {
            val internalFile = File(context.filesDir, "flashcards-srs-data.json")
            val tmpFile = File(context.filesDir, "flashcards-srs-data.json.tmp")
            tmpFile.writeText(jsonContent, Charsets.UTF_8)
            if (internalFile.exists()) internalFile.delete()
            val renamed = tmpFile.renameTo(internalFile)
            if (!renamed) {
                tmpFile.copyTo(internalFile, overwrite = true)
                tmpFile.delete()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
