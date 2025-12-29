package pt.ipt.dam.ecowallet.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "despesa")
data class Despesa(
    @PrimaryKey(autoGenerate = false) // MongoDB gera o ID, ou nós geramos um UUID
    @SerializedName("_id") // O Mongo manda "_id", mas nós usamos "id" no código
    var id: String = "",

    val titulo: String,
    val valor: Double,
    val categoria: String,
    val data: String,
    val fotoCaminho: String?,
    var isSynced: Boolean = false
)