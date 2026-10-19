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
        CollectionEntity::class,
        BookCollectionRef::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class OmniDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun folderDao(): FolderDao
    abstract fun readerDao(): ReaderDao
    abstract fun collectionDao(): CollectionDao

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

        // Collections (user shelves) and their membership table.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `collections` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_collections_name` ON `collections` (`name`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `book_collections` (" +
                        "`bookId` INTEGER NOT NULL, `collectionId` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`bookId`, `collectionId`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_book_collections_collectionId` " +
                        "ON `book_collections` (`collectionId`)"
                )
            }
        }

        // Notes: the text a reader types is stored next to the selected quote.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `bookmarks` ADD COLUMN `note` TEXT NOT NULL DEFAULT ''")
            }
        }

        fun create(context: Context): OmniDatabase =
            Room.databaseBuilder(context.applicationContext, OmniDatabase::class.java, "omnipage.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}