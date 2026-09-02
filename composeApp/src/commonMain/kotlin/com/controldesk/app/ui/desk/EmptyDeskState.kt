package com.controldesk.app.ui.desk

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controldesk.app.theme.DeskColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmptyDeskState(
    onPairHost: () -> Unit,
    onAdminAccessRequest: () -> Unit = onPairHost,
    panelMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .then(
                if (panelMode) {
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = onAdminAccessRequest,
                    )
                } else {
                    Modifier
                },
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Control Desk", color = DeskColors.TextPrimary, fontSize = 26.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            if (panelMode) {
                "Nenhum computador pareado. Toque e segure para configurar."
            } else {
                "Pareie Mac, Windows ou Linux na mesma rede e controle apps, volume e microfone."
            },
            color = DeskColors.TextMuted,
            fontSize = 15.sp,
        )
        if (!panelMode) {
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onPairHost,
                colors = ButtonDefaults.buttonColors(containerColor = DeskColors.Accent),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Parear computador")
            }
        }
    }
}
