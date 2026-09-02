package com.controldesk.app.ui.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.controldesk.app.panel.PANEL_ADMIN_PIN
import com.controldesk.app.theme.DeskColors

@Composable
fun AdminPinDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Acesso administrativo") },
        text = {
            Column {
                Text(
                    "Digite o PIN para abrir as configurações de pareamento.",
                    color = DeskColors.TextMuted,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter(Char::isDigit).take(6)
                        error = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("PIN") },
                    isError = error,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(12.dp),
                )
                if (error) {
                    Spacer(Modifier.height(8.dp))
                    Text("PIN incorreto.", color = DeskColors.Danger)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (pin == PANEL_ADMIN_PIN) {
                        onSuccess()
                    } else {
                        error = true
                    }
                },
            ) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}
