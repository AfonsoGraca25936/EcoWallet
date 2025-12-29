package pt.ipt.dam.ecowallet.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pt.ipt.dam.ecowallet.model.Despesa

@Dao
interface DespesaDao {
    // O ERRO ESTAVA AQUI: Mudámos de "despesas" para "despesa"
    @Query("SELECT * FROM despesa")
    suspend fun getAll(): List<Despesa>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(despesa: Despesa)

    @Delete
    suspend fun delete(despesa: Despesa)

    // AQUI TAMBÉM: "despesa" no singular
    @Query("DELETE FROM despesa")
    suspend fun deleteAll()
}