package br.maxymus.agenda

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// Sistema visual (Astra, 17/09)
private val Fundo = Color(0xFF10141C); private val Papel = Color(0xFF181F2B); private val Elevada = Color(0xFF232D3D)
private val Linha = Color(0xFF283345); private val LinhaForte = Color(0xFF43516A)
private val Tinta = Color(0xFFF3F5FA); private val Tinta2 = Color(0xFFB5C0D3); private val Acento = Color(0xFF8AA4FF); private val Perigo = Color(0xFFFF707B)
private val FUSO: ZoneId = ZoneId.of("America/Cuiaba")
private val fmtData: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val fmtCurta: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale("pt", "BR"))

/** Dados que o editor manipula; `base` nulo = bloco novo. */
private data class Edicao(val base: Bloco?, val dia: Int, val ini: Int, val fim: Int, val cat: Categoria, val rot: String)

/** Estado da comunicação com o Google, separado por tipo (Astra P0). */
private sealed class Rede { object Ocioso : Rede(); data class Carregando(val texto: String) : Rede(); data class Erro(val texto: String, val autorizar: Intent? = null) : Rede() }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun AgendaScreen(conta: String, sair: () -> Unit, autorizar: (Intent) -> Unit) {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val repo = remember(conta) { GoogleAgenda(contexto, conta) }
    var hoje by remember { mutableStateOf(LocalDate.now(FUSO)) }
    var seg by remember { mutableStateOf(hoje.minusDays((hoje.dayOfWeek.value - 1).toLong())) }
    var blocos by remember { mutableStateOf<List<Bloco>>(emptyList()) }
    var carregou by remember { mutableStateOf(false) }
    var rede by remember { mutableStateOf<Rede>(Rede.Carregando("Carregando semana…")) }
    var selecionado by remember { mutableStateOf<String?>(null) }
    var alcance by remember { mutableStateOf("semana") }
    var copiado by remember { mutableStateOf<Bloco?>(null) }
    var diaMovel by remember { mutableStateOf(hoje.dayOfWeek.value - 1) }
    var edicao by remember { mutableStateOf<Edicao?>(null) }
    var menu by remember { mutableStateOf(false) }
    var novaVersao by remember { mutableStateOf<Atualizador.Versao?>(null) }
    var conferindo by remember { mutableStateOf(false) }
    val instalada = remember { Atualizador.versaoInstalada(contexto) }
    LaunchedEffect(Unit) { val v = Atualizador.consultar(); if (v != null && v.codigo > instalada.second) novaVersao = v }
    val movel = LocalConfiguration.current.screenWidthDp < 600
    val ocupado = rede is Rede.Carregando

    fun sel() = blocos.firstOrNull { it.id == selecionado }
    fun dataDe(dia: Int): LocalDate = seg.plusDays(dia.toLong())
    fun rotuloAlcance(b: Bloco?, escopo: String = alcance): String = when {
        b != null && b.unico -> "Só ${b.data.format(fmtCurta)}"
        escopo == "dia" -> "Só ${dataDe(b?.dia ?: diaMovel).format(fmtCurta)}"
        else -> "Série inteira"
    }

