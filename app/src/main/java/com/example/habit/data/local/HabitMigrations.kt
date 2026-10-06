package com.example.habit.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Instant
import java.time.ZoneId

object HabitMigrations {
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS coach_messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, habit_id INTEGER NOT NULL, role TEXT NOT NULL, text TEXT NOT NULL, created_at INTEGER NOT NULL, exchange_id TEXT NOT NULL, FOREIGN KEY(habit_id) REFERENCES habits(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_coach_messages_habit_id ON coach_messages(habit_id)")
            db.execSQL("CREATE TABLE IF NOT EXISTS coach_caches (id TEXT NOT NULL PRIMARY KEY, habit_id INTEGER NOT NULL, request TEXT NOT NULL, response TEXT NOT NULL, baseline TEXT NOT NULL, catalog_sha256 TEXT NOT NULL, created_at INTEGER NOT NULL, FOREIGN KEY(habit_id) REFERENCES habits(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_coach_caches_habit_id ON coach_caches(habit_id)")
            db.execSQL("CREATE TABLE IF NOT EXISTS coach_actions (id TEXT NOT NULL PRIMARY KEY, habit_id INTEGER NOT NULL, strategy_id TEXT NOT NULL, inverse TEXT NOT NULL, applied_at INTEGER NOT NULL, status TEXT NOT NULL, confirmation TEXT NOT NULL, FOREIGN KEY(habit_id) REFERENCES habits(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_coach_actions_habit_id ON coach_actions(habit_id)")
            db.execSQL("CREATE TABLE IF NOT EXISTS habit_field_versions (habit_id INTEGER NOT NULL, field TEXT NOT NULL, revision INTEGER NOT NULL, PRIMARY KEY(habit_id, field), FOREIGN KEY(habit_id) REFERENCES habits(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        }
    }
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE habits ADD COLUMN created_epoch_day INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE habits ADD COLUMN archived_epoch_day INTEGER")
            db.execSQL("ALTER TABLE habits ADD COLUMN cue TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE habits ADD COLUMN anchor TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE habits ADD COLUMN plan_note TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE habits ADD COLUMN reminder_enabled INTEGER")
            db.execSQL("ALTER TABLE habits ADD COLUMN reminder_minute INTEGER")
            db.execSQL("ALTER TABLE completions ADD COLUMN tracking_mode TEXT NOT NULL DEFAULT 'BINARY'")
            db.execSQL("ALTER TABLE completions ADD COLUMN quantity_amount TEXT")
            db.execSQL("ALTER TABLE completions ADD COLUMN quantity_unit TEXT")
            db.execSQL("CREATE TABLE IF NOT EXISTS schedule_history (habit_id INTEGER NOT NULL, effective_day INTEGER NOT NULL, kind TEXT NOT NULL, weekday_mask INTEGER NOT NULL, quota INTEGER, PRIMARY KEY(habit_id, effective_day), FOREIGN KEY(habit_id) REFERENCES habits(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE TABLE IF NOT EXISTS tracking_history (habit_id INTEGER NOT NULL, effective_day INTEGER NOT NULL, mode TEXT NOT NULL, target TEXT, unit TEXT, PRIMARY KEY(habit_id, effective_day), FOREIGN KEY(habit_id) REFERENCES habits(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            // Freeze the local dates previously resolved by v1, without changing original timestamps.
            val zone = ZoneId.systemDefault()
            db.query("SELECT id, created_at, archived_at, frequency, scheduled_days FROM habits").use { rows ->
                while (rows.moveToNext()) {
                    val id = rows.getLong(0)
                    val createdDay = Instant.ofEpochMilli(rows.getLong(1)).atZone(zone).toLocalDate().toEpochDay()
                    val archivedDay = if (rows.isNull(2)) null else Instant.ofEpochMilli(rows.getLong(2)).atZone(zone).toLocalDate().toEpochDay()
                    db.execSQL("UPDATE habits SET created_epoch_day = ?, archived_epoch_day = ? WHERE id = ?", arrayOf<Any?>(createdDay, archivedDay, id))
                    // v1's calculator used the mask even for Daily; preserve that exact eligibility.
                    val mask = rows.getInt(4)
                    val kind = if (rows.getString(3) == "DAILY" && mask == 127) "DAILY" else "CUSTOM"
                    db.execSQL("INSERT INTO schedule_history VALUES (?, ?, ?, ?, NULL)", arrayOf<Any>(id, createdDay, kind, mask))
                    db.execSQL("INSERT INTO tracking_history VALUES (?, ?, 'BINARY', NULL, NULL)", arrayOf<Any>(id, createdDay))
                }
            }
        }
    }
}
