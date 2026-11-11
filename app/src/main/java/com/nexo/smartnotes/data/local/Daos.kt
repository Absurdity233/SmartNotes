package com.nexo.smartnotes.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query(
        """
        SELECT * FROM notes
        WHERE accountId = :accountId AND trashed = 0
        ORDER BY pinned DESC, updatedAt DESC
        """
    )
    fun observeActive(accountId: String): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE accountId = :accountId AND trashed = 1
        ORDER BY updatedAt DESC
        """
    )
    fun observeTrash(accountId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE accountId = :accountId AND dirty = 1")
    suspend fun dirtyNotes(accountId: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE accountId = :accountId")
    suspend fun allNotes(accountId: String): List<NoteEntity>

    @Query(
        """
        SELECT * FROM notes
        WHERE accountId = :accountId AND trashed = 0
          AND reminderAt IS NOT NULL AND reminderAt > :now
        """
    )
    suspend fun upcomingReminders(accountId: String, now: Long): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes WHERE accountId = :accountId AND trashed = 0")
    fun observeCount(accountId: String): Flow<Int>

    /**
     * 数据库侧搜索（多个关键词为 AND 关系，均对 searchBlob 做包含匹配）。
     *
     * 相比「全量读进内存再 filter」，只把命中行带回来；
     * 配合 searchBlob 上的内容和 accountId 索引，笔记量上去后差距会很明显。
     */
    @Query(
        """
        SELECT * FROM notes
        WHERE accountId = :accountId AND trashed = 0 AND archived = 0
          AND (:q1 = '' OR searchBlob LIKE '%' || :q1 || '%')
          AND (:q2 = '' OR searchBlob LIKE '%' || :q2 || '%')
          AND (:q3 = '' OR searchBlob LIKE '%' || :q3 || '%')
        ORDER BY pinned DESC, updatedAt DESC
        """
    )
    suspend fun search(accountId: String, q1: String, q2: String, q3: String): List<NoteEntity>

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Upsert
    suspend fun upsertAll(notes: List<NoteEntity>)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM notes WHERE accountId = :accountId")
    suspend fun deleteAccount(accountId: String)

    @Query("UPDATE notes SET accountId = :to, dirty = 1 WHERE accountId = :from")
    suspend fun moveAccount(from: String, to: String)

    /** 游客笔记并入新账号（同时把 ownerId 改为新用户，避免云端拒绝）。 */
    @Query("UPDATE notes SET accountId = :to, ownerId = :to, dirty = 1 WHERE accountId = :from")
    suspend fun adoptNotes(from: String, to: String)

    @Query("UPDATE notes SET dirty = 0, serverSeq = :seq WHERE id = :id")
    suspend fun markSynced(id: String, seq: Long)

    @Query("SELECT * FROM notes WHERE accountId = :accountId AND type = :type AND trashed = 0")
    suspend fun notesOfType(accountId: String, type: String): List<NoteEntity>
}

@Dao
interface CommentDao {

    @Query("SELECT * FROM comments WHERE noteId = :noteId AND deleted = 0 ORDER BY createdAt ASC")
    fun observeForNote(noteId: String): Flow<List<CommentEntity>>

    @Query("SELECT * FROM comments WHERE noteId = :noteId AND deleted = 0 ORDER BY createdAt ASC")
    suspend fun forNote(noteId: String): List<CommentEntity>

    @Query("SELECT * FROM comments WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): CommentEntity?

    @Query("SELECT * FROM comments WHERE dirty = 1")
    suspend fun dirtyComments(): List<CommentEntity>

    @Upsert
    suspend fun upsert(comment: CommentEntity)

    @Upsert
    suspend fun upsertAll(comments: List<CommentEntity>)

    @Query("DELETE FROM comments WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: String)
}

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findByRemoteId(remoteId: String): UserEntity?

    @Query("SELECT * FROM users ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<UserEntity>>

    @Upsert
    suspend fun upsert(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE users SET lastSyncSeq = :seq, lastSyncAt = :at WHERE id = :id")
    suspend fun updateSyncState(id: String, seq: Long, at: Long)
}
