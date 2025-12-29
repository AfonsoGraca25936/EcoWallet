package pt.ipt.dam.ecowallet.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "utilizador")
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @SerializedName("username")
    val username: String,

    @SerializedName("email")
    val email: String,

    val saldo: Double = 0.0
)
