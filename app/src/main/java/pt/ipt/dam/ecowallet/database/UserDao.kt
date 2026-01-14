package pt.ipt.dam.ecowallet.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pt.ipt.dam.ecowallet.model.User

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User)

    @Query("DELETE FROM utilizador")
    suspend fun logout() // Agora chamamos logout() na MainActivity

    @Query("SELECT * FROM utilizador LIMIT 1")
    suspend fun getUtilizador(): User?

    // MUDANÇA: userId é String
    @Query("UPDATE utilizador SET saldo = :novoSaldo WHERE id = :userId")
    suspend fun updateSaldo(userId: String, novoSaldo: Double)

    @Query("DELETE FROM utilizador")
    suspend fun deleteAll()
}