package pt.ipt.dam.ecowallet

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.ipt.dam.ecowallet.api.RetrofitClient
import pt.ipt.dam.ecowallet.database.AppDatabase
import pt.ipt.dam.ecowallet.model.Despesa
import pt.ipt.dam.ecowallet.model.SaldoRequest
import pt.ipt.dam.ecowallet.model.User
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.math.abs

/**
 * Atividade Principal: Gere a visualização do saldo, gráficos de despesas/receitas
 * e a lista de transações recentes.
 */
class MainActivity : AppCompatActivity() {

    // Instâncias da base de dados e componentes da interface
    private lateinit var database: AppDatabase
    private lateinit var adapter: DespesaAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvSaldo: TextView
    private lateinit var pieChart: PieChart

    // Dados do utilizador e lista de transações em memória
    private var currentUser: User? = null
    private var listaCompleta: List<Despesa> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Força a aplicação a usar o modo claro (desativa o modo escuro automático)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)

        // Configura a cor da barra de estado (topo) para verde e ícones brancos
        window.statusBarColor = Color.parseColor("#2E7D32")
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        setContentView(R.layout.activity_main)

        // Inicializa o acesso à base de dados local (Room)
        database = AppDatabase.getDatabase(this)

        // Liga as variáveis aos elementos do layout XML
        tvEmpty = findViewById(R.id.tvEmpty)
        recyclerView = findViewById(R.id.recyclerView)
        tvSaldo = findViewById(R.id.tvSaldo)
        pieChart = findViewById(R.id.pieChart)

        // Configuração estética inicial do gráfico circular
        pieChart.setUsePercentValues(true)
        pieChart.description.isEnabled = false
        pieChart.legend.isEnabled = false
        pieChart.setEntryLabelColor(Color.BLACK)

        // Referência aos botões e ícones clicáveis
        val btnLogout = findViewById<ImageView>(R.id.btnLogout)
        val btnAbout = findViewById<ImageView>(R.id.btnAbout)
        val btnCalendar = findViewById<ImageView>(R.id.btnCalendar)
        val btnShowDespesa = findViewById<Button>(R.id.btnShowDespesa)
        val btnShowReceita = findViewById<Button>(R.id.btnShowReceita)
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAdd)
        val fabReceita = findViewById<FloatingActionButton>(R.id.fabReceita)
        val btnShowResumo = findViewById<Button>(R.id.btnShowResumo)

        // Configuração das ações ao clicar nos botões de resumo e calendário
        btnShowResumo.setOnClickListener { mostrarGraficoResumo() }
        btnCalendar.setOnClickListener { startActivity(Intent(this, CalendarActivity::class.java)) }

        // Ajusta os paddings para que o conteúdo não fique escondido sob a barra de navegação
        val mainView = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        // Configura o Adaptador da lista de transações e define as ações de clique/eliminação
        adapter = DespesaAdapter(
            lista = emptyList(),
            onDespesaClick = { despesa ->
                // Abre o ecrã de edição passando os dados da transação selecionada
                val intent = Intent(this, EditTransactionActivity::class.java).apply {
                    putExtra("ID", despesa.id)
                    putExtra("USER_ID", despesa.userId)
                    putExtra("TITULO", despesa.titulo)
                    putExtra("VALOR", despesa.valor)
                    putExtra("CATEGORIA", despesa.categoria)
                    putExtra("DATA", despesa.data)
                    putExtra("FOTO", despesa.fotoCaminho)
                }
                startActivity(intent)
            },
            onDeleteClick = { despesa -> mostrarDialogoApagar(despesa) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Ações de navegação para adicionar despesa, receita, logout e ecrã "Sobre"
        fabAdd.setOnClickListener { startActivity(Intent(this, AddDespesaActivity::class.java)) }
        fabReceita.setOnClickListener { startActivity(Intent(this, AddReceitaActivity::class.java)) }
        btnLogout.setOnClickListener { realizarLogout() }
        btnAbout.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }

        // Filtros do gráfico: mostra apenas despesas ou apenas receitas
        btnShowDespesa.setOnClickListener { atualizarGrafico(listaCompleta.filter { it.valor < 0 }, "Gastos ", true) }
        btnShowReceita.setOnClickListener { atualizarGrafico(listaCompleta.filter { it.valor > 0 }, "Entradas ", false) }

        // Verifica se o utilizador tem sessão iniciada ao abrir a app
        checkSessionAndLoad()
    }

    override fun onResume() {
        super.onResume()
        // Atualiza os dados sempre que o utilizador volta a este ecrã
        recuperarDadosUtilizador()
    }

    /**
     * Procura os dados do utilizador na base de dados local e inicia a sincronização
     */
    private fun recuperarDadosUtilizador() {
        lifecycleScope.launch {
            val user = database.utilizadorDao().getUtilizador()
            user?.let {
                currentUser = it
                updateSaldoUI(it.saldo)
            }
            loadDespesasLocais() // Carrega a lista do telemóvel
            syncDespesasAPI()   // Tenta buscar dados novos do servidor
        }
    }

    /**
     * Atualiza o PieChart com categorias (Gastos ou Entradas)
     */
    private fun atualizarGrafico(lista: List<Despesa>, titulo: String, isDespesa: Boolean) {
        val totalSoma = lista.sumOf { abs(it.valor) }
        // Agrupa valores por categoria
        val categoriasMap = lista.groupBy { it.categoria }.mapValues { it.value.sumOf { abs(it.valor) } }

        if (categoriasMap.isEmpty()) {
            pieChart.clear(); pieChart.centerText = "Sem dados"; return
        }

        // Cria as fatias do gráfico
        val entries = categoriasMap.map { PieEntry(it.value.toFloat(), it.key) }
        val dataSet = PieDataSet(entries, "").apply {
            if (isDespesa) {
                // Tons de vermelho para gastos
                colors = listOf(Color.parseColor("#FF5252"), Color.parseColor("#D32F2F"), Color.parseColor("#B71C1C"), Color.parseColor("#8B0000"))
            } else {
                colors = ColorTemplate.MATERIAL_COLORS.toList()
            }
            valueTextSize = 12f; valueTextColor = Color.BLACK
        }

        pieChart.data = PieData(dataSet).apply { setValueFormatter(PercentFormatter(pieChart)) }
        pieChart.centerText = "$titulo\n${String.format("%.2f€", totalSoma)}"
        pieChart.animateY(800); pieChart.invalidate()
    }

    /**
     * Mostra o gráfico de comparação geral: Receitas vs Despesas
     */
    private fun mostrarGraficoResumo() {
        val totalReceitas = listaCompleta.filter { it.valor > 0 }.sumOf { it.valor }
        val totalDespesas = listaCompleta.filter { it.valor < 0 }.sumOf { abs(it.valor) }

        if (totalReceitas == 0.0 && totalDespesas == 0.0) {
            pieChart.clear(); pieChart.centerText = "Sem dados"; return
        }

        val entries = mutableListOf<PieEntry>()
        if (totalReceitas > 0) entries.add(PieEntry(totalReceitas.toFloat(), "Receitas"))
        if (totalDespesas > 0) entries.add(PieEntry(totalDespesas.toFloat(), "Despesas"))

        val dataSet = PieDataSet(entries, "").apply {
            // Verde para receitas, Vermelho para despesas
            colors = listOf(Color.parseColor("#4CAF50"), Color.parseColor("#F44336"))
            valueTextSize = 14f
            valueTextColor = Color.WHITE
        }

        pieChart.data = PieData(dataSet).apply { setValueFormatter(PercentFormatter(pieChart)) }
        pieChart.centerText = "Balanço Geral\n${String.format("%.2f€", totalReceitas - totalDespesas)}"
        pieChart.animateY(800)
        pieChart.invalidate()
    }

    /**
     * Carrega as transações da BD local e atualiza a interface
     */
    private fun loadDespesasLocais() {
        lifecycleScope.launch {
            listaCompleta = database.despesaDao().getAll()
            if (listaCompleta.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE; recyclerView.visibility = View.GONE; pieChart.visibility = View.GONE
            } else {
                tvEmpty.visibility = View.GONE; recyclerView.visibility = View.VISIBLE; pieChart.visibility = View.VISIBLE
                adapter.updateList(listaCompleta)
                atualizarGrafico(listaCompleta.filter { it.valor < 0 }, "Gastos (%)", true)
            }
            // Garante que o saldo UI bate certo com a base de dados
            val user = database.utilizadorDao().getUtilizador()
            user?.let { updateSaldoUI(it.saldo) }
        }
    }

    /**
     * Limpa todos os dados locais e volta para o ecrã de Login
     */
    private fun realizarLogout() {
        lifecycleScope.launch {
            database.utilizadorDao().deleteAll(); database.despesaDao().deleteAll()
            startActivity(Intent(this@MainActivity, LoginActivity::class.java)); finish()
        }
    }

    /**
     * Sincroniza as despesas do servidor com a base de dados local
     */
    private fun syncDespesasAPI() {
        val userId = currentUser?.id ?: return
        RetrofitClient.instance.getDespesas(userId).enqueue(object : Callback<List<Despesa>> {
            override fun onResponse(call: Call<List<Despesa>>, response: Response<List<Despesa>>) {
                if (response.isSuccessful) {
                    response.body()?.let { despesasRemotas ->
                        lifecycleScope.launch {
                            // Limpa local e repovoa com dados do servidor (Fonte da Verdade)
                            database.despesaDao().deleteAll()
                            despesasRemotas.forEach { it.isSynced = true; database.despesaDao().insert(it) }
                            loadDespesasLocais()
                        }
                    }
                }
            }
            override fun onFailure(call: Call<List<Despesa>>, t: Throwable) {}
        })
    }

    /**
     * Verifica se existe um utilizador no telemóvel; se não, redireciona para Login
     */
    private fun checkSessionAndLoad() {
        lifecycleScope.launch {
            val user = database.utilizadorDao().getUtilizador()
            if (user == null) {
                startActivity(Intent(this@MainActivity, LoginActivity::class.java)); finish()
            } else {
                currentUser = user; updateSaldoUI(user.saldo); loadDespesasLocais(); syncDespesasAPI()
            }
        }
    }

    // Atualiza o texto do saldo no ecrã com formatação de moeda
    private fun updateSaldoUI(valor: Double) { tvSaldo.text = String.format("%.2f€", valor) }

    // Mostra um aviso antes de apagar uma transação
    private fun mostrarDialogoApagar(despesa: Despesa) {
        AlertDialog.Builder(this).setTitle("Apagar").setMessage("Deseja apagar '${despesa.titulo}'?")
            .setPositiveButton("Sim") { _, _ -> apagarDespesa(despesa) }
            .setNegativeButton("Não", null).show()
    }

    /**
     * Elimina a despesa localmente e no servidor, e atualiza o saldo do utilizador
     */
    private fun apagarDespesa(despesa: Despesa) {
        lifecycleScope.launch(Dispatchers.IO) {
            val user = database.utilizadorDao().getUtilizador()
            user?.let { u ->
                // Devolve o dinheiro ao saldo (Subtrair um valor negativo de despesa = somar)
                val novoSaldo = u.saldo - despesa.valor

                // 1. Atualiza BD local
                database.utilizadorDao().updateSaldo(u.id, novoSaldo)

                // 2. Tenta atualizar servidor (API)
                try {
                    RetrofitClient.instance.updateSaldo(u.id, SaldoRequest(novoSaldo)).execute()
                } catch (e: Exception) { e.printStackTrace() }

                // 3. Atualiza UI instantaneamente
                withContext(Dispatchers.Main) {
                    currentUser = u.copy(saldo = novoSaldo)
                    updateSaldoUI(novoSaldo)
                }
            }

            // 4. Apaga o registo da transação
            database.despesaDao().delete(despesa)
            if (despesa.id.isNotEmpty()) {
                try {
                    RetrofitClient.instance.deleteDespesa(despesa.id).execute()
                } catch (e: Exception) { e.printStackTrace() }
            }

            // Recarrega a lista no ecrã principal
            withContext(Dispatchers.Main) { loadDespesasLocais() }
        }
    }
}