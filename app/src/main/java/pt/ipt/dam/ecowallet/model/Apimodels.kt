package pt.ipt.dam.ecowallet.model

import com.google.gson.annotations.SerializedName

// --- PEDIDOS (REQUESTS) ---

// O que envias para fazer Login
data class LoginRequest(
    @SerializedName("username") val user: String,
    @SerializedName("password") val pass: String
)

// O que envias para Registar
data class RegisterRequest(
    @SerializedName("username") val user: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val pass: String
)

// --- RESPOSTAS (RESPONSES) ---

// A resposta da API ao Login
data class LoginResponse(
    @SerializedName("error") val error: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("user") val user: User?
)

