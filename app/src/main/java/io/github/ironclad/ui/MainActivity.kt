package io.github.ironclad.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ironclad.admin.IronDeviceAdminReceiver
import io.github.ironclad.gauntlet.GauntletChallenge
import io.github.ironclad.gauntlet.QuarantineTimer
import io.github.ironclad.service.AntiTamperAccessibilityService
import io.github.ironclad.service.IronVpnService
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var timer: QuarantineTimer

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startService(Intent(this, IronVpnService::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        timer = QuarantineTimer(this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF64B5F6),
                    secondary = Color(0xFF81C784),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        context = this,
                        timer = timer,
                        onEnableVpn = { requestVpnPermission() },
                        onOpenAccessibilitySettings = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    )
                }
            }
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startService(Intent(this, IronVpnService::class.java))
        }
    }
}

@Composable
fun MainScreen(
    context: Context,
    timer: QuarantineTimer,
    onEnableVpn: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    var timerState by remember { mutableStateOf(timer.getState()) }
    var remainingQuarantine by remember { mutableStateOf(timer.getRemainingQuarantineMillis()) }
    var remainingGauntlet by remember { mutableStateOf(timer.getRemainingGauntletMillis()) }

    val isDeviceOwner = remember { IronDeviceAdminReceiver.isDeviceOwner(context) }
    var isAccessibilityEnabled by remember {
        mutableStateOf(AntiTamperAccessibilityService.isServiceRunning(context))
    }
    var isVpnRunning by remember { mutableStateOf(IronVpnService.isRunning) }

    // Real-time ticking ticker
    LaunchedEffect(Unit) {
        while (true) {
            timerState = timer.getState()
            remainingQuarantine = timer.getRemainingQuarantineMillis()
            remainingGauntlet = timer.getRemainingGauntletMillis()
            isAccessibilityEnabled = AntiTamperAccessibilityService.isServiceRunning(context)
            isVpnRunning = IronVpnService.isRunning
            delay(1000L)
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "🛡️ IRONCLAD",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 2.sp
        )
        Text(
            text = "Single-Player Adult Content Blocker",
            fontSize = 13.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Tier Indicator Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isDeviceOwner) "TIER 2: IRON FORTRESS" else "TIER 1: QUICK SHIELD",
                        fontWeight = FontWeight.Bold,
                        color = if (isDeviceOwner) Color(0xFF81C784) else Color(0xFFFFB74D)
                    )
                    Text(
                        text = if (isDeviceOwner) "ADB Enforced" else "Zero-PC",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isDeviceOwner) {
                    Text(
                        text = "Hardware-level Device Owner active. Safe Mode and uninstall blocked at the OS kernel level.",
                        fontSize = 13.sp,
                        color = Color.LightGray
                    )
                } else {
                    Text(
                        text = "Standard anti-tamper active. VPN and Accessibility shields prevent in-the-moment disablement.",
                        fontSize = 13.sp,
                        color = Color.LightGray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Shield Controls (if not Device Owner)
        if (!isDeviceOwner) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Setup Shields", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "DNS Filter VPN: ${if (isVpnRunning) "Active" else "Inactive"}")
                        Button(
                            onClick = onEnableVpn,
                            enabled = !isVpnRunning
                        ) {
                            Text(if (isVpnRunning) "Enabled" else "Enable")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Anti-Tamper Shield: ${if (isAccessibilityEnabled) "Active" else "Inactive"}")
                        Button(
                            onClick = onOpenAccessibilitySettings,
                            enabled = !isAccessibilityEnabled
                        ) {
                            Text(if (isAccessibilityEnabled) "Enabled" else "Enable")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Quarantine & Unlock Protocol Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Quarantine & Unlock",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                when (timerState) {
                    QuarantineTimer.State.LOCKED -> {
                        Text(
                            text = "Protection is locked. There is NO bypass key and no password.",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { timer.initiateQuarantine() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))
                        ) {
                            Text("Initiate 24-Hour Unlock Request")
                        }
                    }
                    QuarantineTimer.State.QUARANTINE_COUNTDOWN -> {
                        val hours = remainingQuarantine / (1000 * 60 * 60)
                        val minutes = (remainingQuarantine / (1000 * 60)) % 60
                        val seconds = (remainingQuarantine / 1000) % 60

                        Text(
                            text = "COOLING-OFF COUNTDOWN",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB74D)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = String.format("%02d:%02d:%02d", hours, minutes, seconds),
                            fontSize = 36.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Urges are temporary. Protection remains strictly active until the timer completes.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = { timer.resetToLocked() }) {
                            Text("Cancel Unlock Request & Relock")
                        }
                    }
                    QuarantineTimer.State.GAUNTLET_WINDOW -> {
                        GauntletView(
                            remainingMillis = remainingGauntlet,
                            onPassed = { timer.markGauntletPassed() },
                            onFailed = { timer.resetToLocked() }
                        )
                    }
                    QuarantineTimer.State.GRACE_UNLOCKED -> {
                        Text(
                            text = "🔓 TEMPORARY 1-HOUR UNLOCK ACTIVE",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF81C784)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "You may adjust settings or uninstall now if desired. Ironclad will automatically relock when the hour expires.",
                            fontSize = 13.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { timer.resetToLocked() }) {
                            Text("Relock Protection Now")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ADB Instructions (if Tier 1)
        if (!isDeviceOwner) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF262626))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⚡ Upgrade to Tier 2 (Iron Fortress)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Connect phone to PC once with USB debugging and run:",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "adb shell dpm set-device-owner io.github.ironclad/.admin.IronDeviceAdminReceiver",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF64B5F6)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun GauntletView(
    remainingMillis: Long,
    onPassed: () -> Unit,
    onFailed: () -> Unit
) {
    val challenge = remember { GauntletChallenge(120) }
    var inputChar by remember { mutableStateOf("") }
    val progress = challenge.currentProgress
    val target = challenge.targetString

    val minutes = (remainingMillis / (1000 * 60)) % 60
    val seconds = (remainingMillis / 1000) % 60

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "THE PAIN GAUNTLET",
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE57373),
            fontSize = 16.sp
        )
        Text(
            text = "Window closes in: ${String.format("%02d:%02d", minutes, seconds)}",
            fontSize = 13.sp,
            color = Color.LightGray
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Type the 120-character sequence below. Backspace is disabled; any typo immediately resets progress to zero.",
            fontSize = 12.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Progress percentage
        LinearProgressIndicator(
            progress = { challenge.getProgressPercent() },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = Color(0xFF81C784)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Target box showing progress vs remaining
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .border(1.dp, Color(0xFF424242), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text = "Typed: ${challenge.currentProgress.length} / 120 (Mistakes: ${challenge.totalMistakes})",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = target,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = inputChar,
            onValueChange = { newText ->
                if (newText.length > inputChar.length) {
                    val addedChar = newText.last()
                    challenge.appendCharacter(addedChar)
                    if (challenge.isCompleted()) {
                        onPassed()
                    }
                }
                inputChar = ""
            },
            label = { Text("Type next: '${challenge.getNextExpectedCharacter() ?: ""}'") },
            placeholder = { Text("Tap keyboard here...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
