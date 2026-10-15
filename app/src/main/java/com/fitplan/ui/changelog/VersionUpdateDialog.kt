package com.fitplan.ui.changelog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fitplan.app.R
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.presentation.core.util.secondaryItemAlpha
import com.fitplan.ui.common.DialogButtonColumn
import com.fitplan.ui.common.DialogPrimaryButton
import com.fitplan.ui.common.ReleaseNotesContent
import com.fitplan.ui.common.ReleaseNotesMaxHeight
import com.fitplan.updater.ReleaseNotesSection

/**
 * 覆盖安装新版本后的「本次更新」弹窗：标题写版本号，正文按重要度列出这个版本的改动。
 *
 * 与「发现新版本」的 `UpdateDialog` 不同，这里不用再问「要不要去下载」——升级已经发生了，
 * 所以只留一个「知道了」。正文可能很长，限高滚动。
 */
@Composable
fun VersionUpdateDialog(
    version: String,
    sections: List<ReleaseNotesSection>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.changelog_dialog_title, version)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)) {
                ReleaseNotesContent(
                    sections = sections,
                    modifier = Modifier
                        .heightIn(max = ReleaseNotesMaxHeight)
                        .verticalScroll(rememberScrollState()),
                )

                Text(
                    text = stringResource(R.string.changelog_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.secondaryItemAlpha(),
                )

                DialogButtonColumn {
                    DialogPrimaryButton(
                        text = stringResource(R.string.changelog_dialog_confirm),
                        onClick = onDismiss,
                    )
                }
            }
        },
        confirmButton = {},
    )
}
