package li.gkd.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.ui.component.EmptyText
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.itemPadding
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.appInfoMapFlow
import li.gkd.app.util.launchAsFn
import li.gkd.app.util.ruleSummaryFlow
import li.gkd.app.util.throttle

@Serializable
data object SlowGroupRoute : NavKey

@Composable
fun SlowGroupPage() {
    val mainVm = LocalMainViewModel.current
    val ruleSummary by ruleSummaryFlow.collectAsStateWithLifecycle()
    val appInfoCache by appInfoMapFlow.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    PerfIconButton(imageVector = PerfIcon.ArrowBack, onClick = {
                        mainVm.popPage()
                    })
                },
                title = { Text(text = "Slow queries") },
                actions = {
                    PerfIconButton(
                        imageVector = PerfIcon.Info,
                        onClick = throttle(mainVm.scope.launchAsFn {
                            mainVm.dialogRequests.showMessage(
                                title = "Slow queries",
                                text = arrayOf(
                                    "A single rule is considered a slow query if it meets all 3 of the following conditions",
                                    "1. The selector's right side can't do a fast query and isn't an active query, or it uses << internally and can't do a fast query\n2. preKeys is empty\n3. matchTime is empty or greater than 10s",
                                    "A slow query may cause slow triggering or extra battery drain. Some possible optimizations\n1. Reduce how often the selector fetches new nodes\n2. Reduce or limit the rule's query time or count"
                                ).joinToString("\n\n"),
                            )
                        }),
                    )
                }
            )
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding)
        ) {
            items(
                ruleSummary.slowGlobalGroups,
                { (_, r) -> r.subsItem.id to r.group.key }
            ) { (group, rule) ->
                SlowGroupCard(
                    modifier = Modifier
                        .clickable(onClick = throttle {
                            mainVm.navigatePage(
                                SubsGlobalGroupListRoute(
                                    rule.subsItem.id,
                                    group.key
                                )
                            )
                        })
                        .itemPadding(),
                    title = group.name,
                    desc = "${rule.rawSubs.name}/global rule"
                )
            }
            items(
                ruleSummary.slowAppGroups,
                { (_, r) -> Triple(r.subsItem.id, r.appId, r.group.key) }
            ) { (group, rule) ->
                SlowGroupCard(
                    modifier = Modifier
                        .clickable(onClick = throttle {
                            mainVm.navigatePage(
                                SubsAppGroupListRoute(
                                    rule.subsItem.id,
                                    rule.app.id,
                                    group.key
                                )
                            )
                        })
                        .itemPadding(),
                    title = group.name,
                    desc = "${rule.rawSubs.name}/app rule/${appInfoCache[rule.app.id]?.name ?: rule.app.name ?: rule.app.id}"
                )
            }
            item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (ruleSummary.slowGroupCount == 0) {
                    EmptyText(text = "No rules yet")
                }
            }
        }
    }
}

@Composable
fun SlowGroupCard(title: String, desc: String, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PerfIcon(
            imageVector = PerfIcon.KeyboardArrowRight,
        )
    }
}
