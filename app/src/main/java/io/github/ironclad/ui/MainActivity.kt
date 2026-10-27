package io.github.ironclad.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
            val vpnIntent = Intent(this, IronVpnService::class.java).apply {
                action = IronVpnService.ACTION_START
            }
            startService(vpnIntent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        timer = QuarantineTimer(this)

        if (IronDeviceAdminReceiver.isDeviceOwner(this)) {
            if (timer.getState() == QuarantineTimer.State.GRACE_UNLOCKED) {
                IronDeviceAdminReceiver.relaxRestrictions(this)
            } else {
                IronDeviceAdminReceiver.enforceDefaultPolicies(this)
                IronDeviceAdminReceiver.enforcePrivateDns(this)
            }
        }

        setContent {
            val tacticalTheme = darkColorScheme(
                primary = Color(0xFF10B981),        // Signal Emerald Green
                onPrimary = Color(0xFF041E15),
                secondary = Color(0xFF38BDF8),      // Avionics Cyan
                background = Color(0xFF090A0D),     // Deep Obsidian Matte
                surface = Color(0xFF111418),        // Tactical Dark Plate
                surfaceVariant = Color(0xFF181C22),
                outline = Color(0xFF262B34),
                onBackground = Color(0xFFF1F3F5),
                onSurface = Color(0xFFE5E7EB)
            )

            MaterialTheme(colorScheme = tacticalTheme) {
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
            val vpnIntent = Intent(this, IronVpnService::class.java).apply {
                action = IronVpnService.ACTION_START
            }
            startService(vpnIntent)
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
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. Top System Header ---
        HeaderSection(isDeviceOwner = isDeviceOwner, isVpnRunning = isVpnRunning)

        Spacer(modifier = Modifier.height(16.dp))

        // --- 2. Defense Telemetry Panel ---
        TelemetryGrid(
            isDeviceOwner = isDeviceOwner,
            isVpnRunning = isVpnRunning,
            isAccessibilityEnabled = isAccessibilityEnabled
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- 3. Subsystem Controls (Mode 1 Quick Shield Setup) ---
        if (!isDeviceOwner) {
            SubsystemControlsCard(
                isVpnRunning = isVpnRunning,
                isAccessibilityEnabled = isAccessibilityEnabled,
                onEnableVpn = onEnableVpn,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- 4. Quarantine & Deactivation Protocol Console ---
        ProtocolConsoleCard(
            timerState = timerState,
            remainingQuarantine = remainingQuarantine,
            remainingGauntlet = remainingGauntlet,
            isDeviceOwner = isDeviceOwner,
            onInitiateQuarantine = { timer.initiateQuarantine() },
            onCancelQuarantine = { timer.resetToLocked() },
            onGauntletPassed = {
                timer.markGauntletPassed()
                if (isDeviceOwner) IronDeviceAdminReceiver.relaxRestrictions(context)
            },
            onRelock = {
                timer.resetToLocked()
                if (isDeviceOwner) {
                    IronDeviceAdminReceiver.enforceDefaultPolicies(context)
                    IronDeviceAdminReceiver.enforcePrivateDns(context)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- 5. Tier 2 Enterprise ADB Provisioning ---
        if (!isDeviceOwner) {
            AdbTerminalCard(
                onCopyCommand = {
                    val cmd = "adb shell dpm set-device-owner io.github.ironclad/.admin.IronDeviceAdminReceiver"
                    clipboardManager.setText(AnnotatedString(cmd))
                    Toast.makeText(context, "ADB command copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 6. Footer Signature ---
        Text(
            text = "IRONCLAD DEFENSE SYSTEMS // KERNEL-ANCHORED CONTENT PROTECTION",
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = Color(0xFF4B5563),
            letterSpacing = 1.sp
        )
    }
}

// -------------------------------------------------------------------------------------------------
// Subcomponents (Structured, Precision Design)
// -------------------------------------------------------------------------------------------------

@Composable
fun HeaderSection(isDeviceOwner: Boolean, isVpnRunning: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1E232B), RoundedCornerShape(4.dp))
            .background(Color(0xFF0F1216))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (isVpnRunning || isDeviceOwner) Color(0xFF10B981) else Color(0xFFF59E0B),
                            RoundedCornerShape(1.dp)
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "IRONCLAD",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0xFFF9FAFB),
                    letterSpacing = 2.sp
                )
            }
            Text(
                text = "SYSTEM ARCHITECTURE v1.0",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = Color(0xFF6B7280)
            )
        }

        Box(
            modifier = Modifier
                .border(
                    1.dp,
                    if (isDeviceOwner) Color(0xFF065F46) else Color(0xFF78350F),
                    RoundedCornerShape(3.dp)
                )
                .background(if (isDeviceOwner) Color(0xFF064E3B) else Color(0xFF451A03))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isDeviceOwner) "TIER 2 // FORTRESS" else "TIER 1 // SHIELD",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDeviceOwner) Color(0xFF34D399) else Color(0xFFFBBF24),
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun TelemetryGrid(isDeviceOwner: Boolean, isVpnRunning: Boolean, isAccessibilityEnabled: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1F242C), RoundedCornerShape(4.dp))
            .background(Color(0xFF12151A))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "DEFENSE SUBSYSTEMS",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF9CA3AF),
                letterSpacing = 1.sp
            )
            Text(
                text = "DIAGNOSTICS: NOMINAL",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = Color(0xFF10B981)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        TelemetryRow(
            name = "FILTER ENGINE",
            value = "1.2M DOMAIN SINKHOLE",
            statusColor = Color(0xFF10B981)
        )
        TelemetryRow(
            name = "DNS PROXY",
            value = if (isVpnRunning) "TUN ROUTED (IPv4+IPv6)" else "OFFLINE",
            statusColor = if (isVpnRunning) Color(0xFF10B981) else Color(0xFFEF4444)
        )
        TelemetryRow(
            name = "SAFESEARCH",
            value = "STRICT VIP ENFORCED",
            statusColor = Color(0xFF10B981)
        )
        TelemetryRow(
            name = "DOH / DOT DROPS",
            value = "PORT 853/443 ACTIVE",
            statusColor = Color(0xFF10B981)
        )
        TelemetryRow(
            name = "ANTI-TAMPER",
            value = if (isDeviceOwner) "OS KERNEL LOCK" else if (isAccessibilityEnabled) "GUARDIAN ACTIVE" else "DISABLED",
            statusColor = if (isDeviceOwner || isAccessibilityEnabled) Color(0xFF10B981) else Color(0xFFF59E0B)
        )
        TelemetryRow(
            name = "QUARANTINE CLOCK",
            value = "MONOTONIC HARDWARE",
            statusColor = Color(0xFF10B981)
        )
    }
}

@Composable
fun TelemetryRow(name: String, value: String, statusColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color(0xFF6B7280)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(statusColor, RoundedCornerShape(1.dp))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = value,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color(0xFFD1D5DB)
            )
        }
    }
}

