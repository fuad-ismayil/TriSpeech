package com.trispeech.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "transcripts", indices = [Index(value = ["operationId"], unique = true)])
data class TranscriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val text: String,
    val summary: String = "",
    val languageCode: String,
    val engine: String,
    val model: String,
    val createdAtEpochMs: Long,
    val durationMs: Long,
    val operationId: String? = null
)

@Entity(
    tableName = "audio_metadata",
    foreignKeys = [ForeignKey(
        entity = TranscriptEntity::class,
        parentColumns = ["id"], childColumns = ["transcriptId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("transcriptId")]
)
data class AudioMetadataEntity(
    @PrimaryKey val transcriptId: Long,
    val filePath: String,
    val mimeType: String = "audio/wav",
    val sizeBytes: Long,
    val sampleRate: Int = 16_000,
    val channels: Int = 1
)
