package com.futsegunda.wear.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.futsegunda.wear.data.TokenStore
import com.futsegunda.wear.network.ApiClient
import com.futsegunda.wear.network.JsonCodec
import com.futsegunda.wear.network.LoginRequest
import com.futsegunda.wear.network.LoginResponse
import com.futsegunda.wear.network.ServerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
)

class LoginViewModel(app: Application) : AndroidViewModel(app) {
    private val tokenStore = TokenStore(app)

    private val _state = MutableStateFlow(LoginUiState(loggedIn = tokenStore.isLoggedIn()))
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(error = "Preencha usuário e senha")
            return
        }
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val resp = ApiClient.service.login(
                    apiKey = ServerConfig.API_KEY,
                    body = JsonCodec.body(LoginRequest(username.trim(), password)),
                )
                val body = JsonCodec.decode<LoginResponse>(resp.body())
                if (resp.isSuccessful && body?.token != null) {
                    tokenStore.token = body.token
                    _state.value = LoginUiState(loggedIn = true)
                } else {
                    _state.value = _state.value.copy(loading = false, error = "Usuário ou senha incorretos")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = "Sem conexão")
            }
        }
    }
}
