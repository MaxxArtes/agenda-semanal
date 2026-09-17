package br.maxymus.agenda

/** Ids temporários dos blocos criados na tela antes de o Google responder; a Fila resolve na hora de gravar. */
object Ids {
    private val mapa = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var n = 0
    fun temporario(): String = synchronized(this) { "tmp-" + (++n) + "-" + System.currentTimeMillis().toString(36) }
    fun registra(tmp: String, real: String) { mapa[tmp] = real }
    fun real(id: String): String = if (id.startsWith("tmp-")) (mapa[id] ?: throw IllegalStateException("A criação anterior ainda não chegou ao Google")) else id
}
