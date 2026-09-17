package br.maxymus.agenda

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Assistente por linguagem: manda o pedido ("marca dentista quinta 15h") e a semana em tela para o
 * serviço do dono, que consulta um modelo e devolve ações estruturadas. O app mostra o resumo e só
 * aplica depois que o usuário confirma. A chave do modelo fica no servidor; quem quiser pode usar a
 * própria chave do OpenRouter (guardada só neste aparelho).
 */
object Assistente {
    private const val URL_SERVICO = "https://pocketlm.maxymus.dev.br/agenda/assistente"
    val disponivel: Boolean get() = BuildConfig.ASSISTENTE_TOKEN.isNotEmpty()

    /** Uma ação proposta pelo modelo. `id` refere-se a um bloco da semana em tela. */
    data class Acao(val tipo: String, val id: String?, val dia: Int?, val ini: Int?, val fim: Int?, val cat: Categoria?, val rot: String?, val alcance: String)
    data class Resposta(val resumo: String, val acoes: List<Acao>, val pergunta: String?, val modelo: String?)

    fun chavePropria(ctx: Context): String = ctx.getSharedPreferences("agenda", Context.MODE_PRIVATE).getString("chave_openrouter", "") ?: ""
    fun guardaChave(ctx: Context, chave: String) = ctx.getSharedPreferences("agenda", Context.MODE_PRIVATE).edit().putString("chave_openrouter", chave.trim()).apply()

    private fun minutos(s: String?): Int? {
        if (s == null) return null
        val p = s.split(":"); if (p.size != 2) return null
        val h = p[0].toIntOrNull() ?: return null; val m = p[1].toIntOrNull() ?: return null
        val t = h * 60 + m
        return if (h == 0 && m == 0) FIM else t.coerceIn(INICIO, FIM)
    }

    suspend fun pedir(ctx: Context, pedido: String, hoje: LocalDate, seg: LocalDate, blocos: List<Bloco>): Result<Resposta> = withContext(Dispatchers.IO) {
        runCatching {
            val corpo = JSONObject().apply {
                put("pedido", pedido); put("hoje", hoje.toString()); put("seg", seg.toString())
                val chave = chavePropria(ctx); if (chave.isNotEmpty()) put("chave", chave)
                put("blocos", JSONArray().apply { blocos.forEach { b -> put(JSONObject().apply { put("id", b.id); put("dia", b.dia); put("ini", hhmm(b.ini)); put("fim", hhmm(b.fim)); put("cat", b.cat.chave); put("rot", b.rot); put("unico", b.unico) }) } })
            }
            val con = (URL(URL_SERVICO).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 15000; readTimeout = 90000; doOutput = true
                setRequestProperty("Authorization", "Bearer " + BuildConfig.ASSISTENTE_TOKEN); setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            con.outputStream.use { it.write(corpo.toString().toByteArray()) }
            val texto = (if (con.responseCode < 400) con.inputStream else con.errorStream).bufferedReader().use { it.readText() }
            val j = JSONObject(texto)
            if (con.responseCode >= 400) throw IllegalStateException(j.optString("erro", "O assistente respondeu ${con.responseCode}"))
            val acoes = mutableListOf<Acao>()
            val arr = j.optJSONArray("acoes") ?: JSONArray()
            for (i in 0 until arr.length()) { val a = arr.getJSONObject(i)
                acoes += Acao(a.optString("tipo"), a.optString("id").ifEmpty { null }, if (a.has("dia")) a.optInt("dia") else null, minutos(a.optString("ini").ifEmpty { null }), minutos(a.optString("fim").ifEmpty { null }),
                    a.optString("cat").ifEmpty { null }?.let { Categoria.por(it) }, a.optString("rot").ifEmpty { null }, a.optString("alcance", "dia")) }
            Resposta(j.optString("resumo"), acoes, j.optString("pergunta").ifEmpty { null }.takeIf { it != "null" }, j.optString("modelo").ifEmpty { null })
        }
    }

    /** Frase legível de uma ação, para a confirmação. */
    fun descreve(a: Acao, blocos: List<Bloco>): String {
        val alvo = a.id?.let { id -> blocos.firstOrNull { it.id == id } }
        val onde = a.dia?.let { DIAS[it.coerceIn(0, 6)] } ?: alvo?.let { DIAS[it.dia] } ?: "?"
        val alc = if (a.alcance == "semana") "toda semana" else "só neste dia"
        return when (a.tipo) {
            "criar" -> "Criar \"${a.rot ?: "?"}\" (${a.cat?.nome ?: "Outro"}) $onde ${a.ini?.let { hhmm(it) } ?: "?"}–${a.fim?.let { hhmm(it) } ?: "?"}, $alc"
            "mover" -> "Mover \"${alvo?.rot ?: a.id}\" para $onde ${a.ini?.let { hhmm(it) } ?: "?"}, $alc"
            "alterar" -> "Alterar \"${alvo?.rot ?: a.id}\": " + listOfNotNull(a.rot?.let { "nome $it" }, a.cat?.let { "categoria ${it.nome}" }, a.dia?.let { "dia ${DIAS[it.coerceIn(0, 6)]}" }, a.ini?.let { "início ${hhmm(it)}" }, a.fim?.let { "fim ${hhmm(it)}" }).joinToString(", ") + ", $alc"
            "remover" -> "Remover \"${alvo?.rot ?: a.id}\", $alc"
            else -> a.tipo
        }
    }
}
