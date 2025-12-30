package pt.ipt.dam.ecowallet.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "utilizador")
data class User(
    @PrimaryKey(autoGenerate = false)
    @SerializedName("_id")
    val id: String,

    val username: String,
    val email: String,
    val saldo: Double = 0.0
)
