package pt.ipt.dam.ecowallet.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pt.ipt.dam.ecowallet.model.User

@Dao
interface UserDao {

    // Insere o utilizador quando faz login
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User)

    // Vai buscar o utilizador logado (se existir)
    @Query("SELECT * FROM utilizador_logado LIMIT 1")
    suspend fun getUtilizador(): User?

    // Apaga o utilizador (Logout)
    @Query("DELETE FROM utilizador_logado")
    suspend fun logout()
}