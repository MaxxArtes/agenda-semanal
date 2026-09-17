package br.maxymus.agenda

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

/**
 * Entrada: login Google com o escopo do Google Agenda e, logado, a tela da agenda.
 * O cadastro OAuth (tipo Android, pacote br.maxymus.agenda + SHA-1 da chave) fica no Google Cloud do dono;
 * o app não carrega nenhum client id.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF6C8CFF), secondary = Color(0xFF4CC486), background = Color(0xFF0F1420), surface = Color(0xFF171D2C))) {
                App()
            }
        }
    }
}

@Composable
private fun App() {
    val contexto = LocalContext.current
    val opcoes = remember { GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().requestScopes(Scope(GoogleAgenda.ESCOPO)).build() }
    val cliente = remember { GoogleSignIn.getClient(contexto, opcoes) }
    var conta by remember { mutableStateOf(GoogleSignIn.getLastSignedInAccount(contexto)?.takeIf { GoogleSignIn.hasPermissions(it, Scope(GoogleAgenda.ESCOPO)) }?.email) }
    var erro by remember { mutableStateOf<String?>(null) }
    val entrar = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        runCatching { GoogleSignIn.getSignedInAccountFromIntent(r.data).getResult(ApiException::class.java) }
            .onSuccess { c -> if (GoogleSignIn.hasPermissions(c, Scope(GoogleAgenda.ESCOPO))) { conta = c.email; erro = null } else erro = "Você não liberou o acesso ao Google Agenda." }
            .onFailure { e -> erro = if (e is ApiException) "Login falhou (código ${e.statusCode}). Confira se o app está cadastrado no Google Cloud com esta chave." else e.message }
    }

    val email = conta
    if (email == null) {
        Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Agenda Semanal", style = MaterialTheme.typography.headlineMedium)
            Text("\nSua rotina fica no Google Agenda, num calendário próprio chamado \"Agenda Semanal\". Entre com a conta Google para começar.", textAlign = TextAlign.Center)
            Button(onClick = { entrar.launch(cliente.signInIntent) }, modifier = Modifier.padding(top = 24.dp)) { Text("Entrar com Google") }
            erro?.let { Text("\n$it", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        }
        return
    }
    AgendaScreen(conta = email, sair = { cliente.signOut(); GoogleAgenda(contexto, email).esqueceCalendario(); conta = null }, autorizar = { intent: Intent -> entrar.launch(intent) })
}
