package com.example.timewise

import android.content.Intent
import androidx.compose.ui.draw.scale
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timewise.ui.theme.TimewiseTheme
import com.example.timewise.R

/**
 * Stays a separate Activity so Android can launch it over foreign apps.
 * All UI is Compose. No XML.
 */
class BlockingActivity : ComponentActivity() {

    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = AppPreferences(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val blockedPackage = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)
            ?: AppMonitorService.instance?.lastBlockedPackage

        val message = pickMessage(blockedPackage)
        val delayMs = prefs.delayMillis

        setContent {
            TimewiseTheme(darkTheme = true) {
                BlockingScreen(
                    message = message,
                    delayMs = delayMs,
                    onResist = { recordAndClose(resisted = true) },
                    onContinue = {
                        recordAndClose(resisted = false)
                        openApp(blockedPackage)
                    }
                )
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        recordAndClose(resisted = true)
    }

    private fun recordAndClose(resisted: Boolean) {
        prefs.recordInterception(resisted)

        if (resisted) {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
        }

        finishAffinity()
    }

    private fun openApp(packageName: String?) {
        if (packageName == null) return

        AppMonitorService.pause(this, packageName)

        Handler(Looper.getMainLooper()).postDelayed({
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(this)
            }
        }, 300)
    }

    private fun pickMessage(pkg: String?): String {
        val p = pkg?.lowercase() ?: ""

        val social = listOf(
            "instagram",
            "tiktok",
            "facebook",
            "twitter",
            "snapchat",
            "reddit"
        )

        val video = listOf(
            "youtube",
            "netflix",
            "twitch",
            "disney",
            "hulu",
            "prime"
        )

        return when {
            social.any { p.contains(it) } -> SOCIAL_MESSAGES.random()
            video.any { p.contains(it) } -> VIDEO_MESSAGES.random()
            else -> GENERIC_MESSAGES.random()
        }
    }

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"

        val SOCIAL_MESSAGES = listOf(
            "Scrolling won't fill the silence.\nWhat actually matters right now?",
            "You opened this without thinking.\nThat's the trap — notice it.",
            "Everyone there is also mindlessly scrolling.\nBe the one who isn't.",
        )

        val VIDEO_MESSAGES = listOf(
            "One more episode is rarely just one.\nIs this what you planned?",
            "Your future self is watching.\nWhat would they want you to do?",
        )

        val GENERIC_MESSAGES = listOf(
            "Take a breath.\nWas this intentional, or just habit?",
            "You marked this app as a distraction.\nPast-you was right.",
            "What were you doing before you reached for your phone?",
        )
    }
}

// ── Compose UI ────────────────────────────────────────────────────────────────

@Composable
fun BlockingScreen(
    message: String,
    delayMs: Long,
    onResist: () -> Unit,
    onContinue: () -> Unit,
) {
    var millisLeft by remember { mutableLongStateOf(delayMs) }
    var countdownDone by remember { mutableStateOf(delayMs <= 0L) }

    DisposableEffect(delayMs) {
        if (delayMs <= 0L) {
            countdownDone = true
            millisLeft = 0L
            onDispose { }
        } else {
            val timer = object : CountDownTimer(delayMs, 100) {
                override fun onTick(remaining: Long) {
                    millisLeft = remaining
                }

                override fun onFinish() {
                    millisLeft = 0L
                    countdownDone = true
                }
            }.start()

            onDispose {
                timer.cancel()
            }
        }
    }

    val progress by animateFloatAsState(
        targetValue = if (delayMs > 0L) millisLeft.toFloat() / delayMs else 0f,
        animationSpec = tween(durationMillis = 100, easing = LinearEasing),
        label = "countdown"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F14)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color(0xFF6C63FF))
                .align(Alignment.TopCenter)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color(0xFF6C63FF), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.timewiseicon_foreground),
                    contentDescription = "Timewise icon",
                    modifier = Modifier.size(72.dp).scale(1.45f)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = if (countdownDone) {
                    "Ready"
                } else {
                    "Opening in ${(millisLeft / 1000) + 1}s"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = Color(0xFF6C63FF),
                trackColor = Color(0xFF2A2A38),
            )

            Spacer(Modifier.height(32.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFE0E0E8),
                textAlign = TextAlign.Center,
                lineHeight = 26.sp
            )

            if (countdownDone) {
                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Do you really want to open this?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9B9BA8),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(40.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onResist,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6C63FF)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Stay focused", color = Color.White)
                }

                OutlinedButton(
                    onClick = onContinue,
                    enabled = countdownDone,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF9B9BA8),
                        disabledContentColor = Color(0xFF4A4A58)
                    )
                ) {
                    Text("Open anyway")
                }
            }
        }
    }
}