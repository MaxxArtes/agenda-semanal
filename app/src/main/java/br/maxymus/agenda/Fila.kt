package br.maxymus.agenda

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fila de escritas no Google, em segundo plano (pedido do dono, 17/09: a tela muda na hora; o Google
 * recebe depois, em ordem, sem travar o uso). Vive no processo, não na tela: sair da tela não cancela.
 * Uma falha para a fila, guarda o motivo e espera "Tentar novamente"; as escritas seguintes ficam
 * esperando na ordem, para não gravar a segunda antes da primeira.
 */
object Fila {
    class Item(val descricao: String, val acao: suspend () -> Unit)
    class Estado(val pendentes: Int, val enviando: Boolean, val erro: String?, val autorizar: android.content.Intent? = null)

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val trava = Mutex()
    private val itens = ArrayDeque<Item>()
    private val _estado = MutableStateFlow(Estado(0, false, null))
    val estado: StateFlow<Estado> = _estado
    @Volatile private var rodando = false
    @Volatile private var travada = false   // após erro, espera o usuário

    fun enfileirar(descricao: String, acao: suspend () -> Unit) {
        synchronized(itens) { itens.addLast(Item(descricao, acao)) }
        publica(); bombear()
    }

    /** Depois de um erro: destrava e tenta de novo a partir do item que falhou. */
    fun tentarDeNovo() { travada = false; publica(); bombear() }

    /** Descarta o item que falhou (a tela já foi recarregada do Google) e segue com o resto. */
    fun descartarFalha() { synchronized(itens) { itens.removeFirstOrNull() }; travada = false; publica(); bombear() }

    private fun publica(erro: String? = null, autorizar: android.content.Intent? = null) {
        val n = synchronized(itens) { itens.size }
        _estado.value = Estado(n, rodando, erro ?: if (travada) _estado.value.erro else null, autorizar ?: if (travada) _estado.value.autorizar else null)
    }

    private fun bombear() {
        if (rodando || travada) return
        escopo.launch {
            trava.withLock {
                if (rodando || travada) return@withLock
                rodando = true; publica()
                while (true) {
                    val item = synchronized(itens) { itens.firstOrNull() } ?: break
                    try { item.acao(); synchronized(itens) { itens.removeFirstOrNull() }; publica() }
                    catch (e: com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) { travada = true; rodando = false; publica("Precisa autorizar o acesso ao Google Agenda para enviar: ${item.descricao}", e.intent); return@withLock }
                    catch (e: Exception) { travada = true; rodando = false; publica("Não foi possível enviar \"${item.descricao}\": ${GoogleAgenda.mensagem(e)}"); return@withLock }
                }
                rodando = false; publica()
            }
        }
    }
}