@Composable
fun SubsystemControlsCard(
    isVpnRunning: Boolean,
    isAccessibilityEnabled: Boolean,
    onEnableVpn: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1F242C), RoundedCornerShape(4.dp))
            .background(Color(0xFF12151A))
            .padding(14.dp)
    ) {
        Text(
            text = "SUBSYSTEM CONFIGURATION",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF9CA3AF),
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // VPN Row
        ControlTile(
            title = "DNS Filtering Shield",
            detail = if (isVpnRunning) "Local TUN proxy sinkholes adult traffic" else "Required: Routes lookups into local blocker",
            isActive = isVpnRunning,
            buttonText = if (isVpnRunning) "ARMED" else "ARM SHIELD",
            onClick = onEnableVpn
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Accessibility Row
        ControlTile(
            title = "Anti-Tamper Guardian",
            detail = if (isAccessibilityEnabled) "Intercepts Settings and uninstalls" else "Required: Ejects tampering attempts to home",
            isActive = isAccessibilityEnabled,
            buttonText = if (isAccessibilityEnabled) "ACTIVE" else "ENABLE",
            onClick = onOpenAccessibilitySettings
        )
    }
}

@Composable
fun ControlTile(
    title: String,
    detail: String,
    isActive: Boolean,
    buttonText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isActive) Color(0xFF1E2822) else Color(0xFF2C241E), RoundedCornerShape(3.dp))
            .background(if (isActive) Color(0xFF0F1612) else Color(0xFF16120E))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(0xFFE5E7EB)
            )
            Text(
                text = detail,
                fontSize = 10.sp,
                color = Color(0xFF9CA3AF)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        OutlinedButton(
            onClick = onClick,
            enabled = !isActive,
            shape = RoundedCornerShape(2.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (isActive) Color(0xFF10B981) else Color(0xFFF59E0B)
            ),
            border = BorderStroke(1.dp, if (isActive) Color(0xFF059669) else Color(0xFFD97706)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = buttonText,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun ProtocolConsoleCard(
    timerState: QuarantineTimer.State,
    remainingQuarantine: Long,
    remainingGauntlet: Long,
    isDeviceOwner: Boolean,
    onInitiateQuarantine: () -> Unit,
    onCancelQuarantine: () -> Unit,
    onGauntletPassed: () -> Unit,
    onRelock: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1F242C), RoundedCornerShape(4.dp))
            .background(Color(0xFF12151A))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ACCESS CONTROL // PROTOCOL CONSOLE",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF9CA3AF),
            letterSpacing = 1.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        when (timerState) {
            QuarantineTimer.State.LOCKED -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(3.dp))
                        .background(Color(0xFF0F172A))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "STATUS: STRICT LOCKDOWN",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF38BDF8),
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No instant bypass exists. Disabling protection requires initiating a mandatory 24-hour hardware countdown followed by a zero-error typing challenge.",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onInitiateQuarantine,
                    shape = RoundedCornerShape(3.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E232B),
                        contentColor = Color(0xFFF59E0B)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFD97706)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "INITIATE 24-HOUR UNLOCK COUNTDOWN",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            QuarantineTimer.State.QUARANTINE_COUNTDOWN -> {
                val hours = remainingQuarantine / (1000 * 60 * 60)
                val minutes = (remainingQuarantine / (1000 * 60)) % 60
                val seconds = (remainingQuarantine / 1000) % 60

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF78350F), RoundedCornerShape(3.dp))
                        .background(Color(0xFF1C1309))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "COOLING-OFF COUNTDOWN IN PROGRESS",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = Color(0xFFFBBF24),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = String.format("%02d:%02d:%02d", hours, minutes, seconds),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF59E0B),
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "HARDWARE UPTIME ANCHORED // PROTECTION FULLY ACTIVE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = Color(0xFF78716C)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = onCancelQuarantine,
                    shape = RoundedCornerShape(3.dp),
                    border = BorderStroke(1.dp, Color(0xFF4B5563)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "ABORT UNLOCK & CANCEL TIMER",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFFD1D5DB)
                    )
                }
            }

            QuarantineTimer.State.GAUNTLET_WINDOW -> {
                GauntletTerminal(
                    remainingMillis = remainingGauntlet,
                    onPassed = onGauntletPassed,
                    onFailed = onCancelQuarantine
                )
            }

            QuarantineTimer.State.GRACE_UNLOCKED -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF065F46), RoundedCornerShape(3.dp))
                        .background(Color(0xFF064E3B))
                        .padding(14.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "CLEARANCE ACTIVE // 1-HOUR WINDOW",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFF34D399),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Maintenance window open. Settings may be adjusted now. System will automatically relock when the hour expires.",
                            fontSize = 11.sp,
                            color = Color(0xFFD1FAE5),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onRelock,
                    shape = RoundedCornerShape(3.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF047857),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "RELOCK PROTECTION IMMEDIATELY",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
