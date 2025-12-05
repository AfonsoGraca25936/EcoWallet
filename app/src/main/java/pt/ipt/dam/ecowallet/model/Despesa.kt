package pt.ipt.dam.ecowallet.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Entity(tableName = "despesas")
data class Despesa(
    // ID LOCAL (Para o Room no telemóvel) - Continua a ser Int e automático
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // ID REMOTO (Para o MongoDB) - Vem do JSON como "_id"
    @SerializedName("_id")
    val mongoId: String? = null,

    val titulo: String,
    val valor: Double,
    val categoria: String,
    val data: String,
    val fotoCaminho: String?,

    // Este campo não vai para a API, serve só para a app saber o que sincronizar
    val isSynced: Boolean = false
) : Serializable