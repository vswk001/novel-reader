package com.moyue.reader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class,
        ChapterEntity::class,
        BookmarkEntity::class,
        ReadStatEntity::class,
        HighlightEntity::class,
        BookImageEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun readStatDao(): ReadStatDao
    abstract fun highlightDao(): HighlightDao
    abstract fun bookImageDao(): BookImageDao

    companion object {
        /** v1→v2：书籍增加 space 字段（0=普通书架，1=隐私空间），保留现有数据 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN space INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v2→v3：书籍增加 deletedAt 字段（回收站，0=正常，>0=删除时间戳） */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN deletedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v4→v5：书籍增加 tags 字段（分组标签，JSON 数组字符串） */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /** v3→v4：划线高亮 + EPUB 内联图片占位两张新表 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS highlights (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "bookId INTEGER NOT NULL, chapterIdx INTEGER NOT NULL, start INTEGER NOT NULL, " +
                        "end INTEGER NOT NULL, text TEXT NOT NULL, note TEXT NOT NULL, " +
                        "color INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS book_images (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "bookId INTEGER NOT NULL, chapterIdx INTEGER NOT NULL, charOffset INTEGER NOT NULL, " +
                        "fileName TEXT NOT NULL, srcW INTEGER NOT NULL, srcH INTEGER NOT NULL)"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "moyue.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
