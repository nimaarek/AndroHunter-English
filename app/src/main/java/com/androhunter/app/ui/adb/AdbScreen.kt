package com.androhunter.app.ui.adb

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androhunter.app.ui.common.HunterCard
import com.androhunter.app.ui.common.HunterTopBar
import com.androhunter.app.ui.theme.*
import kotlinx.coroutines.*

@Composable
fun AdbScreen(onBack: () -> Unit) {
    var pairIp      by remember { mutableStateOf("192.168.1.") }
    var pairPort    by remember { mutableStateOf("") }
    var pairCode    by remember { mutableStateOf("") }
    var connectIp   by remember { mutableStateOf("192.168.1.") }
    var connectPort by remember { mutableStateOf("5555") }
    val output      = remember { mutableStateListOf<String>() }
    val scope       = rememberCoroutineScope()

    fun run(cmd: String) {
        output.add(0, "$ $cmd")
        scope.launch(Dispatchers.IO) {
            try {
                val proc   = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
                val stdout = proc.inputStream.bufferedReader().readText()
                val stderr = proc.errorStream.bufferedReader().readText()
                proc.waitFor()
                withContext(Dispatchers.Main) {
                    if (stdout.isNotBlank()) output.add(0, stdout.trim())
                    if (stderr.isNotBlank()) output.add(0, "ERR: ${stderr.trim()}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { output.add(0, "ERROR: ${e.message}") }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(HunterBg).verticalScroll(rememberScrollState())) {
        HunterTopBar("ADB MANAGER", onBack)

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Pair section
            HunterCard {
                Text("ADB WIRELESS PAIR", color = HunterGreen, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                HunterInput("IP Address", pairIp) { pairIp = it }
                Spacer(Modifier.height(6.dp))
                HunterInput("Port", pairPort) { pairPort = it }
                Spacer(Modifier.height(6.dp))
                HunterInput("Pair Code", pairCode) { pairCode = it }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick  = { run("adb pair $pairIp:$pairPort $pairCode") },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = HunterGreen),
                    shape    = RoundedCornerShape(4.dp)
                ) {
                    Text("[ PAIR ]", color = HunterBg, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }

            // Connect section
            HunterCard {
                Text("ADB CONNECT", color = HunterBlue, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                HunterInput("IP Address", connectIp) { connectIp = it }
                Spacer(Modifier.height(6.dp))
                HunterInput("Port", connectPort) { connectPort = it }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick  = { run("adb connect $connectIp:$connectPort") },
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.buttonColors(containerColor = HunterBlue),
                        shape    = RoundedCornerShape(4.dp)
                    ) { Text("[ CONNECT ]", color = HunterBg, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
                    Button(
                        onClick  = { run("adb disconnect $connectIp:$connectPort") },
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.buttonColors(containerColor = HunterRed),
                        shape    = RoundedCornerShape(4.dp)
                    ) { Text("[ DISC ]", color = HunterBg, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
                }
            }

            // Quick commands
            HunterCard {
                Text("QUICK COMMANDS", color = HunterYellow, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                val cmds = listOf(
                    "adb devices"               to "Connected devices",
                    "adb shell id"              to "User info",
                    "adb shell getprop"         to "System properties",
                    "adb shell pm list packages" to "Installed packages",
                    "adb shell netstat -an"     to "Open ports",
                    "adb shell ps"              to "Running processes",
                    "adb logcat -d -v brief"   to "Recent logcat",
                )
                cmds.forEach { (cmd, desc) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(cmd,  color = HunterGreen,   fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(desc, color = HunterTextDim, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        }
                        IconButton(onClick = { run(cmd) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = HunterGreen)
                        }
                    }
                    Divider(color = HunterBorder, thickness = 0.5.dp)
                }
            }

            // Output
            if (output.isNotEmpty()) {
                HunterCard {
                    Text("OUTPUT", color = HunterTextDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    output.take(30).forEach { line ->
                        Text(
                            line,
                            color      = if (line.startsWith("ERR") || line.startsWith("ERROR")) HunterRed else HunterGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HunterInput(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value         = value,
        onValueChange = onChange,
        label         = { Text(label, fontFamily = FontFamily.Monospace, color = HunterTextDim, fontSize = 11.sp) },
        modifier      = Modifier.fillMaxWidth(),
        singleLine    = true,
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = HunterGreen,
            unfocusedBorderColor = HunterBorder,
            focusedTextColor     = HunterGreen,
            unfocusedTextColor   = HunterGreen,
            cursorColor          = HunterGreen,
            focusedLabelColor    = HunterGreen,
        ),
        shape = RoundedCornerShape(4.dp)
    )
}
