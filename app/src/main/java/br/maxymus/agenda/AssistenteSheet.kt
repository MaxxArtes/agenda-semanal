package br.maxymus.agenda

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.speech.RecognizerIntent
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Etapas da folha do assistente (Astra, DESIGN_ASTRA_PLANOS.md). */
private enum class Etapa { CARREGANDO, PLANOS, CHAVE, PEDIDO, RECARGA, PIX, HISTORICO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistenteSheet(email: String, hoje: LocalDate, seg: LocalDate, blocos: List<Bloco>, aoFechar: () -> Unit, aoAplicar: (Assistente.Resposta) -> Unit) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    val folha = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var etapa by remember { mutableStateOf(Etapa.CARREGANDO) }
    var conta by remember { mutableStateOf<Assistente.Conta?>(null) }
    var erro by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    var chave by remember { mutableStateOf(Assistente.chavePropria(ctx)) }
    var pedido by remember { mutableStateOf("") }
    var pedidoRespondido by remember { mutableStateOf("") }   // a resposta só vale para o texto que a gerou
    var resposta by remember { mutableStateOf<Assistente.Resposta?>(null) }
    var bloqueio by remember { mutableStateOf<Pair<String, String>?>(null) }   // (mensagem, ação: "credito" | "planos")
    var recarga by remember { mutableStateOf<Assistente.Recarga?>(null) }
    var voltarPara by remember { mutableStateOf(Etapa.PEDIDO) }
    val usaChave = chave.isNotEmpty() && conta?.plano == "propria"

