package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,
    val isFromGlove: Boolean, // true if glove sent it, false if user spoke it
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long
    
    @androidx.room.Update
    suspend fun updateMessage(message: MessageEntity)
    
    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteMessages(ids: List<Int>)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
}

@Database(entities = [MessageEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}