    /** Roda uma escrita no Google e recarrega a semana; distingue falha da escrita e falha da releitura. */
    fun executa(texto: String, escrita: (suspend () -> Unit)? = null) {
        escopo.launch {
            rede = Rede.Carregando(texto)
            var escreveu = false
            try {
                withContext(Dispatchers.IO) { escrita?.invoke(); escreveu = true; blocos = repo.semana(seg) }
                carregou = true; rede = Rede.Ocioso
            } catch (e: UserRecoverableAuthIOException) { rede = Rede.Erro("Precisa autorizar o acesso ao Google Agenda.", e.intent) }
            catch (e: Exception) {
                rede = Rede.Erro(if (escrita != null && escreveu) "Alteração enviada; não foi possível atualizar a tela." else if (escrita != null) "Não foi possível salvar: " + GoogleAgenda.mensagem(e) else "Não foi possível carregar: " + GoogleAgenda.mensagem(e))
            }
        }
    }
    LaunchedEffect(seg) { selecionado = null; carregou = false; executa("Carregando semana…") }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); val h = LocalDate.now(FUSO); if (h != hoje) hoje = h } }

    // ---- mudanças com alcance ----
    fun aplicar(b: Bloco, dia: Int, ini: Int, fim: Int, cat: Categoria = b.cat, rot: String = b.rot, escopoMudanca: String = alcance) {
        val data = dataDe(dia)
        executa("Salvando…") {
            when {
                b.serie == null -> repo.alterarUnico(b.id, rot, cat, data, ini, fim)
                b.unico || escopoMudanca == "dia" -> repo.alterarOcorrencia(b.id, rot, cat, data, ini, fim)
                else -> repo.alterarSerie(b.serie, rot, cat, dia, ini, fim)
            }
        }
    }
    fun criar(dia: Int, ini: Int, fim: Int, cat: Categoria, rot: String, escopoMudanca: String = alcance) {
        val data = dataDe(dia)
        executa("Criando…") { if (escopoMudanca == "dia") repo.criarUnico(rot, cat, data, ini, fim) else repo.criarSerie(rot, cat, data, ini, fim) }
    }
    fun remover(b: Bloco, escopoMudanca: String = alcance) {
        selecionado = null
        executa("Removendo…") { if (b.serie != null && !b.unico && escopoMudanca == "semana") repo.remover(b.serie) else repo.remover(b.id) }
    }

    Column(modifier = Modifier.fillMaxSize().background(Fundo).statusBarsPadding()) {
        // ---- cabeçalho: título + menu ----
        Row(modifier = Modifier.fillMaxWidth().height(48.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Agenda Semanal", color = Tinta, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções", tint = Tinta2) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(conta, fontSize = 12.sp, color = Tinta2) }, onClick = { menu = false }, enabled = false)
                    DropdownMenuItem(text = { Text("Versão ${instalada.first}" + (novaVersao?.let { " · nova: ${it.nome}" } ?: "")) }, onClick = { menu = false }, enabled = false)
                    DropdownMenuItem(text = { Text(if (novaVersao != null) "Atualizar para ${novaVersao!!.nome}" else if (conferindo) "Conferindo…" else "Conferir atualização") }, onClick = {
                        val nv = novaVersao
                        if (nv != null) { menu = false; Atualizador.baixarEInstalar(contexto, nv) }
                        else escopo.launch { conferindo = true; val v = Atualizador.consultar(); conferindo = false
                            if (v == null) rede = Rede.Erro("Não consegui consultar o canal de atualização.") else if (v.codigo > instalada.second) novaVersao = v else { menu = false; rede = Rede.Ocioso; android.widget.Toast.makeText(contexto, "Você já está na versão mais nova (${instalada.first}).", android.widget.Toast.LENGTH_SHORT).show() } }
                    })
                    DropdownMenuItem(text = { Text("Sair") }, onClick = { menu = false; sair() })
                }
            }
        }
        // ---- navegação da semana ----
        Row(modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { seg = seg.minusWeeks(1) }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Semana anterior", tint = Tinta) }
            Text(intervalo(seg), color = Tinta, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            TextButton(onClick = { seg = hoje.minusDays((hoje.dayOfWeek.value - 1).toLong()); diaMovel = hoje.dayOfWeek.value - 1 }) { Text("Hoje", color = Acento) }
            IconButton(onClick = { seg = seg.plusWeeks(1) }) { Icon(Icons.Filled.ChevronRight, contentDescription = "Próxima semana", tint = Tinta) }
        }
        // ---- alcance das mudanças ----
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Aplicar mudanças a", color = Tinta2, fontSize = 12.sp, modifier = Modifier.padding(end = 12.dp))
            Segmentado(listOf("semana" to "Toda semana", "dia" to "Só este dia"), alcance) { alcance = it }
        }
        // ---- faixa de dias (celular) ----
        if (movel) Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0..6) { val d = dataDe(i); val on = i == diaMovel; val ehHoje = d == hoje
                Column(modifier = Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Acento else Color.Transparent).clickable { diaMovel = i; selecionado = null },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(DIAS_CURTOS[i], color = if (on) Fundo else Tinta2, fontSize = 12.sp, lineHeight = 16.sp)
                    Text("${d.dayOfMonth}", color = if (on) Fundo else Tinta, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
                    Box(modifier = Modifier.width(20.dp).height(2.dp).background(if (ehHoje) (if (on) Fundo else Acento) else Color.Transparent))
                }
            }
        }
        novaVersao?.let { nv -> Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Elevada).padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Versão ${nv.nome} disponível" + (nv.mudou.firstOrNull()?.let { ": $it" } ?: ""), color = Tinta, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            TextButton(onClick = { Atualizador.baixarEInstalar(contexto, nv) }) { Text("Atualizar", color = Acento) }
            TextButton(onClick = { novaVersao = null }) { Text("Depois", color = Tinta2) } } }
        // ---- faixa de estado (carregando / erro / vazio) ----
        val r = rede
        when {
            r is Rede.Carregando -> Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Acento); Text(r.texto, color = Tinta2, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp)) }
            r is Rede.Erro -> Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Elevada).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r.texto, color = Tinta, fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (r.autorizar != null) TextButton(onClick = { autorizar(r.autorizar) }) { Text("Autorizar", color = Acento) } else TextButton(onClick = { executa("Carregando semana…") }) { Text("Tentar novamente", color = Acento) } }
            carregou && blocos.none { !movel || it.dia == diaMovel } -> Text(if (blocos.isEmpty()) "Sua semana está vazia. Toque em um horário ou em Adicionar." else "Sem compromissos ${DIAS[diaMovel].lowercase().let { if (it.endsWith("a") || it.endsWith("o")) "neste $it" else "nesta $it" }}. Toque em um horário ou em Adicionar.",
                color = Tinta2, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        // ---- grade ----
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Grade(
                blocos = blocos, seg = seg, hoje = hoje, movel = movel, diaMovel = diaMovel, selecionado = selecionado, travado = ocupado,
                aoTocarVazio = { dia, m ->
                    val c = copiado
                    when {
                        !carregou || ocupado -> {}
                        c != null -> criar(dia, m.coerceAtMost(FIM - c.duracao), (m + c.duracao).coerceAtMost(FIM), c.cat, c.rot)
                        selecionado != null -> selecionado = null
                        else -> edicao = Edicao(null, dia, m, (m + 60).coerceAtMost(FIM), Categoria.OUTRO, "")
                    }
                },
                aoTocarBloco = { b -> if (selecionado == b.id) edicao = Edicao(b, b.dia, b.ini, b.fim, b.cat, b.rot) else selecionado = b.id },
                aoMover = { b, dia, ini -> aplicar(b, dia, ini, ini + b.duracao) },
                aoEsticar = { b, ini, fim -> aplicar(b, b.dia, ini, fim) },
                aoDeslizar = { sentido -> if (selecionado == null) diaMovel = (diaMovel + sentido).coerceIn(0, 6) },
            )
        }
        // ---- barra inferior fixa ----
        val s = sel(); val c = copiado
        Column(modifier = Modifier.fillMaxWidth().background(Elevada).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            when {
                c != null -> {
                    Text("Copiado: ${c.rot} · ${rotuloAlcance(null)}", color = Tinta, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Toque numa hora vazia para colar.", color = Tinta2, fontSize = 12.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { copiado = null }, modifier = Modifier.height(48.dp)) { Text("Parar") } }
                }
                s != null -> {
                    Text("${s.rot} · ${hhmm(s.ini)}–${hhmm(s.fim)}", color = Tinta, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Mudanças: ${rotuloAlcance(s)}", color = Tinta2, fontSize = 12.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Button(onClick = { copiado = s; selecionado = null }, enabled = !ocupado, modifier = Modifier.height(48.dp)) { Text("Copiar") }
                        val cabe = s.fim + s.duracao <= FIM
                        OutlinedButton(onClick = { criar(s.dia, s.fim, s.fim + s.duracao, s.cat, s.rot) }, enabled = cabe && !ocupado, modifier = Modifier.height(48.dp)) { Text(if (cabe) "Duplicar" else "Duplicar (não cabe)") }
                        OutlinedButton(onClick = { edicao = Edicao(s, s.dia, s.ini, s.fim, s.cat, s.rot) }, enabled = !ocupado, modifier = Modifier.height(48.dp)) { Text("Editar") }
                        if (s.unico && s.serie != null) OutlinedButton(onClick = { selecionado = null; executa("Voltando à rotina…") { repo.voltarRotina(s) } }, enabled = !ocupado, modifier = Modifier.height(48.dp)) { Text("Voltar à rotina") }
                        OutlinedButton(onClick = { selecionado = null }, modifier = Modifier.height(48.dp)) { Text("Desmarcar") }
                    }
                }
                else -> Button(onClick = { edicao = Edicao(null, if (movel) diaMovel else hoje.dayOfWeek.value - 1, 8 * 60, 9 * 60, Categoria.OUTRO, "") }, enabled = carregou && !ocupado, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("+ Adicionar") }
            }
        }
    }

    // ---- editor em folha ----
    edicao?.let { ed ->
        var rot by remember(ed) { mutableStateOf(ed.rot) }
        var dia by remember(ed) { mutableStateOf(ed.dia) }
        var cat by remember(ed) { mutableStateOf(ed.cat) }
        var ini by remember(ed) { mutableStateOf(ed.ini) }
        var fim by remember(ed) { mutableStateOf(ed.fim) }
        var escopoMudanca by remember(ed) { mutableStateOf(if (ed.base?.unico == true) "dia" else alcance) }
        var erroNome by remember(ed) { mutableStateOf<String?>(null) }
        var erroHora by remember(ed) { mutableStateOf<String?>(null) }
        var confirmaRemover by remember(ed) { mutableStateOf(false) }
        val folha = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { edicao = null }, sheetState = folha, containerColor = Elevada, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
            Column(modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).align(Alignment.CenterHorizontally).padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (ed.base == null) "Novo compromisso" else "Editar compromisso", color = Tinta, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = rot, onValueChange = { rot = it; erroNome = null }, label = { Text("Nome") }, singleLine = true, isError = erroNome != null, supportingText = erroNome?.let { { Text(it, color = Perigo) } }, modifier = Modifier.fillMaxWidth())
                    Seletor("Dia", "${DIAS[dia]} ${dataDe(dia).format(fmtData)}", DIAS.mapIndexed { i, n -> "$n ${dataDe(i).format(fmtData)}" }) { dia = it }
                    SeletorCategoria(cat) { cat = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val horas = (INICIO until FIM step PASSO).toList()
                        Box(Modifier.weight(1f)) { Seletor("Início", hhmm(ini), horas.map { hhmm(it) }) { ini = horas[it]; erroHora = null } }
                        val fins = (INICIO + PASSO..FIM step PASSO).toList()
                        Box(Modifier.weight(1f)) { Seletor("Fim", hhmm(fim), fins.map { hhmm(it) }) { fim = fins[it]; erroHora = null } }
                    }
                    Text(if (fim > ini) "Duração: ${duracao(fim - ini)}" else (erroHora ?: "O fim precisa ser depois do início."), color = if (fim > ini) Tinta2 else Perigo, fontSize = 12.sp)
                    if (ed.base?.unico == true) Text("Vale só para ${ed.base.data.format(fmtData)}.", color = Tinta2, fontSize = 12.sp)
                    else Column {
                        Text("Vale para", color = Tinta2, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                        Segmentado(listOf("semana" to "Todas as semanas", "dia" to "Só ${dataDe(dia).format(fmtCurta)}"), escopoMudanca) { escopoMudanca = it }
                        if (escopoMudanca == "semana" && ed.base != null) Text("Toda semana altera a série inteira, inclusive as semanas passadas.", color = Tinta2, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    ed.base?.let { b ->
                        if (!confirmaRemover) TextButton(onClick = { confirmaRemover = true }) { Text("Remover", color = Perigo) }
                        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (b.serie != null && !b.unico && escopoMudanca == "semana") "Remover de todas as semanas?" else "Remover só em ${dataDe(dia).format(fmtCurta)}?", color = Tinta, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Button(onClick = { remover(b, escopoMudanca); edicao = null }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Perigo, contentColor = Fundo)) { Text("Remover") }
                            TextButton(onClick = { confirmaRemover = false }) { Text("Não") }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { edicao = null }, modifier = Modifier.height(48.dp)) { Text("Cancelar", color = Tinta2) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (rot.isBlank()) { erroNome = "Dê um nome ao compromisso."; return@Button }
                        if (fim <= ini) { erroHora = "O fim precisa ser depois do início."; return@Button }
                        val b = ed.base
                        if (b == null) criar(dia, ini, fim, cat, rot.trim(), escopoMudanca) else aplicar(b, dia, ini, fim, cat, rot.trim(), escopoMudanca)
                        edicao = null
                    }, modifier = Modifier.height(48.dp)) { Text("Salvar") }
                }
            }
        }
    }
}