    fun carregaConta(depois: (Assistente.Conta) -> Unit = {}) {
        escopo.launch {
            runCatching { Assistente.conta(ctx, email) }
                .onSuccess { c -> conta = c; depois(c) }
                .onFailure { e -> erro = "Não consegui carregar seu plano: ${e.message}"; if (etapa == Etapa.CARREGANDO) etapa = Etapa.PLANOS }
        }
    }
    LaunchedEffect(Unit) {
        carregaConta { c ->
            etapa = when {
                c.plano == "propria" && chave.isNotEmpty() -> Etapa.PEDIDO
                c.plano == "propria" -> Etapa.CHAVE   // modo chave ativo em outro aparelho: pedir a chave de novo
                c.plano == "pago" || (c.plano == "gratuito" && c.consentiu) -> Etapa.PEDIDO
                else -> Etapa.PLANOS
            }
        }
    }
    val ouvir = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { pedido = it }   // voz só preenche; não envia
    }

    ModalBottomSheet(onDismissRequest = aoFechar, sheetState = folha, containerColor = Elevada, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).align(Alignment.CenterHorizontally).padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (etapa) {
                Etapa.CARREGANDO -> { Titulo("Assistente"); Linha { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Acento); Texto2("Carregando seu plano…", Modifier.padding(start = 8.dp)) }; erro?.let { Erro(it) } }

                Etapa.PLANOS -> PlanosEtapa(conta = conta, chave = chave, ocupado = ocupado, erro = erro,
                    aoGratuito = { escopo.launch { ocupado = true; erro = null
                        runCatching { Assistente.definirPlano(ctx, email, "gratuito", true) }.onSuccess { conta = it; etapa = Etapa.PEDIDO }.onFailure { erro = it.message }; ocupado = false } },
                    aoPago = { escopo.launch { ocupado = true; erro = null
                        runCatching { Assistente.definirPlano(ctx, email, "pago", false) }.onSuccess { c -> conta = c; voltarPara = Etapa.PEDIDO; etapa = if (c.saldo >= c.preco) Etapa.PEDIDO else Etapa.RECARGA }.onFailure { erro = it.message }; ocupado = false } },
                    aoChave = { etapa = Etapa.CHAVE },
                    aoVoltar = if (conta?.plano in listOf("gratuito", "pago", "propria")) ({ etapa = Etapa.PEDIDO }) else null)

                Etapa.CHAVE -> ChaveEtapa(chaveAtual = chave, ocupado = ocupado, erro = erro,
                    aoSalvar = { nova -> escopo.launch { ocupado = true; erro = null
                        runCatching { Assistente.definirPlano(ctx, email, "propria", false) }.onSuccess { Assistente.guardaChave(ctx, nova); chave = nova; conta = it; etapa = Etapa.PEDIDO }.onFailure { erro = it.message }; ocupado = false } },
                    aoRemover = { Assistente.guardaChave(ctx, ""); chave = ""; etapa = Etapa.PLANOS },
                    aoCancelar = { etapa = if (chave.isNotEmpty() && conta?.plano == "propria") Etapa.PEDIDO else Etapa.PLANOS })

                Etapa.PEDIDO -> {
                    val c = conta
                    Titulo("Pedir ao assistente")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Texto2(when {
                            usaChave -> "Chave própria · cobrança no OpenRouter"
                            c?.plano == "pago" -> "Pago · saldo ${reais(c.saldo)}"
                            c?.plano == "gratuito" -> "Gratuito · pode usar dados para treino · ${c.gratisHoje}/${c.gratisLimite} hoje"
                            else -> "Sem plano"
                        }, Modifier.weight(1f))
                        TextButton(onClick = { erro = null; etapa = Etapa.PLANOS }) { Text("Trocar", color = Acento) }
                    }
                    Texto2("Exemplos: \"marca dentista quinta 15h\", \"academia toda terça das 6 às 7\", \"joga o teclado de hoje pra 21h\".")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = pedido, onValueChange = { pedido = it; if (it != pedidoRespondido) resposta = null }, placeholder = { Text("O que você quer marcar ou mudar?") }, modifier = Modifier.weight(1f), maxLines = 3)
                        IconButton(onClick = {
                            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR"); putExtra(RecognizerIntent.EXTRA_PROMPT, "Diga o que quer marcar ou mudar") }
                            runCatching { ouvir.launch(i) }.onFailure { erro = "Este aparelho não tem reconhecimento de voz disponível." }
                        }, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Mic, contentDescription = "Falar (preenche o campo para você revisar)", tint = Acento) }
                    }
                    if (ocupado) Linha { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Acento); Texto2("Interpretando…", Modifier.padding(start = 8.dp)) }
                    erro?.let { Erro(it) }
                    bloqueio?.let { (msg, acao) ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Papel).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(msg, color = Tinta, fontSize = 14.sp, lineHeight = 20.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (acao == "credito") Button(onClick = { bloqueio = null; voltarPara = Etapa.PEDIDO; etapa = Etapa.RECARGA }, modifier = Modifier.height(48.dp)) { Text("Adicionar crédito") }
                                if (acao == "planos") Button(onClick = { bloqueio = null; etapa = Etapa.PLANOS }, modifier = Modifier.height(48.dp)) { Text("Ver plano pago") }
                                OutlinedButton(onClick = { bloqueio = null; etapa = Etapa.PLANOS }, modifier = Modifier.height(48.dp)) { Text("Trocar plano") }
                            }
                        }
                    }
                    val resp = resposta
                    if (resp != null && pedido == pedidoRespondido) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Papel).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (resp.resumo.isNotEmpty()) Text(resp.resumo, color = Tinta, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                        resp.acoes.forEach { a -> Text("• " + Assistente.descreve(a, blocos), color = Tinta, fontSize = 14.sp, lineHeight = 20.sp) }
                        resp.pergunta?.let { Text(it, color = Acento, fontSize = 14.sp, lineHeight = 20.sp) }
                        if (resp.acoes.isEmpty() && resp.pergunta == null) Texto2("Não entendi nada que dê para aplicar. Tente com dia e horário.")
                        if (resp.custo > 0) Texto2("Este pedido custou ${reais(resp.custo)}." + (if (resp.pergunta != null) " Cada novo envio custa ${reais(resp.custo)}." else ""))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { etapa = Etapa.HISTORICO }) { Text("Histórico", color = Tinta2) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = aoFechar, modifier = Modifier.height(48.dp)) { Text("Fechar", color = Tinta2) }
                        Spacer(Modifier.width(8.dp))
                        if (resp != null && pedido == pedidoRespondido && resp.acoes.isNotEmpty()) {
                            Button(onClick = { aoAplicar(resp); aoFechar() }, modifier = Modifier.height(48.dp)) { Text("Aplicar (${resp.acoes.size})") }
                        } else Button(onClick = {
                            if (pedido.isBlank()) return@Button
                            escopo.launch { ocupado = true; erro = null; bloqueio = null; resposta = null
                                val texto = pedido.trim()
                                runCatching { Assistente.pedir(ctx, email, texto, hoje, seg, blocos, if (usaChave) chave else null) }
                                    .onSuccess { r -> resposta = r; pedidoRespondido = pedido; r.conta?.let { conta = it } }
                                    .onFailure { e ->
                                        val f = e as? Assistente.Falha
                                        when (f?.codigo) {
                                            402 -> bloqueio = "Saldo insuficiente. Cada pedido custa ${reais(conta?.preco ?: 5)}." to "credito"
                                            429 -> bloqueio = ("O limite diário do gratuito foi atingido." + (f.corpo?.optString("libera_em")?.takeIf { it.isNotEmpty() }?.let { " Libera às " + horaLocal(it) + "." } ?: "") + " Plano pago: ${reais(conta?.preco ?: 5)} por pedido, com saldo pré-pago.") to "planos"
                                            403 -> etapa = Etapa.PLANOS
                                            else -> erro = if (e is java.io.IOException) "Sem conexão. Seu pedido foi mantido." else (e.message ?: "Falhou")
                                        }
                                        f?.corpo?.optJSONObject("conta")?.let { runCatching { Assistente.conta(ctx, email) }.onSuccess { c -> conta = c } }
                                    }
                                ocupado = false }
                        }, enabled = !ocupado && pedido.isNotBlank(), modifier = Modifier.height(48.dp)) { Text(if (c?.plano == "pago" && !usaChave) "Entender · ${reais(c.preco)}" else "Entender") }
                    }
                    if (c?.plano == "pago" && !usaChave) Texto2("Cobramos quando a interpretação fica pronta, mesmo se você não aplicar as mudanças.")
                }

                Etapa.RECARGA -> RecargaEtapa(conta = conta, ocupado = ocupado, erro = erro,
                    aoGerar = { valor, cpf -> escopo.launch { ocupado = true; erro = null
                        runCatching { Assistente.criarRecarga(ctx, email, valor, cpf) }.onSuccess { recarga = it; etapa = Etapa.PIX }.onFailure { erro = "Não foi possível gerar o Pix: ${it.message}" }; ocupado = false } },
                    aoRetomar = conta?.pixPendente?.let { id -> { escopo.launch { ocupado = true; runCatching { Assistente.statusRecarga(ctx, email, id) }.onSuccess { recarga = it; etapa = Etapa.PIX }.onFailure { erro = it.message }; ocupado = false } } },
                    aoVoltar = { etapa = voltarPara })

                Etapa.PIX -> { val r = recarga
                    if (r == null) etapa = Etapa.RECARGA else PixEtapa(recarga = r, conta = conta,
                        aoVerificar = { escopo.launch { runCatching { Assistente.statusRecarga(ctx, email, r.id) }.onSuccess { nova -> recarga = nova; if (nova.status in listOf("RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH")) carregaConta() }.onFailure { erro = "Não conseguimos verificar o pagamento." } } },
                        aoOutro = { recarga = null; etapa = Etapa.RECARGA },
                        aoVoltar = { recarga = null; erro = null; etapa = voltarPara })
                }

                Etapa.HISTORICO -> HistoricoEtapa(email = email, aoRecarregar = { voltarPara = Etapa.HISTORICO; etapa = Etapa.RECARGA }, aoVoltar = { etapa = Etapa.PEDIDO })
            }
        }
    }
}

