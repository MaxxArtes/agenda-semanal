package br.maxymus.agenda

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Lembretes locais (notificação ou alarme) para os blocos com `lembrete`, agendados no AlarmManager para os
 * próximos 7 dias. Reagendados sempre que a semana carrega, ao ligar o aparelho e uma vez por dia (alarme
 * de manutenção às 03:00). O dado mora no evento do Google; aqui só o disparo.
 */
object Lembretes {
    const val CANAL_NOTIF = "lembretes"
    const val CANAL_ALARME = "alarmes"
    private const val PREF_CONTA = "conta_lembretes"
    private val FUSO: ZoneId = ZoneId.of("America/Cuiaba")
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun canais(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CANAL_NOTIF, "Lembretes", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Aviso antes de um compromisso" })
        nm.createNotificationChannel(NotificationChannel(CANAL_ALARME, "Alarmes", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alarme de compromisso, com som de despertador"
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true); setBypassDnd(true)
        })
    }

    fun guardaConta(ctx: Context, email: String?) = ctx.getSharedPreferences("agenda", Context.MODE_PRIVATE).edit().putString(PREF_CONTA, email).apply()
    fun conta(ctx: Context): String? = ctx.getSharedPreferences("agenda", Context.MODE_PRIVATE).getString(PREF_CONTA, null)

    /** Alarme exato disponível? (Android 12+ pode exigir permissão nas configurações.) */
    fun exatoPermitido(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    /** Agenda os lembretes dos blocos dados (só os futuros, até 7 dias). Cancela os anteriores. */
    fun agendar(ctx: Context, blocos: List<Bloco>) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = ctx.getSharedPreferences("agenda", Context.MODE_PRIVATE)
        // cancela os agendados na rodada anterior
        val antigos = prefs.getStringSet("lembretes_agendados", emptySet()) ?: emptySet()
        for (chave in antigos) am.cancel(pendente(ctx, chave.hashCode(), null))
        val agora = System.currentTimeMillis(); val limite = agora + 7L * 24 * 3600 * 1000
        val novos = mutableSetOf<String>()
        for (b in blocos) {
            val l = Lembrete.de(b.lembrete); if (l.modo == "nenhum") continue
            val inicio = b.data.atTime((b.ini / 60) % 24, b.ini % 60).atZone(FUSO).toInstant().toEpochMilli()
            val disparo = inicio - l.minutos * 60_000L
            if (disparo < agora - 60_000 || disparo > limite) continue
            val chave = "${b.id}|${b.data}|${b.ini}|${l.chave}"
            val i = Intent(ctx, LembreteReceiver::class.java).setAction("br.maxymus.agenda.LEMBRETE").putExtra("chave", chave).putExtra("rot", b.rot).putExtra("cat", b.cat.chave)
                .putExtra("ini", b.ini).putExtra("fim", b.fim).putExtra("modo", l.modo).putExtra("minutos", l.minutos).putExtra("data", b.data.toString()).putExtra("disparo", disparo)
            val pi = pendente(ctx, chave.hashCode(), i)
            if (exatoPermitido(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, disparo, pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, disparo, pi)
            novos += chave
        }
        prefs.edit().putStringSet("lembretes_agendados", novos).apply()
        manutencao(ctx)
    }

    private fun pendente(ctx: Context, codigo: Int, i: Intent?): PendingIntent =
        PendingIntent.getBroadcast(ctx, codigo, i ?: Intent(ctx, LembreteReceiver::class.java).setAction("br.maxymus.agenda.LEMBRETE"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Alarme diário de manutenção às 03:00: relê o Google e reagenda (cobre semanas seguintes sem abrir o app). */
    private fun manutencao(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val proximo = java.time.ZonedDateTime.now(FUSO).plusDays(1).withHour(3).withMinute(0).withSecond(0).toInstant().toEpochMilli()
        val pi = PendingIntent.getBroadcast(ctx, 1, Intent(ctx, LembreteReceiver::class.java).setAction("br.maxymus.agenda.REAGENDAR"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, proximo, pi)
    }

    /** Sem tela: busca no Google a semana atual e a seguinte e reagenda. Usado no boot e na manutenção. */
    fun reagendarEmSegundoPlano(ctx: Context) {
        val email = conta(ctx) ?: return
        escopo.launch {
            runCatching {
                val repo = GoogleAgenda(ctx.applicationContext, email)
                val hoje = LocalDate.now(FUSO); val seg = hoje.minusDays((hoje.dayOfWeek.value - 1).toLong())
                agendar(ctx.applicationContext, repo.semana(seg) + repo.semana(seg.plusWeeks(1)))
            }
        }
    }

    /** Mostra a notificação (modo notif) ou abre o alarme em tela cheia (modo alarme). */
    fun disparar(ctx: Context, i: Intent) {
        canais(ctx)
        val rot = i.getStringExtra("rot") ?: "Compromisso"; val ini = i.getIntExtra("ini", 0); val fim = i.getIntExtra("fim", 0)
        val modo = i.getStringExtra("modo") ?: "notif"; val minutos = i.getIntExtra("minutos", 0); val cat = Categoria.por(i.getStringExtra("cat"))
        val atrasado = System.currentTimeMillis() > i.getLongExtra("disparo", System.currentTimeMillis()) + 2 * 60_000
        val titulo = if (minutos == 0 || atrasado) "$rot começa agora" else "$rot em $minutos min"
        val corpo = "${hhmm(ini)}–${hhmm(fim)} · Agenda Semanal"
        val abrir = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (modo == "alarme") {
            val tela = Intent(ctx, AlarmeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtras(i)
            val cheia = PendingIntent.getActivity(ctx, i.getStringExtra("chave").hashCode(), tela, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(ctx, CANAL_ALARME).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(titulo).setContentText(corpo)
                .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM).setFullScreenIntent(cheia, true).setOngoing(true).setAutoCancel(false)
                .setContentIntent(cheia).build()
            nm.notify(i.getStringExtra("chave").hashCode(), n)
            runCatching { ctx.startActivity(tela) }
        } else {
            val adiar = PendingIntent.getBroadcast(ctx, (i.getStringExtra("chave") + "adiar").hashCode(), Intent(ctx, LembreteReceiver::class.java).setAction("br.maxymus.agenda.ADIAR").putExtras(i), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(ctx, CANAL_NOTIF).setSmallIcon(android.R.drawable.ic_menu_my_calendar).setContentTitle(titulo).setContentText(corpo)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT).setCategory(NotificationCompat.CATEGORY_REMINDER).setContentIntent(abrir).setAutoCancel(true)
                .addAction(0, "Abrir", abrir).addAction(0, "Adiar 5 min", adiar).build()
            nm.notify(i.getStringExtra("chave").hashCode(), n)
        }
    }

    /** Alarme não atendido (2 min sem resposta): troca por notificação silenciosa com "Abrir". */
    fun naoAtendido(ctx: Context, i: Intent) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val abrir = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CANAL_NOTIF).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Alarme não atendido: ${i.getStringExtra("rot") ?: ""}")
            .setContentText("${hhmm(i.getIntExtra("ini", 0))}–${hhmm(i.getIntExtra("fim", 0))} · Agenda Semanal").setSilent(true).setContentIntent(abrir).setAutoCancel(true).addAction(0, "Abrir", abrir).build()
        nm.notify((i.getStringExtra("chave") ?: "").hashCode(), n)
    }

    /** Adia um alarme em N minutos (reagenda o mesmo intent). */
    fun adiar(ctx: Context, i: Intent, minutos: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val novo = Intent(i).setClass(ctx, LembreteReceiver::class.java).setAction("br.maxymus.agenda.LEMBRETE").putExtra("minutos", 0).putExtra("rot", (i.getStringExtra("rot") ?: "") )
        val pi = PendingIntent.getBroadcast(ctx, (i.getStringExtra("chave") + "adiado").hashCode(), novo, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val quando = System.currentTimeMillis() + minutos * 60_000L
        if (exatoPermitido(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi)
    }
}

class LembreteReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        when (i.action) {
            "br.maxymus.agenda.LEMBRETE" -> Lembretes.disparar(ctx, i)
            "br.maxymus.agenda.ADIAR" -> { (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel((i.getStringExtra("chave") ?: "").hashCode()); Lembretes.adiar(ctx, i, 5) }
            "br.maxymus.agenda.REAGENDAR", Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, "android.intent.action.TIME_SET", "android.intent.action.TIMEZONE_CHANGED" -> Lembretes.reagendarEmSegundoPlano(ctx)
        }
    }
}
