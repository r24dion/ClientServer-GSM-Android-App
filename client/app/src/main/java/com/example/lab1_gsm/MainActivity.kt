package com.example.lab1_gsm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.lab1_gsm.ui.theme.Lab1_gsmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Lab1_gsmTheme {
                GsmScreen()
            }
        }
    }
}

@Composable
fun GsmScreen() {
    var imsi by remember { mutableStateOf("310260000000000") }
    var rand by remember { mutableStateOf("") }
    var sres by remember { mutableStateOf("") }
    var kc by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var encrypted by remember { mutableStateOf("") }

    val typography = MaterialTheme.typography
    val spacing = 8.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "GSM Authentication",
            style = typography.headlineMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            textAlign = TextAlign.Center
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                InfoRow("IMSI", imsi)
                InfoRow("RAND", rand)
                InfoRow("SRES", sres)
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = DividerDefaults.Thickness,
                    color = DividerDefaults.color
                )
                InfoRow("Status", status, isStatus = true)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                status = "REQUESTING RAND..."

                RequestServ.requestRand(imsi) { randResponse ->
                    rand = randResponse ?: run {
                        status = "ERROR: NO RAND"
                        return@requestRand
                    }

                    val kiHex = "00112233445566778899AABBCCDDEEFF"
                    val kiBytes = hexToBytes(kiHex)
                    val randBytes = hexToBytes(rand)

                    val result = Comp128.v1(kiBytes, randBytes)
                    val sresBytes = result.copyOfRange(0, 4)
                    val kcBytes = result.copyOfRange(4, 12)

                    sres = bytesToHex(sresBytes)
                    kc = bytesToHex(kcBytes)

                    status = "VERIFYING..."

                    RequestServ.verify(imsi, sres) { isValid, kcFromServer ->
                        if (isValid) {
                            status = "AUTH SUCCESS"
                            kc = kcFromServer ?: kc
                            FrameCounter.reset()
                        } else {
                            status = "AUTH FAILED"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Аутентификация")
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (status == "AUTH SUCCESS") {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Сообщение") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val frame = FrameCounter.nextFrame()
                    val cipher = A5.encrypt(message, kc, frame)
                    encrypted = cipher

                    RequestServ.sendMessage(imsi, cipher, frame) { response ->
                        encrypted = "Зашифрованный текст: $cipher"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Отправить")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = encrypted,
                    modifier = Modifier.padding(12.dp),
                    style = typography.bodyMedium
                )
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = "Пройдите аутентификацию, чтобы отправлять сообщения",
                    modifier = Modifier.padding(12.dp),
                    style = typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, isStatus: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.ifEmpty { "—" },
            style = MaterialTheme.typography.bodyMedium,
            color = if (isStatus) {
                when (value) {
                    "AUTH SUCCESS" -> MaterialTheme.colorScheme.primary
                    "AUTH FAILED" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            } else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isStatus) FontWeight.Bold else FontWeight.Normal
        )
    }
}

fun hexToBytes(hex: String): ByteArray {
    return hex.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

fun bytesToHex(bytes: ByteArray): String {
    return bytes.joinToString("") { "%02x".format(it) }
}

object FrameCounter {
    private const val MAX_FRAME = 1 shl 22 // 2^22
    private var frameNumber = 0

    fun nextFrame(): String {
        frameNumber = (frameNumber + 1) % MAX_FRAME
        return "%08x".format(frameNumber)
    }

    fun reset() {
        frameNumber = 0
    }
}