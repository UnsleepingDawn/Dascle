package com.fitplan.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fitplan.app.R
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.presentation.core.util.secondaryItemAlpha
import com.fitplan.ui.common.DialogButtonColumn
import com.fitplan.ui.common.DialogPrimaryButton
import com.fitplan.ui.common.ReleaseNotesContent
import com.fitplan.ui.common.ReleaseNotesMaxHeight
import com.fitplan.updater.AppRelease
import com.fitplan.updater.parseReleaseNotes

/**
 * 有新版时的弹窗：标题写版本号，正文是更新说明，底部两个按钮。
 *
 * 「更新说明」来自 GitHub Release 的正文，可能是空的，也可能很长：先按
 * [parseReleaseNotes] 拆成「新增 / 调整 / 修复」分节并按重要度重排，再限高滚动；
 * 一句说明都没写时给一句兜底，别让弹窗空着。
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
                val sections = remember(release.notes) { parseReleaseNotes(release.notes) }
                if (sections.isEmpty()) {
                    Text(text = stringResource(R.string.update_dialog_message_empty))
                } else {
                    ReleaseNotesContent(
                        sections = sections,
                        modifier = Modifier
                            .heightIn(max = ReleaseNotesMaxHeight)
                            .verticalScroll(rememberScrollState()),
                    )
                }

                Text(
                    text = stringResource(R.string.update_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.secondaryItemAlpha(),
                )

                DialogButtonColumn {
                    DialogPrimaryButton(
                        text = stringResource(R.string.update_dialog_download),
                        onClick = onDownload,
                    )
                    DialogPrimaryButton(
                        text = stringResource(R.string.update_dialog_later),
                        onClick = onDismiss,
                    )
                }
            }
        },
        confirmButton = {},
    )
}
