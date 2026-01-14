package pt.ipt.dam.ecowallet.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import pt.ipt.dam.ecowallet.model.Despesa

@Dao
interface DespesaDao {
    // MUDANÇA: userId é String
    @Query("SELECT * FROM despesa WHERE userId = :userId")
    suspend fun getUserDespesas(userId: String): List<Despesa>

    // Adicionado para a MainActivity
    @Query("SELECT * FROM despesa ORDER BY data DESC")
    suspend fun getAll(): List<Despesa>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(despesa: Despesa)

    @Update
    suspend fun update(despesa: Despesa)

    @Delete
    suspend fun delete(despesa: Despesa)

    // Adicionado para o Logout
    @Query("DELETE FROM despesa")
    suspend fun deleteAll()
}