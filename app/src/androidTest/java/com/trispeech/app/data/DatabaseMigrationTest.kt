package com.trispeech.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TriSpeechDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun versionOneMigratesWithoutLosingHistory() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL("INSERT INTO transcripts (id,title,text,summary,languageCode,engine,model,createdAtEpochMs,durationMs) VALUES (1,'Saved','Text','','en','NATIVE_STT','BEST_BALANCE',1,1000)")
            close()
        }
        helper.runMigrationsAndValidate(TEST_DATABASE, 2, true, TriSpeechDatabase.MIGRATION_1_2).use { database ->
            database.query("SELECT title, operationId FROM transcripts WHERE id = 1").use { cursor ->
                assert(cursor.moveToFirst())
                assert(cursor.getString(0) == "Saved")
                assert(cursor.isNull(1))
            }
        }
    }

    companion object { private const val TEST_DATABASE = "migration-test" }
}
