package app.revanced.manager.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.revanced.manager.R

@Composable
fun NotificationCard(
    isWarning: Boolean = false,
    title: String? = null,
    text: String,
    icon: ImageVector,
    actions: (@Composable () -> Unit)?
) {
    val color =
        if (isWarning) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimaryContainer

    NotificationCardInstance(isWarning = isWarning) {
        Column(
            modifier = Modifier.padding(if (title != null) 20.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (title != null) {
                Icon(
                    modifier = Modifier.size(36.dp),
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = color,
                    )
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                    )
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                    )
                }
            }
            actions?.invoke()
        }
    }
}

@Composable
fun NotificationCard(
    isWarning: Boolean = false,
    title: String? = null,
    text: String,
    icon: ImageVector,
    onDismiss: (() -> Unit)? = null,
    primaryAction: (() -> Unit)? = null
) {
    val color =
        if (isWarning) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimaryContainer

    NotificationCardInstance(isWarning = isWarning, onClick = primaryAction) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                modifier = Modifier.size(if (title != null) 36.dp else 24.dp),
                imageVector = icon,
                contentDescription = null,
                tint = color,
            )
            if (title != null) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = color,
                    )
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                    )
                }
            } else {
                Text(
                    modifier = Modifier.weight(1f),
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                )
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = color,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationCardInstance(
    isWarning: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors =
        CardDefaults.cardColors(containerColor = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer)
    val modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
        .clip(RoundedCornerShape(24.dp))

    if (onClick != null) {
        Card(
            onClick = onClick,
            colors = colors,
            modifier = modifier
        ) {
            content()
        }
    } else {
        Card(
            colors = colors,
            modifier = modifier,
        ) {
            content()
        }
    }
}