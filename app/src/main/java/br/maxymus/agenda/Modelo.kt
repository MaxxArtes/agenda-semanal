package br.maxymus.agenda

import androidx.compose.ui.graphics.Color
import java.time.LocalDate

/** Categorias com a cor da tela e o colorId do Google Agenda (1 a 11), para o evento ter a mesma cor lá. */
enum class Categoria(val chave: String, val nome: String, val cor: Color, val texto: Color, val colorId: String) {
    SONO("sono", "Sono", Color(0xFF2B4F8A), Color.White, "9"),
    MANHA("manha", "Rotina da manhã", Color(0xFF3F9A55), Color(0xFFEAFBE9), "10"),
    DESLOC("desloc", "Deslocamento", Color(0xFF7D67C9), Color(0xFFF2EDFF), "1"),
    TRABALHO("trabalho", "Trabalho", Color(0xFFC95555), Color(0xFFFFF1F1), "11"),
    REFEICAO("refeicao", "Família / Refeições", Color(0xFFD98A3C), Color(0xFFFFF3E6), "6"),
    TECLADO("teclado", "Teclado", Color(0xFFC2568D), Color(0xFFFFF0F7), "4"),
    MESTRADO("mestrado", "Mestrado", Color(0xFF6C5BD8), Color.White, "3"),
    ESTUDO("estudo", "Estudo", Color(0xFFD9B22E), Color(0xFF2B2200), "5"),
    IGREJA("igreja", "Célula / Igreja", Color(0xFF2F9E69), Color(0xFFE9FFF2), "2"),
    DESCANSO("descanso", "Descanso / Livre", Color(0xFF4C8FB3), Color(0xFFEAF7FF), "7"),
    OUTRO("outro", "Outro", Color(0xFF4A556E), Color(0xFFEEF1F8), "8");

    companion object {
        fun por(chave: String?) = entries.firstOrNull { it.chave == chave } ?: OUTRO
        fun porColorId(id: String?) = entries.firstOrNull { it.colorId == id } ?: OUTRO
    }
}

/**
 * Um bloco na semana em tela. `ini` e `fim` em minutos desde 00:00 (fim pode ser 1440 = meia-noite).
 * `serie` é o id do evento recorrente no Google (rotina); `id` é o id da ocorrência ou do evento avulso.
 * `unico` = vale só nesta data (evento avulso ou ocorrência alterada da série).
 */
data class Bloco(
    val id: String,
    val serie: String?,
    val data: LocalDate,
    val ini: Int,
    val fim: Int,
    val cat: Categoria,
    val rot: String,
    val unico: Boolean,
) {
    val dia: Int get() = data.dayOfWeek.value - 1   // segunda = 0
    val duracao: Int get() = fim - ini
}

const val INICIO = 5 * 60
const val FIM = 24 * 60
const val PASSO = 30
val DIAS = listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo")
val DIAS_CURTOS = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")

fun hhmm(m: Int): String = "%02d:%02d".format((m / 60) % 24, m % 60)
