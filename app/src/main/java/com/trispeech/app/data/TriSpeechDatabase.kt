package com.trispeech.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptDao {
    @Query("SELECT * FROM transcripts ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<TranscriptEntity>>

    @Query("SELECT * FROM transcripts WHERE id = :id")
    fun observe(id: Long): Flow<TranscriptEntity?>

    @Query("SELECT * FROM transcripts WHERE operationId = :operationId LIMIT 1")
    suspend fun getByOperationId(operationId: String): TranscriptEntity?

    @Query("SELECT * FROM transcripts WHERE title LIKE '%' || :query || '%' OR text LIKE '%' || :query || '%' ORDER BY createdAtEpochMs DESC")
    fun search(query: String): Flow<List<TranscriptEntity>>

    @Insert suspend fun insert(transcript: TranscriptEntity): Long
    @Update suspend fun update(transcript: TranscriptEntity)
    @Delete suspend fun delete(transcript: TranscriptEntity)
    @Query("DELETE FROM transcripts")
    suspend fun deleteAll()
}

@Dao
interface AudioMetadataDao {
    @Query("SELECT * FROM audio_metadata WHERE transcriptId = :id")
    suspend fun get(id: Long): AudioMetadataEntity?
    @Query("SELECT * FROM audio_metadata")
    suspend fun getAll(): List<AudioMetadataEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(metadata: AudioMetadataEntity)
}

@Database(entities = [TranscriptEntity::class, AudioMetadataEntity::class], version = 2, exportSchema = true)
abstract class TriSpeechDatabase : RoomDatabase() {
    abstract fun transcriptDao(): TranscriptDao
    abstract fun audioMetadataDao(): AudioMetadataDao

    companion object {
        @Volatile private var instance: TriSpeechDatabase? = null
        fun get(context: Context): TriSpeechDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TriSpeechDatabase::class.java, "trispeech.db")
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transcripts ADD COLUMN operationId TEXT")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transcripts_operationId ON transcripts(operationId)")
            }
        }
    }
}
