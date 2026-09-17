package com.fitplan.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fitplan.app.R
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.presentation.core.util.secondaryItemAlpha
import com.fitplan.updater.AppRelease

/**
 * 有新版时的弹窗：标题写版本号，正文是更新说明，底部两个按钮。
 *
 * 「更新说明」来自 GitHub Release 的正文，可能是空的，也可能很长，
 * 所以空的时候给一句兜底，长的时候限高滚动，别把弹窗撑爆。
 */
@Composable
fun UpdateDialog(
    release: AppRelease,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.update_dialog_title, release.version)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)) {
                if (release.notes.isBlank()) {
                    Text(text = stringResource(R.string.update_dialog_message_empty))
                } else {
                    Text(
                        text = release.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .heightIn(max = NotesMaxHeight)
                            .verticalScroll(rememberScrollState()),
                    )
                }

                Text(
                    text = stringResource(R.string.update_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.secondaryItemAlpha(),
                )

                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.update_dialog_download))
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.update_dialog_later))
                    }
                }
            }
        },
        confirmButton = {},
    )
}

private val NotesMaxHeight = 240.dp
