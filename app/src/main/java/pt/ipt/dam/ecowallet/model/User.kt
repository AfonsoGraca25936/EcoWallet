package pt.ipt.dam.ecowallet.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Entity(tableName = "utilizador_logado") // Tabela para guardar a sessão localmente
data class User(
    @PrimaryKey
    @SerializedName("id") // O nome que vem do JSON da API
    val id: Int,

    @SerializedName("username")
    val username: String,

    @SerializedName("email")
    val email: String,

    @SerializedName("token") // O token de autenticação (muito importante para a API)
    val token: String? = null
) : Serializable
