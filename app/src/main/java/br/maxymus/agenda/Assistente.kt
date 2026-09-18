package br.maxymus.agenda

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Assistente por linguagem. O app manda o pedido e a semana em tela ao serviço do dono, que consulta um
 * modelo e devolve ações estruturadas; o app confirma antes de aplicar. Planos: gratuito (modelos gratuitos,
 * com consentimento de uso dos dados para treino), pago (Haiku, saldo pré-pago por Pix) e chave própria do
 * OpenRouter (guardada neste aparelho e enviada ao servidor a cada pedido para fazer a chamada).
 * Identidade: access token da conta Google (escopo e-mail) no header X-Prova; o servidor confere.
 */
object Assistente {
    private const val BASE = "https://pocketlm.maxymus.dev.br/agenda"
    val disponivel: Boolean get() = BuildConfig.ASSISTENTE_TOKEN.isNotEmpty()

    class Falha(val codigo: Int, mensagem: String, val corpo: JSONObject?) : Exception(mensagem)

    data class Acao(val tipo: String, val id: String?, val dia: Int?, val ini: Int?, val fim: Int?, val cat: Categoria?, val rot: String?, val alcance: String)
    data class Sugestao(val rotulo: String, val pedido: String)
    data class Resposta(val resumo: String, val acoes: List<Acao>, val pergunta: String?, val sugestoes: List<Sugestao>, val modelo: String?, val plano: String?, val custo: Int, val conta: Conta?, val bruto: String)
    data class Conta(val email: String, val plano: String, val consentiu: Boolean, val saldo: Int, val preco: Int, val gratisHoje: Int, val gratisLimite: Int, val gratisLiberaEm: String?,
                     val valoresRecarga: List<Int>, val recargaDisponivel: Boolean, val ambienteAsaas: String, val pixPendente: String?)
    data class Recarga(val id: String, val valor: Int, val status: String, val pixPayload: String?, val pixImagem: String?, val vence: String?)
    data class ItemHistorico(val quando: String, val tipo: String, val valor: Int, val status: String, val id: String)

    fun chavePropria(ctx: Context): String = ctx.getSharedPreferences("agenda", Context.MODE_PRIVATE).getString("chave_openrouter", "") ?: ""
    fun guardaChave(ctx: Context, chave: String) = ctx.getSharedPreferences("agenda", Context.MODE_PRIVATE).edit().putString("chave_openrouter", chave.trim()).apply()

    /** Access token da conta Google (escopo e-mail) para o servidor confirmar quem é a pessoa. */
    private fun prova(ctx: Context, email: String): String =
        GoogleAuthUtil.getToken(ctx, Account(email, "com.google"), "oauth2:https://www.googleapis.com/auth/userinfo.email")

    private fun minutos(s: String?): Int? {
        if (s == null) return null
        val p = s.split(":"); if (p.size != 2) return null
        val h = p[0].toIntOrNull() ?: return null; val m = p[1].toIntOrNull() ?: return null
        val t = h * 60 + m
        return if (h == 0 && m == 0) FIM else t.coerceIn(INICIO, FIM)
    }

    private fun conta(j: JSONObject?): Conta? {
        if (j == null) return null
        val vr = mutableListOf<Int>(); j.optJSONArray("valores_recarga")?.let { a -> for (i in 0 until a.length()) vr += a.getInt(i) }
        return Conta(j.optString("email"), j.optString("plano", "nenhum"), j.optBoolean("consentiu_treino"), j.optInt("saldo"), j.optInt("preco_pedido", 5), j.optInt("gratis_hoje"), j.optInt("gratis_limite", 30),
            j.optString("gratis_libera_em").ifEmpty { null }, vr, j.optBoolean("recarga_disponivel"), j.optString("ambiente_asaas", "sandbox"), j.optString("pix_pendente").ifEmpty { null }.takeIf { it != "null" })
    }
    private fun recarga(j: JSONObject) = Recarga(j.optString("id"), j.optInt("valor"), j.optString("status"), j.optString("pix_payload").ifEmpty { null }, j.optString("pix_imagem").ifEmpty { null }, j.optString("vence").ifEmpty { null })

