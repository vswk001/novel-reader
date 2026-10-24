package com.moyue.reader.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE space = :space AND deletedAt = 0 ORDER BY pinned DESC, lastReadAt DESC")
    fun observeAll(space: Int): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE space = :space AND deletedAt = 0")
    suspend fun getAllOnce(space: Int): List<BookEntity>

    @Query("SELECT * FROM books WHERE deletedAt = 0")
    suspend fun getAll(): List<BookEntity>

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Query("UPDATE books SET lastReadAt = :ts, curChapter = :chapter, curCharOffset = :offset WHERE id = :id")
    suspend fun updateProgress(id: Long, chapter: Int, offset: Int, ts: Long)

    @Query("UPDATE books SET readTimeMillis = readTimeMillis + :delta WHERE id = :id")
    suspend fun addReadTime(id: Long, delta: Long)

    @Query("UPDATE books SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE books SET space = :space WHERE id = :id")
    suspend fun setSpace(id: Long, space: Int)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM books WHERE title = :title AND sizeBytes = :size AND space = :space AND deletedAt = 0")
    suspend fun countDuplicate(title: String, size: Long, space: Int): Int

    @Query("SELECT * FROM books WHERE deletedAt > 0 AND space = :space ORDER BY deletedAt DESC")
    suspend fun listTrash(space: Int): List<BookEntity>

    @Query("UPDATE books SET deletedAt = :ts, pinned = 0 WHERE id = :id")
    suspend fun setDeletedAt(id: Long, ts: Long)

    @Query("SELECT * FROM books WHERE deletedAt > 0 AND deletedAt < :before")
    suspend fun listExpired(before: Long): List<BookEntity>
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY idx")
    suspend fun list(bookId: Long): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY idx")
    fun observeList(bookId: Long): Flow<List<ChapterEntity>>

    @Insert
    suspend fun insertAll(items: List<ChapterEntity>)

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: Long)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY chapterIdx ASC, charOffset ASC")
    fun observeForBook(bookId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId")
    suspend fun listForBook(bookId: Long): List<BookmarkEntity>

    @Insert
    suspend fun insert(b: BookmarkEntity): Long

    @Delete
    suspend fun delete(b: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: Long)
}

@Dao
interface ReadStatDao {
    @Query("SELECT * FROM read_stats WHERE date = :date")
    suspend fun get(date: String): ReadStatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stat: ReadStatEntity)

    @Query("SELECT COALESCE(SUM(millis), 0) FROM read_stats")
    suspend fun totalMillis(): Long

    @Query("SELECT * FROM read_stats")
    suspend fun getAllOnce(): List<ReadStatEntity>
}
