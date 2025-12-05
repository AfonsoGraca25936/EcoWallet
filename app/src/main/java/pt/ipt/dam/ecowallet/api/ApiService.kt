package pt.ipt.dam.ecowallet.api

import pt.ipt.dam.ecowallet.model.Despesa
import pt.ipt.dam.ecowallet.model.LoginRequest
import pt.ipt.dam.ecowallet.model.LoginResponse
import pt.ipt.dam.ecowallet.model.RegisterRequest
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ApiService {

    // --- AUTENTICAÇÃO ---
    @POST("auth/login")
    fun login(@Body request: LoginRequest): Call<LoginResponse>

    @POST("auth/register")
    fun register(@Body request: RegisterRequest): Call<LoginResponse>

    // --- DESPESAS ---

    // Obter todas as despesas do utilizador
    @GET("despesas")
    fun getDespesas(): Call<List<Despesa>>

    // Criar uma nova despesa
    @POST("despesas")
    fun addDespesa(@Body despesa: Despesa): Call<Despesa>

    // Apagar despesa (precisa do ID)
    @DELETE("despesas/{id}")
    fun deleteDespesa(@Path("id") id: String): Call<Void>
}