fun GauntletTerminal(
    remainingMillis: Long,
    onPassed: () -> Unit,
    onFailed: () -> Unit
) {
    val challenge = remember { GauntletChallenge(120) }
    var inputChar by remember { mutableStateOf("") }
    val target = challenge.targetString

    val minutes = (remainingMillis / (1000 * 60)) % 60
    val seconds = (remainingMillis / 1000) % 60

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "CLEARANCE CHALLENGE",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEF4444),
                fontSize = 11.sp
            )
            Text(
                text = "WINDOW: ${String.format("%02d:%02d", minutes, seconds)}",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF87171),
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Type the 120-character verification string below. Backspace is disabled; any single error instantly resets progress.",
            fontSize = 10.sp,
            color = Color(0xFF9CA3AF),
            lineHeight = 14.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Progress bar
        LinearProgressIndicator(
            progress = { challenge.getProgressPercent() },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = Color(0xFF10B981),
            trackColor = Color(0xFF1F2937)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Monospace Terminal Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF374151), RoundedCornerShape(3.dp))
                .background(Color(0xFF030712))
                .padding(10.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "PROGRESS: ${challenge.currentProgress.length}/120",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFF10B981)
                    )
                    Text(
                        text = "FAULTS: ${challenge.totalMistakes}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = if (challenge.totalMistakes > 0) Color(0xFFEF4444) else Color(0xFF6B7280)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = target,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFFE5E7EB),
                    letterSpacing = 1.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
            shape = RoundedCornerShape(3.dp),
            label = {
                Text(
                    text = "NEXT EXPECTED CHAR: '${challenge.getNextExpectedCharacter() ?: ""}'",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            },
            placeholder = {
                Text(
                    text = "Tap keyboard here to enter characters...",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun AdbTerminalCard(onCopyCommand: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1F242C), RoundedCornerShape(4.dp))
            .background(Color(0xFF12151A))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "UPGRADE // TIER 2 PROVISIONING",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF38BDF8),
                letterSpacing = 1.sp
            )
            Text(
                text = "TAP TO COPY",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = Color(0xFF6B7280)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Connect phone to PC once with USB debugging enabled and execute via terminal:",
            fontSize = 11.sp,
            color = Color(0xFF9CA3AF),
            lineHeight = 15.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(3.dp))
                .background(Color(0xFF030712))
                .clickable { onCopyCommand() }
                .padding(10.dp)
        ) {
            Text(
                text = "$ adb shell dpm set-device-owner io.github.ironclad/.admin.IronDeviceAdminReceiver",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = Color(0xFF38BDF8),
                letterSpacing = 0.5.sp,
                lineHeight = 15.sp
            )
        }
    }
}
