package pt.ipt.dam.ecowallet.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "despesa")
data class Despesa(
    @PrimaryKey(autoGenerate = false)
    @SerializedName("_id")
    var id: String = "",          // String

    val userId: String,           // String (Para ligar ao User)

    val titulo: String,
    val valor: Double,
    val categoria: String,
    val data: String,
    val fotoCaminho: String?,

    var isSynced: Boolean = false // Para saberes se já foi enviada para o servidor
)