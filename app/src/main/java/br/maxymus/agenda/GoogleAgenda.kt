package br.maxymus.agenda

import android.content.Context
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.client.json.gson.GsonFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * A agenda mora no Google Agenda, num calendário próprio chamado "Agenda Semanal".
 *  - bloco da rotina = evento recorrente (RRULE:FREQ=WEEKLY), marcado com a propriedade privada rotina=1
 *  - "só este dia" = ocorrência da série alterada (o Google guarda a exceção sozinho) ou evento avulso
 *  - categoria = propriedade privada cat + colorId, para a cor bater também no app do Google
 * Todas as chamadas são bloqueantes: rodar em Dispatchers.IO.
 */
class GoogleAgenda(private val contexto: Context, conta: String) {
    private val fuso: ZoneId = ZoneId.of("America/Cuiaba")
    private val credencial = GoogleAccountCredential.usingOAuth2(contexto, listOf(CalendarScopes.CALENDAR)).setSelectedAccountName(conta)
    private val api: Calendar = Calendar.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credencial).setApplicationName("Agenda Semanal").build()
    private val prefs = contexto.getSharedPreferences("agenda", Context.MODE_PRIVATE)

    /** Acha (ou cria) o calendário "Agenda Semanal" e guarda o id. */
    fun calendarioId(): String {
        prefs.getString("calendario", null)?.let { return it }
        val lista = api.calendarList().list().execute()
        val achado = lista.items?.firstOrNull { it.summary == NOME_CALENDARIO }?.id
        val id = achado ?: run {
            val novo = com.google.api.services.calendar.model.Calendar().setSummary(NOME_CALENDARIO).setTimeZone(fuso.id)
            api.calendars().insert(novo).execute().id
        }
        prefs.edit().putString("calendario", id).apply()
        return id
    }

    fun esqueceCalendario() = prefs.edit().remove("calendario").apply()

    /** Blocos da semana que começa em `seg` (segunda-feira), já expandindo as séries em ocorrências. */
    fun semana(seg: LocalDate): List<Bloco> {
        val cal = calendarioId()
        val de = DateTime(seg.atStartOfDay(fuso).toInstant().toEpochMilli())
        val ate = DateTime(seg.plusDays(7).atStartOfDay(fuso).toInstant().toEpochMilli())
        val eventos = api.events().list(cal).setTimeMin(de).setTimeMax(ate).setSingleEvents(true).setOrderBy("startTime").setMaxResults(500).execute().items ?: emptyList()
        return eventos.mapNotNull { paraBloco(it) }
    }

    private fun paraBloco(e: Event): Bloco? {
        val ini = e.start?.dateTime ?: return null
        val fim = e.end?.dateTime ?: return null
        val zIni = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(ini.value), fuso)
        val zFim = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(fim.value), fuso)
        val data = zIni.toLocalDate()
        val mIni = zIni.hour * 60 + zIni.minute
        var mFim = zFim.hour * 60 + zFim.minute
        if (zFim.toLocalDate().isAfter(data)) mFim = FIM.coerceAtMost(mFim + 24 * 60)   // termina à meia-noite ou depois: trava em 24:00
        if (mIni >= FIM || mFim <= INICIO) return null
        val priv = e.extendedProperties?.private ?: emptyMap()
        val cat = priv["cat"]?.let { Categoria.por(it) } ?: Categoria.porColorId(e.colorId)
        val serie = e.recurringEventId
        val original = e.originalStartTime?.dateTime?.value
        val unico = serie == null || priv["excecao"] == "1" || (original != null && original != ini.value)
        return Bloco(e.id, serie, data, mIni.coerceAtLeast(INICIO), mFim, cat, e.summary ?: "(sem nome)", unico)
    }

    private fun quando(data: LocalDate, minutos: Int): EventDateTime {
        val z = if (minutos >= 24 * 60) data.plusDays(1).atStartOfDay(fuso) else data.atTime(minutos / 60, minutos % 60).atZone(fuso)
        return EventDateTime().setDateTime(DateTime(z.toInstant().toEpochMilli())).setTimeZone(fuso.id)
    }

    private fun corpo(rot: String, cat: Categoria, data: LocalDate, ini: Int, fim: Int, rotina: Boolean, excecao: Boolean = false): Event {
        val props = mutableMapOf("cat" to cat.chave, "app" to "agenda-semanal")
        if (rotina) props["rotina"] = "1"
        if (excecao) props["excecao"] = "1"
        return Event().setSummary(rot).setColorId(cat.colorId).setStart(quando(data, ini)).setEnd(quando(data, fim))
            .setExtendedProperties(Event.ExtendedProperties().setPrivate(props))
    }

    /** Cria um bloco da rotina: série semanal começando na data dada. */
    fun criarSerie(rot: String, cat: Categoria, data: LocalDate, ini: Int, fim: Int): String {
        val e = corpo(rot, cat, data, ini, fim, rotina = true).setRecurrence(listOf("RRULE:FREQ=WEEKLY"))
        return api.events().insert(calendarioId(), e).execute().id
    }

    /** Cria um bloco só de uma data (evento avulso). */
    fun criarUnico(rot: String, cat: Categoria, data: LocalDate, ini: Int, fim: Int): String =
        api.events().insert(calendarioId(), corpo(rot, cat, data, ini, fim, rotina = false)).execute().id

    /**
     * Altera a série inteira (todas as semanas). Move a série para o dia da semana e horário novos
     * mantendo a data de início da série na mesma semana em que ela começou.
     */
    fun alterarSerie(serie: String, rot: String, cat: Categoria, dia: Int, ini: Int, fim: Int) {
        val cal = calendarioId()
        val mestre = api.events().get(cal, serie).execute()
        val inicioAtual = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(mestre.start.dateTime.value), fuso).toLocalDate()
        val segundaDaSerie = inicioAtual.minusDays((inicioAtual.dayOfWeek.value - 1).toLong())
        val novaData = segundaDaSerie.plusDays(dia.toLong())
        val e = corpo(rot, cat, novaData, ini, fim, rotina = true).setRecurrence(mestre.recurrence ?: listOf("RRULE:FREQ=WEEKLY"))
        api.events().patch(cal, serie, e).execute()
    }

    /** Altera só a ocorrência (exceção da série naquela data). Pode mudar de dia dentro da mesma semana. */
    fun alterarOcorrencia(id: String, rot: String, cat: Categoria, data: LocalDate, ini: Int, fim: Int) {
        api.events().patch(calendarioId(), id, corpo(rot, cat, data, ini, fim, rotina = false, excecao = true)).execute()
    }

    /** Altera um evento avulso. */
    fun alterarUnico(id: String, rot: String, cat: Categoria, data: LocalDate, ini: Int, fim: Int) {
        api.events().patch(calendarioId(), id, corpo(rot, cat, data, ini, fim, rotina = false)).execute()
    }

    /** Remove: a série inteira, ou só a ocorrência (vira exceção cancelada), ou o avulso. */
    fun remover(id: String) { api.events().delete(calendarioId(), id).execute() }

    /**
     * Volta a ocorrência à rotina: apaga a alteração daquela data. O Google não tem "desfazer exceção",
     * então recria a ocorrência com os dados da série na data original.
     */
    fun voltarRotina(bloco: Bloco) {
        val cal = calendarioId(); val serie = bloco.serie ?: return
        val mestre = api.events().get(cal, serie).execute()
        val zIni = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(mestre.start.dateTime.value), fuso)
        val zFim = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(mestre.end.dateTime.value), fuso)
        val ini = zIni.hour * 60 + zIni.minute
        val fim = if (zFim.toLocalDate().isAfter(zIni.toLocalDate())) FIM else zFim.hour * 60 + zFim.minute
        val priv = mestre.extendedProperties?.private ?: emptyMap()
        val cat = Categoria.por(priv["cat"])
        // a ocorrência original é a do mesmo dia da semana na semana em tela
        val dataOriginal = bloco.data.minusDays(bloco.dia.toLong()).plusDays((zIni.dayOfWeek.value - 1).toLong())
        val e = corpo(mestre.summary ?: "", cat, dataOriginal, ini, fim, rotina = false).setStatus("confirmed")
        api.events().patch(cal, bloco.id, e).execute()
    }

    companion object {
        const val NOME_CALENDARIO = "Agenda Semanal"
        val ESCOPO = CalendarScopes.CALENDAR
        fun mensagem(t: Throwable): String = when (t) {
            is com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException -> "Precisa autorizar o acesso ao Google Agenda."
            is GoogleJsonResponseException -> "Google Agenda respondeu ${t.statusCode}: ${t.details?.message ?: t.message}"
            is java.io.IOException -> "Sem conexão com o Google Agenda."
            else -> t.message ?: t::class.java.simpleName
        }
    }
}