    /** Chamada ao serviço; erros HTTP viram Falha(codigo, mensagem do servidor, corpo). */
    private suspend fun chama(ctx: Context, email: String, metodo: String, rota: String, corpo: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val p = prova(ctx, email)
        val con = (URL(BASE + rota).openConnection() as HttpURLConnection).apply {
            requestMethod = metodo; connectTimeout = 15000; readTimeout = 90000
            setRequestProperty("Authorization", "Bearer " + BuildConfig.ASSISTENTE_TOKEN); setRequestProperty("X-Prova", p); setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (corpo != null) { doOutput = true; outputStream.use { it.write(corpo.toString().toByteArray()) } }
        }
        val texto = (if (con.responseCode < 400) con.inputStream else con.errorStream)?.bufferedReader()?.use { it.readText() } ?: "{}"
        val j = runCatching { JSONObject(texto) }.getOrElse { JSONObject() }
        if (con.responseCode >= 400) throw Falha(con.responseCode, j.optString("erro", "O serviço respondeu ${con.responseCode}"), j)
        j
    }

    suspend fun conta(ctx: Context, email: String): Conta = conta(chama(ctx, email, "GET", "/conta"))!!
    suspend fun definirPlano(ctx: Context, email: String, plano: String, consentiu: Boolean): Conta =
        conta(chama(ctx, email, "POST", "/plano", JSONObject().put("plano", plano).put("consentiu_treino", consentiu)))!!
    suspend fun criarRecarga(ctx: Context, email: String, valor: Int, cpf: String?): Recarga =
        recarga(chama(ctx, email, "POST", "/credito", JSONObject().put("valor", valor).apply { if (!cpf.isNullOrBlank()) put("cpf", cpf) }))
    suspend fun statusRecarga(ctx: Context, email: String, id: String): Recarga = recarga(chama(ctx, email, "GET", "/credito/$id"))
    suspend fun historico(ctx: Context, email: String): Pair<List<ItemHistorico>, Conta?> {
        val j = chama(ctx, email, "GET", "/historico"); val a = j.optJSONArray("itens") ?: JSONArray(); val lista = mutableListOf<ItemHistorico>()
        for (i in 0 until a.length()) { val it = a.getJSONObject(i); lista += ItemHistorico(it.optString("quando"), it.optString("tipo"), it.optInt("valor"), it.optString("status"), it.optString("id")) }
        return lista to conta(j.optJSONObject("conta"))
    }

    /** `anterior` = (pedido, JSON bruto da resposta) da rodada que está sendo refinada. */
    suspend fun pedir(ctx: Context, email: String, pedido: String, hoje: LocalDate, seg: LocalDate, blocos: List<Bloco>, chave: String?, anterior: Pair<String, String>? = null): Resposta {
        val corpo = JSONObject().apply {
            put("pedido", pedido); put("hoje", hoje.toString()); put("seg", seg.toString())
            anterior?.let { (ped, bruto) -> put("anterior", JSONObject().put("pedido", ped).put("resposta", runCatching { JSONObject(bruto) }.getOrElse { JSONObject() })) }
            if (!chave.isNullOrBlank()) put("chave", chave)
            put("blocos", JSONArray().apply { blocos.forEach { b -> put(JSONObject().apply { put("id", b.id); put("dia", b.dia); put("ini", hhmm(b.ini)); put("fim", hhmm(b.fim)); put("cat", b.cat.chave); put("rot", b.rot); put("unico", b.unico) }) } })
        }
        val j = chama(ctx, email, "POST", "/assistente", corpo)
        val acoes = mutableListOf<Acao>(); val arr = j.optJSONArray("acoes") ?: JSONArray()
        for (i in 0 until arr.length()) { val a = arr.getJSONObject(i)
            acoes += Acao(a.optString("tipo"), a.optString("id").ifEmpty { null }, if (a.has("dia")) a.optInt("dia") else null, minutos(a.optString("ini").ifEmpty { null }), minutos(a.optString("fim").ifEmpty { null }),
                a.optString("cat").ifEmpty { null }?.let { Categoria.por(it) }, a.optString("rot").ifEmpty { null }, a.optString("alcance").let { al -> if (al == "semana") "semana" else "dia" }) }
        val sug = mutableListOf<Sugestao>(); j.optJSONArray("sugestoes")?.let { sa -> for (i in 0 until sa.length()) { val x = sa.getJSONObject(i); if (x.optString("rotulo").isNotEmpty() && x.optString("pedido").isNotEmpty()) sug += Sugestao(x.optString("rotulo"), x.optString("pedido")) } }
        val limpo = JSONObject().apply { put("resumo", j.optString("resumo")); put("acoes", j.optJSONArray("acoes") ?: JSONArray()); put("pergunta", j.opt("pergunta")); put("sugestoes", j.optJSONArray("sugestoes") ?: JSONArray()) }
        return Resposta(j.optString("resumo"), acoes, j.optString("pergunta").ifEmpty { null }.takeIf { it != "null" }, sug.take(6), j.optString("modelo").ifEmpty { null }, j.optString("plano").ifEmpty { null }, j.optInt("custo"), conta(j.optJSONObject("conta")), limpo.toString())
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
