package pt.ipt.dam.ecowallet

import android.graphics.Color
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
    private val onDespesaClick: (Despesa) -> Unit,
    private val onDeleteClick: (Despesa) -> Unit
) : RecyclerView.Adapter<DespesaAdapter.DespesaViewHolder>() {

    class DespesaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitulo: TextView = itemView.findViewById(R.id.tvTitulo)
        val tvValor: TextView = itemView.findViewById(R.id.tvValor)
        val tvCategoria: TextView = itemView.findViewById(R.id.tvCategoria)
        val tvData: TextView = itemView.findViewById(R.id.tvData)
        val ivDespesaThumb: ImageView = itemView.findViewById(R.id.ivDespesaThumb)
        val btnDelete: ImageView = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DespesaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_despesa, parent, false)
        return DespesaViewHolder(view)
    }

    override fun onBindViewHolder(holder: DespesaViewHolder, position: Int) {
        val despesa = lista[position]
        holder.tvTitulo.text = despesa.titulo
        holder.tvCategoria.text = despesa.categoria
        holder.tvData.text = despesa.data

        // Lógica de Cores e Sinais
        if (despesa.valor >= 0) {
            holder.tvValor.text = String.format("+ %.2f€", despesa.valor)
            holder.tvValor.setTextColor(Color.parseColor("#4CAF50")) // Verde
        } else {
            holder.tvValor.text = String.format("%.2f€", despesa.valor)
            holder.tvValor.setTextColor(Color.parseColor("#F44336")) // Vermelho
        }

        // Carregamento de Imagem
        if (!despesa.fotoCaminho.isNullOrEmpty()) {
            val file = File(despesa.fotoCaminho)
            if (file.exists()) {
                holder.ivDespesaThumb.load(file)
            } else {
                holder.ivDespesaThumb.load(R.mipmap.ic_launcher)
            }
        } else {
            holder.ivDespesaThumb.load(R.mipmap.ic_launcher)
        }

        holder.itemView.setOnClickListener { onDespesaClick(despesa) }
        holder.btnDelete.setOnClickListener { onDeleteClick(despesa) }
    }

    override fun getItemCount() = lista.size

    fun updateList(novaLista: List<Despesa>) {
        lista = novaLista
        notifyDataSetChanged()
    }
}