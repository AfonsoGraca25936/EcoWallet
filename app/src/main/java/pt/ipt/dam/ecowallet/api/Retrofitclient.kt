package pt.ipt.dam.ecowallet.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // --- MUDANÇA AQUI ---
    // Substitui este URL pelo link que o Render te deu no Dashboard.
    // Exemplo: "https://ecowallet-api-xyz.onrender.com/"
    // IMPORTANTE: Tem de ter a barra "/" no final!
    private const val BASE_URL = "https://ecowallet-api.onrender.com/"

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}