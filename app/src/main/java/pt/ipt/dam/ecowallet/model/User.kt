package pt.ipt.dam.ecowallet.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "utilizador") // Nome da tabela que usaste no UserDao
data class User(
    @PrimaryKey(autoGenerate = false) // O ID vem do servidor, não geramos localmente
    @SerializedName("_id")        // Mapeia o "_id" do Mongo para a variável "id"
    val id: String,               // <--- STRING (Compatível com o teu JavaScript)

    @SerializedName("username")   // Confirma com o teu JS (linha 26: username)
    val username: String,

    @SerializedName("email")
    val email: String,

    @SerializedName("saldo")
    val saldo: Double = 0.0
)