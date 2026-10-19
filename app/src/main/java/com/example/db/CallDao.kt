package com.example.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CallDao {

    @Query("SELECT * FROM call_records ORDER BY timestamp DESC")
    fun getAllCalls(): Flow<List<CallRecordEntity>>

    @Query("SELECT * FROM call_records WHERE id = :id LIMIT 1")
    fun getCallById(id: String): Flow<CallRecordEntity?>

    @Query("SELECT * FROM call_records WHERE id = :id LIMIT 1")
    suspend fun getCallByIdSync(id: String): CallRecordEntity?

    @Query("""
        SELECT * FROM call_records 
        WHERE contactName LIKE '%' || :query || '%' 
           OR phoneNumber LIKE '%' || :query || '%' 
           OR transcriptJson LIKE '%' || :query || '%' 
           OR executiveSummaryJson LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    fun searchCalls(query: String): Flow<List<CallRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalls(calls: List<CallRecordEntity>)

    @Update
    suspend fun updateCall(call: CallRecordEntity)

    @Query("DELETE FROM call_records WHERE id = :id")
    suspend fun deleteCall(id: String)

    @Query("DELETE FROM call_records")
    suspend fun deleteAllCalls()

    @Query("UPDATE call_records SET transcriptJson = :transcriptJson WHERE id = :id")
    suspend fun updateTranscript(id: String, transcriptJson: String)

    @Query("UPDATE call_records SET syncStatus = :syncStatus WHERE id = :id")
    suspend fun updateSyncStatus(id: String, syncStatus: String)

    @Query("UPDATE call_records SET actionItemsJson = :actionItemsJson WHERE id = :id")
    suspend fun updateActionItems(id: String, actionItemsJson: String)

    @Query("UPDATE call_records SET contactName = :contactName WHERE phoneNumber = :phoneNumber")
    suspend fun updateContactNameByPhone(phoneNumber: String, contactName: String)
}
