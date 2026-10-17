package com.ansh.omnipage.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class,
        BookFts::class,
        FolderEntity::class,
        ReadingPositionEntity::class,
        BookmarkEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class OmniDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun folderDao(): FolderDao
    abstract fun readerDao(): ReaderDao

    companion object {
        // add reading positions + bookmarks/quotes for PDF as well.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reading_positions` (" +
                        "`bookId` INTEGER NOT NULL, `locator` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`bookId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bookmarks` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER NOT NULL, " +
                        "`kind` TEXT NOT NULL, `locator` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                        "`quote` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_bookId` ON `bookmarks` (`bookId`)")
            }
        }

        fun create(context: Context): OmniDatabase =
            Room.databaseBuilder(context.applicationContext, OmniDatabase::class.java, "omnipage.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
