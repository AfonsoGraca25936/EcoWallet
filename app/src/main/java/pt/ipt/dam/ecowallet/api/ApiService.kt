package pt.ipt.dam.ecowallet.api

import pt.ipt.dam.ecowallet.model.Despesa
import pt.ipt.dam.ecowallet.model.LoginRequest
import pt.ipt.dam.ecowallet.model.LoginResponse
import pt.ipt.dam.ecowallet.model.RegisterRequest
import pt.ipt.dam.ecowallet.model.SaldoRequest
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("auth/login")
    fun login(@Body request: LoginRequest): Call<LoginResponse>

    @POST("auth/register")
    fun register(@Body request: RegisterRequest): Call<LoginResponse>

    @GET("despesas")
    fun getDespesas(@Query("userId") userId: String): Call<List<Despesa>>

    @POST("despesas")
    fun addDespesa(@Body despesa: Despesa): Call<Despesa>

    @PUT("despesas/{id}")
    fun updateDespesa(@Path("id") id: String, @Body despesa: Despesa): Call<Despesa>

    @DELETE("despesas/{id}")
    fun deleteDespesa(@Path("id") id: String): Call<Void>

    @PUT("auth/users/{id}")
    fun updateSaldo(@Path("id") id: String, @Body body: Map<String, Double>): Call<Void>

    @PUT("users/{id}/saldo")
    fun updateSaldo(@Path("id") id: String, @Body body: SaldoRequest): Call<Void>
}