private fun intervalo(seg: LocalDate): String {
    val dom = seg.plusDays(6)
    val mes = DateTimeFormatter.ofPattern("MMM", Locale("pt", "BR"))
    return when {
        seg.year != dom.year -> "${seg.dayOfMonth} ${seg.format(mes)} ${seg.year} – ${dom.dayOfMonth} ${dom.format(mes)} ${dom.year}"
        seg.month != dom.month -> "${seg.dayOfMonth} ${seg.format(mes)} – ${dom.dayOfMonth} ${dom.format(mes)} ${dom.year}"
        else -> "${seg.dayOfMonth}–${dom.dayOfMonth} ${seg.format(mes)} ${seg.year}"
    }
}
private fun duracao(m: Int): String = if (m % 60 == 0) "${m / 60} h" else if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"

@Composable
private fun Segmentado(opcoes: List<Pair<String, String>>, atual: String, aoEscolher: (String) -> Unit) {
    Row(modifier = Modifier.height(48.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, LinhaForte, RoundedCornerShape(12.dp))) {
        opcoes.forEach { (v, rotulo) -> val on = v == atual
            Box(modifier = Modifier.fillMaxHeight().background(if (on) Acento else Color.Transparent).clickable { aoEscolher(v) }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(rotulo, color = if (on) Fundo else Tinta2, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Seletor(rotulo: String, atual: String, opcoes: List<String>, aoEscolher: (Int) -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    Column {
        Text(rotulo, color = Tinta2, fontSize = 12.sp)
        Box {
            OutlinedButton(onClick = { aberto = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(atual, modifier = Modifier.weight(1f), textAlign = TextAlign.Start, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("▾") }
            DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
                opcoes.forEachIndexed { i, o -> DropdownMenuItem(text = { Text(o) }, onClick = { aoEscolher(i); aberto = false }) }
            }
        }
    }
}

@Composable
private fun SeletorCategoria(atual: Categoria, aoEscolher: (Categoria) -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    Column {
        Text("Categoria", color = Tinta2, fontSize = 12.sp)
        Box {
            OutlinedButton(onClick = { aberto = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(atual.cor)); Spacer(Modifier.width(10.dp))
                Text(atual.nome, modifier = Modifier.weight(1f), textAlign = TextAlign.Start); Text("▾")
            }
            DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
                Categoria.entries.forEach { c -> DropdownMenuItem(leadingIcon = { Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(c.cor)) }, text = { Text(c.nome) }, onClick = { aoEscolher(c); aberto = false }) }
            }
        }
    }
}

/** A grade: coluna das horas + 7 colunas (ou 1 no celular), com blocos posicionados por minuto. */
@Composable
private fun Grade(
    blocos: List<Bloco>, seg: LocalDate, hoje: LocalDate, movel: Boolean, diaMovel: Int, selecionado: String?, travado: Boolean,
    aoTocarVazio: (Int, Int) -> Unit, aoTocarBloco: (Bloco) -> Unit,
    aoMover: (Bloco, Int, Int) -> Unit, aoEsticar: (Bloco, Int, Int) -> Unit, aoDeslizar: (Int) -> Unit,
) {
    val alturaMeia: Dp = if (movel) 64.dp else 72.dp
    val larguraHora: Dp = 48.dp
    val cabecalho: Dp = if (movel) 0.dp else 48.dp
    val linhas = (FIM - INICIO) / PASSO
    val alturaTotal = alturaMeia * linhas + cabecalho
    val densidade = LocalDensity.current
    val rolagem = rememberScrollState()
    var manipulando by remember { mutableStateOf(false) }
    // primeira abertura no dia de hoje: uma hora antes de agora
    LaunchedEffect(Unit) {
        val agora = LocalTime.now(FUSO); val m = agora.hour * 60 + agora.minute
        if (m > INICIO + 60) rolagem.scrollTo(with(densidade) { (alturaMeia * ((m - 60 - INICIO) / PASSO)).roundToPx() })
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Papel)) {
        val colunas = if (movel) 1 else 7
        val larguraCol: Dp = (maxWidth - larguraHora) / colunas
        val pxMeia = with(densidade) { alturaMeia.toPx() }
        val pxCol = with(densidade) { larguraCol.toPx() }
        val pxHora = with(densidade) { larguraHora.toPx() }
        val pxTopo = with(densidade) { cabecalho.toPx() }
        fun diaDe(x: Float) = if (movel) diaMovel else ((x - pxHora) / pxCol).toInt().coerceIn(0, 6)
        fun minutoDe(y: Float) = (INICIO + ((y - pxTopo) / pxMeia).toInt().coerceIn(0, linhas - 1) * PASSO)

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rolagem, enabled = !manipulando)) {
            Box(modifier = Modifier.fillMaxWidth().height(alturaTotal)
                .pointerInput(movel, diaMovel, selecionado, travado) { detectTapGestures { p -> if (p.x > pxHora && p.y > pxTopo) aoTocarVazio(diaDe(p.x), minutoDe(p.y)) } }
                .pointerInput(movel, selecionado) { if (movel && selecionado == null) { var dx = 0f; var dy = 0f
                    detectHorizontalDragGestures(onDragStart = { dx = 0f; dy = 0f }, onDragEnd = { if (abs(dx) > 64f * density && abs(dx) > 1.5f * abs(dy)) aoDeslizar(if (dx < 0) 1 else -1) }) { ch, d -> dx += d; dy += abs(ch.positionChange().y) } } }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    for (c in 0 until colunas) {
                        val d = if (movel) diaMovel else c
                        if (!movel && seg.plusDays(d.toLong()) == hoje) drawRect(Elevada, Offset(pxHora + c * pxCol, 0f), Size(pxCol, size.height))
                        drawLine(LinhaForte, Offset(pxHora + c * pxCol, 0f), Offset(pxHora + c * pxCol, size.height), 1f)
                    }
                    if (!movel) drawLine(LinhaForte, Offset(0f, pxTopo), Offset(size.width, pxTopo), 1f)
                    for (r in 0..linhas) { val y = pxTopo + r * pxMeia; drawLine(if (r % 2 == 0) LinhaForte else Linha, Offset(pxHora, y), Offset(size.width, y), 1f) }
                }
                if (!movel) for (c in 0 until 7) {
                    val data = seg.plusDays(c.toLong())
                    Column(modifier = Modifier.offset(x = larguraHora + larguraCol * c).width(larguraCol).height(cabecalho), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(DIAS_CURTOS[c], color = Tinta, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(data.format(fmtData), color = Tinta2, fontSize = 12.sp, maxLines = 1)
                    }
                }
                for (r in 0 until linhas step 2) {
                    Text(hhmm(INICIO + r * PASSO), color = Tinta2, fontSize = 12.sp, modifier = Modifier.offset(x = 6.dp, y = cabecalho + alturaMeia * r + (if (r == 0) 2.dp else (-8).dp)))
                }
                for (b in blocos) {
                    val col = if (movel) { if (b.dia != diaMovel) continue else 0 } else b.dia
                    BlocoView(b, b.id == selecionado, !travado, larguraCol, alturaMeia, larguraHora + larguraCol * col, cabecalho + alturaMeia * ((b.ini - INICIO) / PASSO), pxMeia, pxCol, movel,
                        aoTocarBloco, aoMover, aoEsticar, aoManipular = { manipulando = it })
                }
                // linha da hora atual, acima dos blocos
                val agora = LocalTime.now(FUSO); val m = agora.hour * 60 + agora.minute
                val colHoje = hoje.dayOfWeek.value - 1
                if (m in INICIO until FIM && seg.plusDays(colHoje.toLong()) == hoje && (!movel || diaMovel == colHoje)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val y = pxTopo + (m - INICIO) / PASSO.toFloat() * pxMeia; val x0 = pxHora + (if (movel) 0 else colHoje) * pxCol
                        drawCircle(Perigo, 5.dp.toPx(), Offset(x0, y)); drawLine(Perigo, Offset(x0, y), Offset(x0 + pxCol, y), 2.dp.toPx())
                    }
                }
            }
        }
    }
}

