package li.gkd.app.ui

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.Serializable
import li.gkd.app.META
import li.gkd.app.R
import li.gkd.app.store.storeFlow
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.component.RotatingLoadingIcon
import li.gkd.app.ui.component.SettingItem
import li.gkd.app.ui.component.TextMenu
import li.gkd.app.ui.share.LocalDarkTheme
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.itemPadding
import li.gkd.app.ui.style.titleItemPadding
import li.gkd.app.util.ISSUES_URL
import li.gkd.app.util.REPOSITORY_URL
import li.gkd.app.util.ShortUrlSet
import li.gkd.app.util.UpdateChannelOption
import li.gkd.app.util.findOption
import li.gkd.app.util.launchAsFn
import li.gkd.app.util.launchTry
import li.gkd.app.util.throttle
import li.gkd.app.util.toast

@Serializable
data object AboutRoute : NavKey

@Composable
fun AboutPage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<AboutVm>()
    val store by storeFlow.collectAsStateWithLifecycle()
    val updateChannel = UpdateChannelOption.objects.findOption(store.updateChannel)

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = {
                            mainVm.popPage()
                        },
                    )
                },
                title = { Text(text = "About") },
                actions = {
                    PerfIconButton(
                        imageVector = PerfIcon.Share,
                        onClick = { vm.setShareAppDialogVisible(true) },
                    )
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedLogoIcon(
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = throttle { toast("Hey, what was that for~ Ouch~") }
                        )
                        .fillMaxWidth(0.33f)
                        .aspectRatio(1f)
                )
                Column(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(onClick = { vm.setInfoDialogVisible(true) })
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = META.appName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = META.versionName,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            SettingItem(
                imageVector = null,
                title = "Source code",
                onClick = {
                    mainVm.openUrl(REPOSITORY_URL)
                },
            )
            if (META.isGkdChannel) {
                SettingItem(
                    imageVector = null,
                    title = "Support the project",
                    onClick = {
                        mainVm.navigateWebPage(ShortUrlSet.URL10)
                    },
                )
            }
            SettingItem(
                imageVector = null,
                title = "Terms of use",
                onClick = {
                    mainVm.navigateWebPage(ShortUrlSet.URL12)
                },
            )
            SettingItem(
                imageVector = null,
                title = "Privacy policy",
                onClick = {
                    mainVm.navigateWebPage(ShortUrlSet.URL11)
                },
            )

            FeedbackSection()
            SettingItem(
                title = "Export logs",
                imageVector = PerfIcon.Share,
                onClick = {
                    mainVm.shareLog.show()
                }
            )
            if (mainVm.updateStatus != null) {
                Text(
                    text = "Update",
                    modifier = Modifier.titleItemPadding(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextMenu(
                    title = "Update channel",
                    option = updateChannel
                ) {
                    if (mainVm.updateStatus.checkUpdatingFlow.value) return@TextMenu
                    if (it.value == UpdateChannelOption.Beta.value) {
                        vm.scope.launchTry {
                            if (!mainVm.dialogRequests.confirm(
                                title = "Version channel",
                                text = "The beta channel updates faster\nbut is less stable and may have more bugs\nplease use with caution",
                            )) return@launchTry
                            vm.setUpdateChannel(it)
                        }
                    } else {
                        vm.setUpdateChannel(it)
                    }
                }
                Row(
                    modifier = Modifier
                        .clickable(
                            onClick = throttle {
                                mainVm.updateStatus.checkUpdate(true)
                            }
                        )
                        .fillMaxWidth()
                        .itemPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Check for updates",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    RotatingLoadingIcon(loading = mainVm.updateStatus.checkUpdatingFlow.collectAsStateWithLifecycle().value)
                }
            }
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }

    AboutDialogs()
}

@Composable
private fun FeedbackSection() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<AboutVm>()
    val primaryColor = MaterialTheme.colorScheme.primary
    Text(
        text = "Feedback",
        modifier = Modifier.titleItemPadding(),
        style = MaterialTheme.typography.titleSmall,
        color = primaryColor,
    )
    Column(
        modifier = Modifier
            .clickable(onClick = throttle(vm.scope.launchAsFn {
                val noticeText = buildAnnotatedString {
                    val highlightStyle = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                    )
                    append("Thanks for taking the time to give feedback, ")
                    withStyle(style = highlightStyle) {
                        append("GKD does not ship with any rules by default, and only accepts feedback related to the app itself")
                    }
                    append("\n\n")
                    append("Please first determine whether this is an issue with a third-party rule subscription. If so, you should report it to the rule provider instead of here. ")
                    withStyle(style = highlightStyle) {
                        append("If you're confident this is an issue with the GKD app itself")
                    }
                    append(", you can tap below to continue giving feedback")
                }
                if (!mainVm.dialogRequests.confirm(
                    title = "Feedback notice",
                    text = noticeText,
                    confirmText = "Continue",
                    dismissOnRequest = true,
                )) return@launchAsFn
                mainVm.openUrl(ISSUES_URL)
            }))
            .fillMaxWidth()
            .itemPadding()
    ) {
        Text(
            text = "Report an issue",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun AnimatedLogoIcon(
    modifier: Modifier = Modifier
) {
    val darkTheme = LocalDarkTheme.current
    val colorRid = if (darkTheme) R.color.better_white else R.color.better_black
    var atEnd by remember { mutableStateOf(false) }
    val animation = AnimatedImageVector.animatedVectorResource(id = R.drawable.ic_anim_logo)
    val painter = rememberAnimatedVectorPainter(
        animation,
        atEnd
    )
    LaunchedEffect(Unit) {
        while (isActive) {
            atEnd = !atEnd
            delay(animation.totalDuration.toLong())
        }
    }
    Icon(
        modifier = modifier,
        painter = painter,
        contentDescription = null,
        tint = colorResource(colorRid),
    )
}
