package com.dd.daykit.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Main app database
 */
@Database(
    entities = [CustomAlarmSound::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun customAlarmSoundDao(): CustomAlarmSoundDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // v1 -> v2: haSoundUrl kolom (zie CustomAlarmSound.kt). Expliciete migratie i.p.v.
        // fallbackToDestructiveMigration() - dat laatste zou alle bestaande custom geluiden
        // van gebruikers die al op v1 zitten stilletjes wissen.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE custom_alarm_sounds ADD COLUMN haSoundUrl TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "agendawekker_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