// ---------- etapas ----------

@Composable
private fun PlanosEtapa(conta: Assistente.Conta?, chave: String, ocupado: Boolean, erro: String?, aoGratuito: () -> Unit, aoPago: () -> Unit, aoChave: () -> Unit, aoVoltar: (() -> Unit)?) {
    var escolhido by remember { mutableStateOf<String?>(null) }
    var concordo by remember { mutableStateOf(false) }
    Titulo("Escolha como usar o assistente")
    conta?.let { Texto2("Conta: ${it.email}") }
    Column(Modifier.verticalScroll(rememberScrollState()).weightIfNeeded(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Cartao(selecionado = escolhido == "gratuito", onClick = { escolhido = "gratuito"; concordo = false }) {
            Text("Gratuito", color = Tinta, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text("R$ 0", color = Tinta, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
            Texto("Modelos gratuitos do OpenRouter."); Texto("Limite diário compartilhado entre quem usa o plano gratuito."); Texto("Os provedores podem usar os dados enviados para treinar modelos.")
            if (escolhido == "gratuito") Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enviamos seu pedido e os dados da semana exibida para interpretar o que você quer mudar. Os provedores podem usar esses dados para treinar modelos.", color = Tinta, fontSize = 14.sp, lineHeight = 20.sp)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = concordo, onCheckedChange = { concordo = it }); Text("Li e concordo com esse uso dos dados.", color = Tinta, fontSize = 14.sp) }
                Button(onClick = aoGratuito, enabled = concordo && !ocupado, modifier = Modifier.height(48.dp)) { Text("Concordo") }
            }
        }
        Cartao(selecionado = escolhido == "pago", onClick = { escolhido = "pago" }) {
            Text("Pago", color = Tinta, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text("${reais(conta?.preco ?: 5)} por pedido", color = Tinta, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
            Texto("Claude Haiku 4.5 · sem uso dos dados para treino."); Texto("Sem limite diário. Usa seu saldo pré-pago."); Texto("Recarga por Pix. Sem cartão e sem mensalidade.")
            conta?.takeIf { it.saldo > 0 }?.let { Texto2("Saldo atual: ${reais(it.saldo)}") }
            if (escolhido == "pago") Button(onClick = aoPago, enabled = !ocupado, modifier = Modifier.padding(top = 8.dp).height(48.dp)) { Text("Usar pago") }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, LinhaForte, RoundedCornerShape(12.dp)).clickable { aoChave() }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (chave.isNotEmpty()) "Usar minha chave do OpenRouter (já salva)" else "Usar minha chave do OpenRouter", color = Tinta, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Texto("Os pedidos são cobrados na sua conta do OpenRouter."); Texto("Uso dos dados e limites dependem do modelo, do provedor e das suas configurações.")
        }
        erro?.let { Erro(it) }
    }
    if (aoVoltar != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = aoVoltar, modifier = Modifier.height(48.dp)) { Text("Voltar", color = Tinta2) } }
}

@Composable
private fun ChaveEtapa(chaveAtual: String, ocupado: Boolean, erro: String?, aoSalvar: (String) -> Unit, aoRemover: () -> Unit, aoCancelar: () -> Unit) {
    var chave by remember { mutableStateOf(chaveAtual) }
    var mostrar by remember { mutableStateOf(false) }
    Titulo("Minha chave do OpenRouter")
    Texto("A chave é salva neste aparelho e enviada ao servidor do app a cada pedido, para fazer a chamada ao modelo na sua conta. Crie uma em openrouter.ai/keys.")
    OutlinedTextField(value = chave, onValueChange = { chave = it }, singleLine = true, placeholder = { Text("sk-or-…") }, visualTransformation = if (mostrar) VisualTransformation.None else PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        trailingIcon = { TextButton(onClick = { mostrar = !mostrar }) { Text(if (mostrar) "Ocultar" else "Mostrar chave", color = Acento) } })
    erro?.let { Erro(it) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        if (chaveAtual.isNotEmpty()) TextButton(onClick = aoRemover) { Text("Remover chave", color = Perigo) }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = aoCancelar, modifier = Modifier.height(48.dp)) { Text("Cancelar", color = Tinta2) }
        Spacer(Modifier.width(8.dp))
        Button(onClick = { aoSalvar(chave.trim()) }, enabled = chave.trim().length > 10 && !ocupado, modifier = Modifier.height(48.dp)) { Text("Salvar e usar") }
    }
}

@Composable
private fun RecargaEtapa(conta: Assistente.Conta?, ocupado: Boolean, erro: String?, aoGerar: (Int, String?) -> Unit, aoRetomar: (() -> Unit)?, aoVoltar: () -> Unit) {
    var valor by remember { mutableStateOf<Int?>(null) }
    var cpf by remember { mutableStateOf("") }
    Titulo("Adicionar crédito")
    conta?.let { Texto2("${it.email} · saldo ${reais(it.saldo)}") }
    if (conta != null && !conta.recargaDisponivel) Erro("A recarga ainda não está disponível neste ambiente.")
    if (conta?.ambienteAsaas == "sandbox") Texto2("Ambiente de teste: o Pix gerado aqui não movimenta dinheiro de verdade.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (v in (conta?.valoresRecarga ?: listOf(500, 1000, 2000))) {
            val on = valor == v
            Column(Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Acento else Papel).border(1.dp, if (on) Acento else LinhaForte, RoundedCornerShape(12.dp)).clickable { valor = v }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(reais(v).replace(",00", ""), color = if (on) Fundo else Tinta, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("${v / (conta?.preco ?: 5)} pedidos", color = if (on) Fundo else Tinta2, fontSize = 12.sp)
            }
        }
    }
    OutlinedTextField(value = cpf, onValueChange = { cpf = it.filter { ch -> ch.isDigit() }.take(14) }, singleLine = true, label = { Text("CPF (o Pix exige, só na primeira recarga)") }, modifier = Modifier.fillMaxWidth())
    aoRetomar?.let { TextButton(onClick = it) { Text("Retomar Pix pendente", color = Acento) } }
    erro?.let { Erro(it) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = aoVoltar, modifier = Modifier.height(48.dp)) { Text("Voltar", color = Tinta2) }
        Spacer(Modifier.width(8.dp))
        val v = valor
        Button(onClick = { if (v != null) aoGerar(v, cpf.ifBlank { null }) }, enabled = v != null && !ocupado && conta?.recargaDisponivel == true, modifier = Modifier.height(48.dp)) { Text(if (ocupado) "Gerando Pix…" else if (v != null) "Gerar Pix de ${reais(v).replace(",00", "")}" else "Gerar Pix") }
    }
}

