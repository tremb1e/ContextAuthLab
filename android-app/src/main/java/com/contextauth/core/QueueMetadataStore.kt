package com.contextauth.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class QueueMetadata(
    val fileName: String,
    val batchId: String,
    val createdAt: Long,
    val sizeBytes: Long,
    val retryCount: Int,
    val lastError: String,
    val nextRetryAtWallMillis: Long
)

class QueueMetadataStore(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS upload_queue (
                file_name TEXT PRIMARY KEY NOT NULL,
                batch_id TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                size_bytes INTEGER NOT NULL,
                retry_count INTEGER NOT NULL,
                last_error TEXT NOT NULL,
                next_retry_at_wall_millis INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_upload_queue_retry ON upload_queue(next_retry_at_wall_millis, created_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS upload_queue")
        onCreate(db)
    }

    fun upsert(metadata: QueueMetadata) {
        writableDatabase.insertWithOnConflict(
            "upload_queue",
            null,
            ContentValues().apply {
                put("file_name", metadata.fileName)
                put("batch_id", metadata.batchId)
                put("created_at", metadata.createdAt)
                put("size_bytes", metadata.sizeBytes)
                put("retry_count", metadata.retryCount)
                put("last_error", metadata.lastError)
                put("next_retry_at_wall_millis", metadata.nextRetryAtWallMillis)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun delete(fileName: String) {
        writableDatabase.delete("upload_queue", "file_name = ?", arrayOf(fileName))
    }

    fun clear() {
        writableDatabase.delete("upload_queue", null, null)
    }

    fun dueEntries(nowMillis: Long, limit: Int = 20): List<QueueMetadata> {
        val cursor = readableDatabase.query(
            "upload_queue",
            null,
            "next_retry_at_wall_millis <= ?",
            arrayOf(nowMillis.toString()),
            null,
            null,
            "next_retry_at_wall_millis ASC, created_at ASC",
            limit.toString()
        )
        return cursor.use {
            buildList {
                while (it.moveToNext()) add(it.toMetadata())
            }
        }
    }

    fun oldest(): QueueMetadata? {
        val cursor = readableDatabase.query(
            "upload_queue",
            null,
            null,
            null,
            null,
            null,
            "created_at ASC",
            "1"
        )
        return cursor.use { if (it.moveToFirst()) it.toMetadata() else null }
    }

    fun count(): Int {
        val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM upload_queue", null)
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun totalBytes(): Long {
        val cursor = readableDatabase.rawQuery("SELECT COALESCE(SUM(size_bytes), 0) FROM upload_queue", null)
        return cursor.use { if (it.moveToFirst()) it.getLong(0) else 0L }
    }

    private fun android.database.Cursor.toMetadata(): QueueMetadata = QueueMetadata(
        fileName = getString(getColumnIndexOrThrow("file_name")),
        batchId = getString(getColumnIndexOrThrow("batch_id")),
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        sizeBytes = getLong(getColumnIndexOrThrow("size_bytes")),
        retryCount = getInt(getColumnIndexOrThrow("retry_count")),
        lastError = getString(getColumnIndexOrThrow("last_error")),
        nextRetryAtWallMillis = getLong(getColumnIndexOrThrow("next_retry_at_wall_millis"))
    )

    companion object {
        private const val DATABASE_NAME = "upload_queue.db"
        private const val DATABASE_VERSION = 1
    }
}
