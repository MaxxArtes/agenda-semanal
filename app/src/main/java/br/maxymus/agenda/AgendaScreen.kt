package br.maxymus.agenda

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val Fundo = Color(0xFF0F1420); private val Papel = Color(0xFF171D2C); private val Linha = Color(0xFF26304A); private val LinhaForte = Color(0xFF36425F)
private val Tinta = Color(0xFFE8ECF5); private val Tinta2 = Color(0xFFA3AEC6); private val Hoje = Color(0xFF1D2740); private val Acento = Color(0xFF6C8CFF); private val Agora = Color(0xFFE63946)
private val fmtData: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Dados que o editor manipula; `id`/`serie` nulos = bloco novo. */
private data class Edicao(val base: Bloco?, val dia: Int, val ini: Int, val fim: Int, val cat: Categoria, val rot: String)

@Composable
fun AgendaScreen(conta: String, sair: () -> Unit, autorizar: (Intent) -> Unit) {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val repo = remember(conta) { GoogleAgenda(contexto, conta) }
    val hoje = remember { LocalDate.now() }
    var seg by remember { mutableStateOf(hoje.minusDays((hoje.dayOfWeek.value - 1).toLong())) }
    var blocos by remember { mutableStateOf<List<Bloco>>(emptyList()) }
    var estado by remember { mutableStateOf("Carregando o Google Agenda") }
    var ocupado by remember { mutableStateOf(false) }
    var selecionado by remember { mutableStateOf<String?>(null) }
    var alcance by remember { mutableStateOf("semana") }
    var copiado by remember { mutableStateOf<Bloco?>(null) }
    var diaMovel by remember { mutableStateOf(hoje.dayOfWeek.value - 1) }
    var edicao by remember { mutableStateOf<Edicao?>(null) }
    val movel = LocalConfiguration.current.screenWidthDp < 600

    fun sel() = blocos.firstOrNull { it.id == selecionado }
    fun dataDe(dia: Int): LocalDate = seg.plusDays(dia.toLong())

    /** Roda uma operação no Google e recarrega a semana; erros viram texto na linha de estado. */
    fun executa(texto: String, acao: suspend () -> Unit) {
        escopo.launch {
            ocupado = true; estado = texto
            try {
                withContext(Dispatchers.IO) { acao(); blocos = repo.semana(seg) }
                estado = if (blocos.isEmpty()) "Semana vazia. Toque numa hora para adicionar." else "Sincronizado com o Google Agenda"
            } catch (e: UserRecoverableAuthIOException) { estado = "Autorize o acesso ao Google Agenda."; autorizar(e.intent) }
            catch (e: Exception) { estado = GoogleAgenda.mensagem(e) }
            ocupado = false
        }
    }
    LaunchedEffect(seg) { selecionado = null; executa("Carregando a semana") {} }

    // ---- mudanças com alcance ----
    fun aplicar(b: Bloco, dia: Int, ini: Int, fim: Int, cat: Categoria = b.cat, rot: String = b.rot, escopoMudanca: String = alcance) {
        val data = dataDe(dia)
        executa("Salvando no Google Agenda") {
            when {
                b.serie == null -> repo.alterarUnico(b.id, rot, cat, data, ini, fim)
                b.unico || escopoMudanca == "dia" -> repo.alterarOcorrencia(b.id, rot, cat, data, ini, fim)
                else -> repo.alterarSerie(b.serie, rot, cat, dia, ini, fim)
            }
        }
    }
    fun criar(dia: Int, ini: Int, fim: Int, cat: Categoria, rot: String, escopoMudanca: String = alcance) {
        val data = dataDe(dia)
        executa("Criando no Google Agenda") { if (escopoMudanca == "dia") repo.criarUnico(rot, cat, data, ini, fim) else repo.criarSerie(rot, cat, data, ini, fim) }
    }
    fun remover(b: Bloco, escopoMudanca: String = alcance) {
        selecionado = null
        executa("Removendo") { if (b.serie != null && !b.unico && escopoMudanca == "semana") repo.remover(b.serie) else repo.remover(b.id) }
    }

    Column(modifier = Modifier.fillMaxSize().background(Fundo).statusBarsPadding()) {
        // ---- cabeçalho ----
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Agenda Semanal", color = Tinta, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("${seg.format(fmtData)} a ${seg.plusDays(6).format(fmtData)}", color = Tinta2, fontSize = 12.sp)
            }
            OutlinedButton(onClick = { seg = seg.minusWeeks(1) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) { Text("‹") }
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = { seg = hoje.minusDays((hoje.dayOfWeek.value - 1).toLong()); diaMovel = hoje.dayOfWeek.value - 1 }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) { Text("Hoje") }
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = { seg = seg.plusWeeks(1) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) { Text("›") }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = alcance == "semana", onClick = { alcance = "semana" }, label = { Text("Toda semana") })
            FilterChip(selected = alcance == "dia", onClick = { alcance = "dia" }, label = { Text("Só este dia") })
            Spacer(Modifier.weight(1f))
            TextButton(onClick = sair) { Text("Sair", color = Tinta2) }
        }
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (ocupado) CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = Acento)
            Text(estado, color = Tinta2, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
        }
        // ---- faixa de dias (celular) ----
        if (movel) Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0..6) { val d = dataDe(i); val on = i == diaMovel
                Column(modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) Acento else Papel).border(1.dp, if (d == hoje) Acento else LinhaForte, RoundedCornerShape(10.dp)).clickable { diaMovel = i; selecionado = null }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(DIAS_CURTOS[i], color = if (on) Fundo else Tinta2, fontSize = 11.sp)
                    Text("${d.dayOfMonth}", color = if (on) Fundo else Tinta, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        // ---- barra do selecionado / copiado ----
        val s = sel()
        if (copiado != null || s != null) Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(Hoje).border(1.dp, Acento, RoundedCornerShape(10.dp)).padding(8.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val c = copiado
            if (c != null) {
                Text("Copiado: ${c.rot}. Toque numa hora vazia para colar.", color = Tinta, fontSize = 13.sp)
                OutlinedButton(onClick = { copiado = null }) { Text("Parar") }
            } else if (s != null) {
                Text("${s.rot} ${hhmm(s.ini)}–${hhmm(s.fim)}" + (if (s.unico) " (só ${s.data.format(fmtData)})" else ""), color = Tinta, fontSize = 13.sp)
                Button(onClick = { copiado = s; selecionado = null }) { Text("Copiar") }
                OutlinedButton(onClick = { if (s.fim + s.duracao <= FIM) criar(s.dia, s.fim, s.fim + s.duracao, s.cat, s.rot) else estado = "Não cabe abaixo: o dia termina à meia-noite." }) { Text("Duplicar") }
                OutlinedButton(onClick = { edicao = Edicao(s, s.dia, s.ini, s.fim, s.cat, s.rot) }) { Text("Editar") }
                if (s.unico && s.serie != null) OutlinedButton(onClick = { selecionado = null; executa("Voltando à rotina") { repo.voltarRotina(s) } }) { Text("Voltar à rotina") }
                OutlinedButton(onClick = { selecionado = null }) { Text("Desmarcar") }
            }
        }
        // ---- grade ----
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
            Grade(
                blocos = blocos, seg = seg, hoje = hoje, movel = movel, diaMovel = diaMovel, selecionado = selecionado,
                aoTocarVazio = { dia, m ->
                    val c = copiado
                    when {
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
            Button(onClick = { edicao = Edicao(null, if (movel) diaMovel else hoje.dayOfWeek.value - 1, 8 * 60, 9 * 60, Categoria.OUTRO, "") }, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).navigationBarsPadding()) { Text("Adicionar") }
        }
    }

    // ---- editor ----
    edicao?.let { ed ->
        var rot by remember(ed) { mutableStateOf(ed.rot) }
        var dia by remember(ed) { mutableStateOf(ed.dia) }
        var cat by remember(ed) { mutableStateOf(ed.cat) }
        var ini by remember(ed) { mutableStateOf(ed.ini) }
        var fim by remember(ed) { mutableStateOf(ed.fim) }
        var escopoMudanca by remember(ed) { mutableStateOf(if (ed.base?.unico == true) "dia" else alcance) }
        var erro by remember(ed) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { edicao = null },
            title = { Text(if (ed.base == null) "Novo compromisso" else "Editar compromisso") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = rot, onValueChange = { rot = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Seletor("Dia", DIAS[dia], DIAS) { dia = it }
                    Seletor("Categoria", cat.nome, Categoria.entries.map { it.nome }) { cat = Categoria.entries[it] }
                    val horas = (INICIO until FIM step PASSO).toList()
                    Seletor("Início", hhmm(ini), horas.map { hhmm(it) }) { ini = horas[it] }
                    val fins = (INICIO + PASSO..FIM step PASSO).toList()
                    Seletor("Fim", hhmm(fim), fins.map { hhmm(it) }) { fim = fins[it] }
                    if (ed.base?.unico != true) Column {
                        Text("Vale para", color = Tinta2, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = escopoMudanca == "semana", onClick = { escopoMudanca = "semana" }); Text("Todas as semanas") }
                        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = escopoMudanca == "dia", onClick = { escopoMudanca = "dia" }); Text("Só ${DIAS[dia]} ${dataDe(dia).format(fmtData)}") }
                    }
                    erro?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (rot.isBlank()) { erro = "Dê um nome ao compromisso."; return@Button }
                    if (fim <= ini) { erro = "O fim precisa ser depois do início."; return@Button }
                    val b = ed.base
                    if (b == null) criar(dia, ini, fim, cat, rot.trim(), escopoMudanca) else aplicar(b, dia, ini, fim, cat, rot.trim(), escopoMudanca)
                    edicao = null
                }) { Text("Salvar") }
            },
            dismissButton = {
                Row {
                    ed.base?.let { b -> TextButton(onClick = { remover(b, escopoMudanca); edicao = null }) { Text("Remover", color = Agora) } }
                    TextButton(onClick = { edicao = null }) { Text("Cancelar") }
                }
            }
        )
    }
}