@Composable
private fun PixEtapa(recarga: Assistente.Recarga, conta: Assistente.Conta?, aoVerificar: () -> Unit, aoOutro: () -> Unit, aoVoltar: () -> Unit) {
    val ctx = LocalContext.current
    var copiado by remember { mutableStateOf(false) }
    var restante by remember { mutableStateOf("") }
    val pago = recarga.status in listOf("RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH")
    val expirou = recarga.status in listOf("OVERDUE", "DELETED", "REFUNDED") || restante == "expirou"
    LaunchedEffect(recarga.id, pago) {
        while (!pago) {
            val v = recarga.vence?.let { runCatching { OffsetDateTime.parse(it.replace(" ", "T") + if (it.contains("+") || it.endsWith("Z")) "" else "-04:00") }.getOrNull() }
            if (v != null) { val s = java.time.Duration.between(OffsetDateTime.now(), v).seconds; restante = if (s <= 0) "expirou" else "%02d:%02d".format(s / 60, s % 60) }
            delay(5000); aoVerificar()
        }
    }
    Titulo(if (pago) "Pagamento confirmado" else "Pagar ${reais(recarga.valor).replace(",00", "")} por Pix")
    conta?.let { Texto2("Crédito para ${it.email}") }
    if (pago) {
        Text("${reais(recarga.valor)} adicionados · saldo ${reais(conta?.saldo ?: recarga.valor)}", color = Tinta, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Button(onClick = aoVoltar, modifier = Modifier.height(48.dp)) { Text("Voltar ao pedido") } }
    } else if (expirou) {
        Erro("Este Pix expirou.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = aoVoltar) { Text("Voltar", color = Tinta2) }; Button(onClick = aoOutro, modifier = Modifier.height(48.dp)) { Text("Gerar outro") } }
    } else {
        val bmp = remember(recarga.pixImagem) { recarga.pixImagem?.let { runCatching { val b = Base64.decode(it, Base64.DEFAULT); BitmapFactory.decodeByteArray(b, 0, b.size) }.getOrNull() } }
        if (bmp != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Box(Modifier.size(256.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp)) { Image(bmp.asImageBitmap(), contentDescription = "QR code Pix", modifier = Modifier.size(240.dp)) } }
        Button(onClick = { (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Pix", recarga.pixPayload ?: "")); copiado = true }, enabled = !recarga.pixPayload.isNullOrEmpty(), modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(if (copiado) "Código copiado" else "Copiar código Pix") }
        Texto2("Abra seu banco e cole o código na opção Pix Copia e Cola.")
        if (restante.isNotEmpty()) Texto2("Expira em $restante")
        Linha { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Tinta2); Texto2("Aguardando pagamento. A confirmação é automática.", Modifier.padding(start = 8.dp)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = aoVoltar) { Text("Fechar (o Pix continua válido)", color = Tinta2) }; TextButton(onClick = aoVerificar) { Text("Verificar novamente", color = Acento) } }
    }
}

@Composable
private fun HistoricoEtapa(email: String, aoRecarregar: () -> Unit, aoVoltar: () -> Unit) {
    val ctx = LocalContext.current
    var itens by remember { mutableStateOf<List<Assistente.ItemHistorico>?>(null) }
    var conta by remember { mutableStateOf<Assistente.Conta?>(null) }
    var erro by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { Assistente.historico(ctx, email) }.onSuccess { (l, c) -> itens = l; conta = c }.onFailure { erro = it.message } }
    Titulo("Histórico")
    conta?.let { Text("Saldo ${reais(it.saldo)} · ${it.email}", color = Tinta, fontSize = 14.sp) }
    erro?.let { Erro(it) }
    val l = itens
    if (l == null && erro == null) Linha { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Acento) }
    else if (l != null && l.isEmpty()) Texto2("Nenhuma recarga nem pedido pago ainda.")
    else if (l != null) Column(Modifier.verticalScroll(rememberScrollState()).weightIfNeeded(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (it in l) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Papel).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (it.tipo == "recarga") "Recarga Pix" else "Pedido", color = Tinta, fontSize = 14.sp)
                Texto2(horaLocal(it.quando) + " · " + when (it.status) { "PENDING" -> "aguardando"; "RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH" -> "confirmada"; "OVERDUE" -> "expirada"; "ok" -> "concluído"; else -> it.status })
            }
            Text((if (it.valor >= 0) "+" else "−") + reais(kotlin.math.abs(it.valor)), color = if (it.valor >= 0) Acento else Tinta, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = aoVoltar) { Text("Voltar", color = Tinta2) }; Button(onClick = aoRecarregar, modifier = Modifier.height(48.dp)) { Text("Adicionar crédito") } }
}

