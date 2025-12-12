package pt.ipt.dam.ecowallet

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import pt.ipt.dam.ecowallet.model.Despesa
import java.io.File

class DespesaAdapter(
    private var lista: List<Despesa>,
    private val onDespesaClick: (Despesa) -> Unit
) : RecyclerView.Adapter<DespesaAdapter.DespesaViewHolder>() {

    class DespesaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titulo: TextView = itemView.findViewById(R.id.tvTitulo)
        val valor: TextView = itemView.findViewById(R.id.tvValor)
        val categoria: TextView = itemView.findViewById(R.id.tvCategoria)
        val data: TextView = itemView.findViewById(R.id.tvData)
        val imagem: ImageView = itemView.findViewById(R.id.ivDespesaThumb)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DespesaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_despesa, parent, false)
        return DespesaViewHolder(view)
    }

    override fun onBindViewHolder(holder: DespesaViewHolder, position: Int) {
        val despesa = lista[position]
        holder.titulo.text = despesa.titulo
        holder.valor.text = String.format("%.2f€", despesa.valor)
        holder.categoria.text = despesa.categoria
        holder.data.text = despesa.data

        // Carregar imagem com a biblioteca Coil
        if (!despesa.fotoCaminho.isNullOrEmpty()) {
            val imgFile = File(despesa.fotoCaminho)
            if (imgFile.exists()) {
                holder.imagem.load(imgFile)
            } else {
                // Tenta carregar da URL se for remoto (opcional, se a API devolver URL)
                holder.imagem.load(R.mipmap.ic_launcher)
            }
        } else {
            holder.imagem.load(R.mipmap.ic_launcher)
        }

        holder.itemView.setOnClickListener { onDespesaClick(despesa) }
    }

    override fun getItemCount() = lista.size

    fun updateList(novaLista: List<Despesa>) {
        lista = novaLista
        notifyDataSetChanged()
    }
}