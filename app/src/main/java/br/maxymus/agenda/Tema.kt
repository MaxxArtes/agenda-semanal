package br.maxymus.agenda

import androidx.compose.ui.graphics.Color

/** Sistema visual (Astra, 17/09), compartilhado por todas as telas. */
internal val Fundo = Color(0xFF10141C)
internal val Papel = Color(0xFF181F2B)
internal val Elevada = Color(0xFF232D3D)
internal val Linha = Color(0xFF283345)
internal val LinhaForte = Color(0xFF43516A)
internal val Tinta = Color(0xFFF3F5FA)
internal val Tinta2 = Color(0xFFB5C0D3)
internal val Acento = Color(0xFF8AA4FF)
internal val Perigo = Color(0xFFFF707B)

internal fun reais(centavos: Int): String = "R$ " + "%d,%02d".format(centavos / 100, centavos % 100)