// ---------- peças ----------
@Composable private fun Titulo(t: String) = Text(t, color = Tinta, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
@Composable private fun Texto(t: String) = Text(t, color = Tinta, fontSize = 14.sp, lineHeight = 20.sp)
@Composable private fun Texto2(t: String, m: Modifier = Modifier) = Text(t, color = Tinta2, fontSize = 12.sp, lineHeight = 16.sp, modifier = m)
@Composable private fun Erro(t: String) = Text(t, color = Perigo, fontSize = 14.sp, lineHeight = 20.sp)
@Composable private fun Linha(c: @Composable () -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) { c() }
@Composable private fun Cartao(selecionado: Boolean, onClick: () -> Unit, c: @Composable () -> Unit) =
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Papel).border(if (selecionado) 2.dp else 1.dp, if (selecionado) Acento else LinhaForte, RoundedCornerShape(12.dp)).clickable { onClick() }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (selecionado) Text("Selecionado", color = Acento, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        c()
    }
private fun Modifier.weightIfNeeded(): Modifier = this
private fun horaLocal(iso: String): String = runCatching {
    val t = if (iso.endsWith("Z")) OffsetDateTime.parse(iso.dropLast(1).let { if (it.length == 19) "$it+00:00" else it.substring(0, 19) + "+00:00" }) else OffsetDateTime.parse(iso)
    t.atZoneSameInstant(ZoneId.of("America/Cuiaba")).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
}.getOrDefault(iso)