@Composable
private fun Seletor(rotulo: String, atual: String, opcoes: List<String>, aoEscolher: (Int) -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    Column {
        Text(rotulo, color = Tinta2, fontSize = 12.sp)
        Box {
            OutlinedButton(onClick = { aberto = true }, modifier = Modifier.fillMaxWidth()) { Text(atual, modifier = Modifier.weight(1f), textAlign = TextAlign.Start); Text("▾") }
            DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
                opcoes.forEachIndexed { i, o -> DropdownMenuItem(text = { Text(o) }, onClick = { aoEscolher(i); aberto = false }) }
            }
        }
    }
}

/** A grade: coluna das horas + 7 colunas (ou 1 no celular), com blocos posicionados por minuto. */
@Composable
private fun Grade(
    blocos: List<Bloco>, seg: LocalDate, hoje: LocalDate, movel: Boolean, diaMovel: Int, selecionado: String?,
    aoTocarVazio: (Int, Int) -> Unit, aoTocarBloco: (Bloco) -> Unit,
    aoMover: (Bloco, Int, Int) -> Unit, aoEsticar: (Bloco, Int, Int) -> Unit, aoDeslizar: (Int) -> Unit,
) {
    val alturaMeia: Dp = if (movel) 30.dp else 24.dp
    val larguraHora: Dp = 44.dp
    val linhas = (FIM - INICIO) / PASSO
    val alturaTotal = alturaMeia * linhas + 28.dp
    val densidade = LocalDensity.current
    val rolagem = rememberScrollState()
    BoxWithConstraints(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Papel).border(1.dp, Linha, RoundedCornerShape(10.dp))) {
        val colunas = if (movel) 1 else 7
        val larguraCol: Dp = (maxWidth - larguraHora) / colunas
        val pxMeia = with(densidade) { alturaMeia.toPx() }
        val pxCol = with(densidade) { larguraCol.toPx() }
        val pxHora = with(densidade) { larguraHora.toPx() }
        val pxTopo = with(densidade) { 28.dp.toPx() }
        fun diaDe(x: Float) = if (movel) diaMovel else ((x - pxHora) / pxCol).toInt().coerceIn(0, 6)
        fun minutoDe(y: Float) = (INICIO + ((y - pxTopo) / pxMeia).toInt().coerceIn(0, linhas - 1) * PASSO)

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rolagem)) {
            Box(modifier = Modifier.fillMaxWidth().height(alturaTotal)
                .pointerInput(movel, diaMovel, selecionado) { detectTapGestures { p -> if (p.x > pxHora && p.y > pxTopo) aoTocarVazio(diaDe(p.x), minutoDe(p.y)) } }
                .pointerInput(movel, selecionado) { if (movel) { var acumulado = 0f; detectHorizontalDragGestures(onDragStart = { acumulado = 0f }, onDragEnd = { if (abs(acumulado) > 60f) aoDeslizar(if (acumulado < 0) 1 else -1) }) { _, dx -> acumulado += dx } } }
            ) {
                // linhas e cabeçalho
                Canvas(modifier = Modifier.fillMaxSize()) {
                    for (c in 0 until colunas) {
                        val d = if (movel) diaMovel else c
                        if (seg.plusDays(d.toLong()) == hoje) drawRect(Hoje, Offset(pxHora + c * pxCol, 0f), androidx.compose.ui.geometry.Size(pxCol, size.height))
                        drawLine(LinhaForte, Offset(pxHora + c * pxCol, 0f), Offset(pxHora + c * pxCol, size.height), 1f)
                    }
                    drawLine(LinhaForte, Offset(0f, pxTopo), Offset(size.width, pxTopo), 1f)
                    for (r in 0..linhas) { val y = pxTopo + r * pxMeia; drawLine(if (r % 2 == 0) LinhaForte else Linha, Offset(pxHora, y), Offset(size.width, y), 1f) }
                    val agora = java.time.LocalTime.now(); val m = agora.hour * 60 + agora.minute
                    val colHoje = (hoje.dayOfWeek.value - 1)
                    if (m in INICIO until FIM && seg.plusDays(colHoje.toLong()) == hoje && (!movel || diaMovel == colHoje)) {
                        val y = pxTopo + (m - INICIO) / PASSO.toFloat() * pxMeia
                        drawLine(Agora, Offset(pxHora, y), Offset(size.width, y), 3f)
                    }
                }
                for (c in 0 until colunas) {
                    val d = if (movel) diaMovel else c; val data = seg.plusDays(d.toLong())
                    Column(modifier = Modifier.offset(x = larguraHora + larguraCol * c).width(larguraCol).height(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(DIAS[d], color = Tinta, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(data.format(fmtData), color = Tinta2, fontSize = 10.sp, maxLines = 1)
                    }
                }
                for (r in 0 until linhas step 2) {
                    Text(hhmm(INICIO + r * PASSO), color = Tinta2, fontSize = 10.sp, modifier = Modifier.offset(x = 4.dp, y = 28.dp + alturaMeia * r - (if (r == 0) 0.dp else 6.dp)))
                }
                // blocos
                for (b in blocos) {
                    val col = if (movel) { if (b.dia != diaMovel) continue else 0 } else b.dia
                    BlocoView(b, b.id == selecionado, larguraCol, alturaMeia, larguraHora + larguraCol * col, 28.dp + alturaMeia * ((b.ini - INICIO) / PASSO), pxMeia, pxCol, movel, aoTocarBloco, aoMover, aoEsticar)
                }
            }
        }
    }
}

