package com.fitplan.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.fitplan.app.BuildConfig
import com.fitplan.app.R
import com.fitplan.presentation.core.components.SettingsItemsPaddings
import com.fitplan.presentation.core.components.material.Scaffold
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.presentation.core.util.secondaryItemAlpha
import com.fitplan.presentation.util.Screen
import com.fitplan.updater.FailureReason
import dev.zacsweers.metrox.viewmodel.metroViewModel

/** 关于页：显示应用名与版本号，并提供 GitHub 仓库入口。 */
object AboutScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = metroViewModel<AboutScreenModel>()
        val checkState by screenModel.state.collectAsState()

        Scaffold(
            topBar = { scrollBehavior ->
                TopAppBar(
                    title = { Text(text = stringResource(R.string.about_title)) },
                    navigationIcon = {
                        IconButton(onClick = navigator::pop) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
            ) {
                Spacer(modifier = Modifier.height(MaterialTheme.padding.extraLarge))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SettingsItemsPaddings.Horizontal),
                )

                Spacer(modifier = Modifier.height(MaterialTheme.padding.small))

                Text(
                    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SettingsItemsPaddings.Horizontal)
                        .secondaryItemAlpha(),
                )

                Spacer(modifier = Modifier.height(MaterialTheme.padding.extraLarge))

                CheckUpdateItem(
                    state = checkState,
                    onClick = {
                        val available = checkState as? ManualCheckState.Available
                        if (available != null) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(available.release.pageUrl)))
                            screenModel.markNotified(available.release)
                        } else {
                            screenModel.check()
                        }
                    },
                )

                Spacer(modifier = Modifier.height(MaterialTheme.padding.medium))

                GithubItem(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))) },
                )

                Spacer(modifier = Modifier.height(MaterialTheme.padding.medium))

                Text(
                    text = stringResource(R.string.about_thanks),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SettingsItemsPaddings.Horizontal)
                        .secondaryItemAlpha(),
                )
            }
        }
    }
}

private const val GITHUB_URL = "https://github.com/UnsleepingDawn/Dascle"

/**
 * 「检查更新」入口：点一下手动查，右侧显示结果。
 *
 * 查到新版本时整行变成「去下载」的意思（点击打开下载页），其余状态点击都是重新检查。
 */
@Composable
private fun CheckUpdateItem(
    state: ManualCheckState,
    onClick: () -> Unit,
) {
    val checking = state is ManualCheckState.Checking

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !checking, onClick = onClick)
            .padding(
                horizontal = SettingsItemsPaddings.Horizontal,
                vertical = SettingsItemsPaddings.Vertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        if (checking) {
            CircularProgressIndicator(
                modifier = Modifier.size(UpdateIconSize),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(UpdateIconSize),
            )
        }
        Text(
            text = stringResource(R.string.about_check_update),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = state.statusText(),
            style = MaterialTheme.typography.bodySmall,
            color = when (state) {
                is ManualCheckState.Available -> MaterialTheme.colorScheme.primary
                is ManualCheckState.Failed -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** 状态文案；[ManualCheckState.Idle] 不显示。 */
@Composable
private fun ManualCheckState.statusText(): String = when (this) {
    ManualCheckState.Idle -> ""
    ManualCheckState.Checking -> stringResource(R.string.about_checking_update)
    is ManualCheckState.UpToDate -> stringResource(R.string.about_up_to_date, currentVersion)
    is ManualCheckState.Available -> stringResource(R.string.about_update_available, release.version)
    is ManualCheckState.Failed -> stringResource(
        when (reason) {
            FailureReason.NETWORK -> R.string.about_update_failed_network
            FailureReason.SERVER -> R.string.about_update_failed_server
            FailureReason.RATE_LIMITED -> R.string.about_update_failed_rate_limited
            FailureReason.NO_RELEASE -> R.string.about_update_failed_no_release
        },
    )
}

private val UpdateIconSize = 20.dp

/** 居中的 GitHub 入口：图标 + 文案，整行可点。 */
@Composable
private fun GithubItem(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = SettingsItemsPaddings.Horizontal,
                vertical = SettingsItemsPaddings.Vertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small, Alignment.CenterHorizontally),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_github),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(R.string.about_github),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
