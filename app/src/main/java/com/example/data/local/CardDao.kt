package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Query("SELECT * FROM cards WHERE studentId = :studentId AND isDeleted = 0 ORDER BY issueDate DESC")
    fun getCardsForStudentFlow(studentId: String): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE studentId = :studentId AND isDeleted = 0 ORDER BY issueDate DESC")
    suspend fun getCardsForStudent(studentId: String): List<CardEntity>

    @Query("SELECT * FROM cards WHERE (studentId = :studentIdOrNumber OR studentNumber = :studentIdOrNumber) AND status = 'ACTIVE' AND isDeleted = 0 LIMIT 1")
    suspend fun getActiveCardForStudent(studentIdOrNumber: String): CardEntity?

    @Query("SELECT * FROM cards WHERE qrPayload = :qrPayload AND isDeleted = 0 ORDER BY issueDate DESC LIMIT 1")
    suspend fun getCardByQrPayload(qrPayload: String): CardEntity?

    @Query("SELECT * FROM cards WHERE id = :cardId AND isDeleted = 0 LIMIT 1")
    suspend fun getCardById(cardId: String): CardEntity?

    @Query("SELECT * FROM cards WHERE cardIdentifier = :cardIdentifier AND isDeleted = 0 LIMIT 1")
    suspend fun getCardByIdentifier(cardIdentifier: String): CardEntity?

    @Query("SELECT * FROM cards WHERE isDeleted = 0")
    suspend fun getAllCardsSnapshot(): List<CardEntity>

    @Query("SELECT * FROM cards WHERE isDeleted = 0")
    fun getAllCardsFlow(): Flow<List<CardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCard(card: CardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCards(cards: List<CardEntity>)

    @Query("UPDATE cards SET status = :newStatus, deactivationDate = :deactivationDate, replacedByCardId = :replacedByCardId, reason = :reason, updatedAt = :updatedAt WHERE id = :cardId")
    suspend fun updateCardStatus(
        cardId: String,
        newStatus: String,
        deactivationDate: Long?,
        replacedByCardId: String?,
        reason: String?,
        updatedAt: Long
    )

    @Query("UPDATE cards SET status = 'REPLACED', deactivationDate = :deactivationDate, replacedByCardId = :newCardId, reason = :reason, updatedAt = :updatedAt WHERE studentId = :studentId AND status = 'ACTIVE'")
    suspend fun markActiveCardsReplaced(
        studentId: String,
        newCardId: String,
        deactivationDate: Long,
        reason: String,
        updatedAt: Long
    )

    @Query("DELETE FROM cards")
    suspend fun clearAllCards()

    @Query("SELECT COUNT(*) FROM cards WHERE isDeleted = 0")
    suspend fun getTotalCardsCount(): Int
}