@Composable
private fun BlocoView(
    b: Bloco, sel: Boolean, larguraCol: Dp, alturaMeia: Dp, x: Dp, y: Dp, pxMeia: Float, pxCol: Float, movel: Boolean,
    aoTocar: (Bloco) -> Unit, aoMover: (Bloco, Int, Int) -> Unit, aoEsticar: (Bloco, Int, Int) -> Unit,
) {
    var arrasto by remember(b.id, sel) { mutableStateOf(Offset.Zero) }
    var estica by remember(b.id, sel) { mutableStateOf(0f to 0f) }   // deslocamento das alças (cima, baixo) em px
    fun calcCol() = if (movel) 0 else (arrasto.x / pxCol).roundToInt()
    fun calcMin() = (arrasto.y / pxMeia).roundToInt() * PASSO
    fun calcIni() = (estica.first / pxMeia).roundToInt() * PASSO
    fun calcFim() = (estica.second / pxMeia).roundToInt() * PASSO
    val dCol = calcCol(); val dMin = calcMin(); val dIni = calcIni(); val dFim = calcFim()
    val iniVis = (b.ini + dMin + dIni).coerceIn(INICIO, b.fim - PASSO)
    val fimVis = (b.fim + dMin + dFim).coerceIn(iniVis + PASSO, FIM)
    val offX = with(LocalDensity.current) { (x + larguraCol * dCol).roundToPx() }
    val offY = with(LocalDensity.current) { (y + alturaMeia * ((iniVis - b.ini) / PASSO)).roundToPx() }
    Box(modifier = Modifier.offset { IntOffset(offX, offY) }.width(larguraCol).height(alturaMeia * ((fimVis - iniVis) / PASSO)).padding(horizontal = 3.dp, vertical = 1.dp)) {
        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(b.cat.cor)
            .then(if (sel) Modifier.border(2.dp, Acento, RoundedCornerShape(6.dp)) else Modifier)
            .then(if (b.unico) Modifier.border(1.dp, Color.White.copy(alpha = .7f), RoundedCornerShape(6.dp)) else Modifier)
            .pointerInput(b, sel) { detectTapGestures { aoTocar(b) } }
            .then(if (sel) Modifier.pointerInput(b) {
                detectDragGestures(onDragEnd = {
                    val nDia = (b.dia + calcCol()).coerceIn(0, 6); val nIni = (b.ini + calcMin()).coerceIn(INICIO, FIM - b.duracao)
                    if (nDia != b.dia || nIni != b.ini) aoMover(b, nDia, nIni)
                    arrasto = Offset.Zero
                }, onDragCancel = { arrasto = Offset.Zero }) { change, drag -> change.consume(); arrasto += drag }
            } else Modifier),
            contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(b.rot + (if (b.unico) " •" else ""), color = b.cat.texto, fontSize = if (movel) 13.sp else 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 13.sp)
                if (fimVis - iniVis > PASSO) Text("${hhmm(iniVis)} – ${hhmm(fimVis)}", color = b.cat.texto.copy(alpha = .9f), fontSize = if (movel) 11.sp else 10.sp, maxLines = 1)
            }
        }
        if (sel) {
            // alças: em cima muda o início, embaixo muda o fim
            Box(modifier = Modifier.align(Alignment.TopCenter).width(56.dp).height(16.dp).offset(y = (-6).dp).clip(RoundedCornerShape(8.dp)).background(Acento)
                .pointerInput(b) { detectDragGestures(onDragEnd = { val nIni = (b.ini + calcIni()).coerceIn(INICIO, b.fim - PASSO); if (nIni != b.ini) aoEsticar(b, nIni, b.fim); estica = 0f to 0f }, onDragCancel = { estica = 0f to 0f }) { change, drag -> change.consume(); estica = (estica.first + drag.y) to estica.second } })
            Box(modifier = Modifier.align(Alignment.BottomCenter).width(56.dp).height(16.dp).offset(y = 6.dp).clip(RoundedCornerShape(8.dp)).background(Acento)
                .pointerInput(b) { detectDragGestures(onDragEnd = { val nFim = (b.fim + calcFim()).coerceIn(b.ini + PASSO, FIM); if (nFim != b.fim) aoEsticar(b, b.ini, nFim); estica = 0f to 0f }, onDragCancel = { estica = 0f to 0f }) { change, drag -> change.consume(); estica = estica.first to (estica.second + drag.y) } })
        }
    }
}