@Composable
private fun BlocoView(
    b: Bloco, sel: Boolean, editavel: Boolean, larguraCol: Dp, alturaMeia: Dp, x: Dp, y: Dp, pxMeia: Float, pxCol: Float, movel: Boolean,
    aoTocar: (Bloco) -> Unit, aoMover: (Bloco, Int, Int) -> Unit, aoEsticar: (Bloco, Int, Int) -> Unit, aoManipular: (Boolean) -> Unit,
) {
    val haptico = LocalHapticFeedback.current
    var arrasto by remember(b.id, sel) { mutableStateOf(Offset.Zero) }
    var estica by remember(b.id, sel) { mutableStateOf(0f to 0f) }
    var manipulando by remember(b.id, sel) { mutableStateOf(false) }
    fun calcCol() = if (movel) 0 else (arrasto.x / pxCol).roundToInt()
    fun calcMin() = (arrasto.y / pxMeia).roundToInt() * PASSO
    fun calcIni() = (estica.first / pxMeia).roundToInt() * PASSO
    fun calcFim() = (estica.second / pxMeia).roundToInt() * PASSO
    val dCol = calcCol(); val dMin = calcMin(); val dIni = calcIni(); val dFim = calcFim()
    val iniVis = (b.ini + dMin + dIni).coerceIn(INICIO, b.fim - PASSO)
    val fimVis = (b.fim + dMin + dFim).coerceIn(iniVis + PASSO, FIM)
    val offX = with(LocalDensity.current) { (x + larguraCol * dCol).roundToPx() }
    val offY = with(LocalDensity.current) { (y + alturaMeia * ((iniVis - b.ini) / PASSO)).roundToPx() }
    val fimGesto = { manipulando = false; aoManipular(false) }
    Box(modifier = Modifier.offset { IntOffset(offX, offY) }.width(larguraCol).height(alturaMeia * ((fimVis - iniVis) / PASSO)).padding(horizontal = 2.dp, vertical = 1.dp)) {
        Box(modifier = Modifier.fillMaxSize()
            .then(if (sel) Modifier.border(2.dp, Acento, RoundedCornerShape(8.dp)).padding(1.dp).border(1.dp, Fundo, RoundedCornerShape(7.dp)).padding(1.dp) else Modifier)
            .clip(RoundedCornerShape(if (sel) 6.dp else 8.dp)).background(b.cat.cor)
            .then(if (b.unico) Modifier.border(1.dp, Color.White.copy(alpha = .7f), RoundedCornerShape(6.dp)) else Modifier)
            .pointerInput(b, sel) { detectTapGestures { aoTocar(b) } }
            .then(if (sel && editavel) Modifier.pointerInput(b) {
                // mover só com toque longo: o arrasto imediato fica com a rolagem (Astra)
                detectDragGesturesAfterLongPress(
                    onDragStart = { haptico.performHapticFeedback(HapticFeedbackType.LongPress); manipulando = true; aoManipular(true) },
                    onDragEnd = { val nDia = (b.dia + calcCol()).coerceIn(0, 6); val nIni = (b.ini + calcMin()).coerceIn(INICIO, FIM - b.duracao)
                        if (nDia != b.dia || nIni != b.ini) aoMover(b, nDia, nIni); arrasto = Offset.Zero; fimGesto() },
                    onDragCancel = { arrasto = Offset.Zero; fimGesto() }) { change, drag -> change.consume(); arrasto += drag }
            } else Modifier),
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(b.rot + (if (b.unico) " •" else ""), color = b.cat.texto, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${hhmm(iniVis)}–${hhmm(fimVis)}", color = b.cat.texto, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
            }
        }
        if (manipulando) Box(modifier = Modifier.align(Alignment.TopCenter).offset(y = (-26).dp).clip(RoundedCornerShape(6.dp)).background(Elevada).padding(horizontal = 8.dp, vertical = 3.dp)) {
            Text("${hhmm(iniVis)}–${hhmm(fimVis)}", color = Tinta, fontSize = 12.sp)
        }
        if (sel && editavel) {
            // alças: visual 24x6, alvo 48x48; superior à esquerda, inferior à direita (Astra)
            Box(modifier = Modifier.align(Alignment.TopStart).offset(x = 8.dp, y = (-24).dp).size(48.dp)
                .pointerInput(b) { detectDragGestures(onDragStart = { manipulando = true; aoManipular(true) }, onDragEnd = { val nIni = (b.ini + calcIni()).coerceIn(INICIO, b.fim - PASSO); if (nIni != b.ini) aoEsticar(b, nIni, b.fim); estica = 0f to 0f; fimGesto() }, onDragCancel = { estica = 0f to 0f; fimGesto() }) { change, drag -> change.consume(); estica = (estica.first + drag.y) to estica.second } },
                contentAlignment = Alignment.Center) { Box(Modifier.width(24.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Acento)) }
            Box(modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = 24.dp).size(48.dp)
                .pointerInput(b) { detectDragGestures(onDragStart = { manipulando = true; aoManipular(true) }, onDragEnd = { val nFim = (b.fim + calcFim()).coerceIn(b.ini + PASSO, FIM); if (nFim != b.fim) aoEsticar(b, b.ini, nFim); estica = 0f to 0f; fimGesto() }, onDragCancel = { estica = 0f to 0f; fimGesto() }) { change, drag -> change.consume(); estica = estica.first to (estica.second + drag.y) } },
                contentAlignment = Alignment.Center) { Box(Modifier.width(24.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Acento)) }
        }
    }
}
