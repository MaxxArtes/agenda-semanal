package br.maxymus.agenda

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Alarme em tela cheia: som de despertador, vibração, Parar e Adiar 5 min. Para sozinho depois de 2 minutos. */
class AlarmeActivity : ComponentActivity() {
    private var som: MediaPlayer? = null
    private val parada = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val i = intent
        val rot = i.getStringExtra("rot") ?: "Compromisso"; val ini = i.getIntExtra("ini", 0); val fim = i.getIntExtra("fim", 0)
        val cat = Categoria.por(i.getStringExtra("cat")); val data = runCatching { LocalDate.parse(i.getStringExtra("data")) }.getOrNull()
        val minutos = i.getIntExtra("minutos", 0)
        tocar()
        parada.postDelayed({ silenciar(); Lembretes.naoAtendido(this, i); finish() }, 2 * 60_000L)
        setContent {
            Column(modifier = Modifier.fillMaxSize().background(Fundo).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                val agora = java.time.LocalTime.now(java.time.ZoneId.of("America/Cuiaba"))
                Text(agora.format(DateTimeFormatter.ofPattern("HH:mm")), color = Tinta, fontSize = 64.sp, lineHeight = 72.sp, fontWeight = FontWeight.SemiBold)
                Text("Começa às ${hhmm(ini)} · termina às ${hhmm(fim)}" + (data?.let { " · " + it.format(DateTimeFormatter.ofPattern("EEEE, dd/MM", java.util.Locale("pt", "BR"))) } ?: ""), color = Tinta2, fontSize = 14.sp)
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(cat.cor)); Spacer(Modifier.width(8.dp))
                    Text(cat.nome, color = Tinta2, fontSize = 14.sp)
                }
                Text(rot, color = Tinta, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(48.dp))
                Button(onClick = { fechar() }, modifier = Modifier.fillMaxWidth().height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = Acento, contentColor = Fundo)) { Text("Parar", fontSize = 18.sp) }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { Lembretes.adiar(this@AlarmeActivity, i, 5); fechar() }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("Adiar 5 min", fontSize = 18.sp, color = Tinta) }
            }
        }
    }

    private fun tocar() {
        runCatching {
            som = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@AlarmeActivity, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                isLooping = true; prepare(); start()
            }
        }
        runCatching {
            val v = if (Build.VERSION.SDK_INT >= 31) (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator else @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
        }
    }

    private fun silenciar() {
        runCatching { som?.stop(); som?.release() }; som = null
        runCatching { val v = if (Build.VERSION.SDK_INT >= 31) (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator else @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator; v.cancel() }
    }

    private fun fechar() {
        silenciar()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel((intent.getStringExtra("chave") ?: "").hashCode())
        finish()
    }

    override fun onDestroy() { parada.removeCallbacksAndMessages(null); silenciar(); super.onDestroy() }
}
