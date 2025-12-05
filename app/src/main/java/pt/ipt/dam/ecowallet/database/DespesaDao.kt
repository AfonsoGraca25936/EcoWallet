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

    // Lista todas as despesas (as mais recentes primeiro)
    @Query("SELECT * FROM despesas ORDER BY id DESC")
    suspend fun getAll(): List<Despesa>

    // Insere uma nova despesa
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(despesa: Despesa)

    // Atualiza uma despesa existente
    @Update
    suspend fun update(despesa: Despesa)

    // Apaga uma despesa
    @Delete
    suspend fun delete(despesa: Despesa)

    // Apaga tudo (útil quando fazes logout)
    @Query("DELETE FROM despesas")
    suspend fun deleteAll()
}