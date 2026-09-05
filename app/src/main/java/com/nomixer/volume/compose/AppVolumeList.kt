package com.nomixer.volume.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.nomixer.volume.R
import com.nomixer.volume.data.App

internal data class Group(
    val name: String,
    val apps: List<App>,
    val startIndex: Int,
    val enableHide: Boolean
)

fun LazyListScope.group(
    header: @Composable () -> String,
    apps: List<App>,
    enableHide: Boolean = true,
    shadowColor: Color = Color.Transparent,
    onChange: (() -> Unit)? = null,
    onHeaderClick: (() -> Unit)? = null
) {
    if (apps.isNotEmpty()) {
        stickyHeader {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(top = 8.dp)
                    .then(
                        if (onHeaderClick != null) {
                            Modifier.clickable { onHeaderClick() }
                        } else {
                            Modifier
                        }
                    )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                ) {
                    NothingDot()
                    Text(
                        text = header().uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        items(
            items = apps.sortedWith(App.comparator), key = { app -> app.packageName }) { app ->
            // Apps move between groups as they start and stop playing;
            // animateItem slides them there instead of teleporting.
            AppVolumeSlider(
                app,
                true,
                enableHide,
                shadowColor = shadowColor,
                onChange = onChange,
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
fun AppVolumeList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    apps: MutableCollection<App>,
    showEmpty: Boolean = false,
    showAll: Boolean,
    shadowColor: Color = Color.Transparent,
    onChange: (() -> Unit)? = null,
    onShowAll: (() -> Unit)? = null,
    content: (LazyListScope.() -> Unit)? = null
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var selectedGroup by remember { mutableStateOf<String?>(null) }

    val activePlayers = mutableListOf<App>()
    val inactivePlayers = mutableListOf<App>()
    val hiddenPlayers = mutableListOf<App>()
    val otherAppsWithActivities = mutableListOf<App>()
    val otherAppsWithoutActivities = mutableListOf<App>()

    for (app in apps) {
        if (app.isPlayer) {
            if (!app.hidden) {
                if (app.isPlaying) {
                    activePlayers.add(app)
                } else {
                    inactivePlayers.add(app)
                }
            } else {
                hiddenPlayers.add(app)
            }
        } else {
            if (app.hasAnyActivity) {
                otherAppsWithActivities.add(app)
            } else {
                otherAppsWithoutActivities.add(app)
            }
        }
    }

    val groups = buildList<Group> {
        var currentIndex = 0
        val addGroup = { name: String, appsList: List<App>, enableHide: Boolean ->
            if (appsList.isNotEmpty()) {
                add(Group(name, appsList, currentIndex, enableHide))
                currentIndex += 1 + appsList.size
            }
        }
        addGroup(stringResource(R.string.group_active), activePlayers, true)
        addGroup(stringResource(R.string.group_inactive), inactivePlayers, true)
        addGroup(stringResource(R.string.group_hidden), hiddenPlayers, true)
        addGroup(stringResource(R.string.group_other), otherAppsWithActivities, false)
        addGroup(stringResource(R.string.group_system), otherAppsWithoutActivities, false)
    }

    LaunchedEffect(showAll) {
        if (showAll && groups.isNotEmpty()) {
            scope.launch {
                listState.scrollToItem(0, 0)
            }
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = contentPadding
    ) {
        content?.invoke(this)

        if (!showAll) {
            if (activePlayers.isNotEmpty()) {
                // apps comes from a mutableStateMapOf, whose iteration order
                // isn't insertion order and isn't guaranteed stable across
                // structural changes elsewhere in the map -- feeding that
                // straight into an animateItem()-tracked list let an
                // unrelated change anywhere in the map read as this list
                // silently reordering itself, animating a "move" that
                // never really happened. Sorted the same way the grouped
                // list below already is, so the order here only changes
                // when it's actually supposed to.
                items(
                    items = activePlayers.sortedWith(App.comparator),
                    key = { app -> app.packageName }
                ) { app ->
                    AppVolumeSlider(
                        app,
                        showOptions = false,
                        shadowColor = shadowColor,
                        onChange = onChange,
                        modifier = Modifier.animateItem()
                    )
                }
            } else if (showEmpty) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(
                            12.dp, Alignment.CenterVertically
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "NO ACTIVE PLAYERS", style = MaterialTheme.typography.titleMedium)

                        Button(onClick = { onShowAll?.invoke() }) {
                            Text(text = "Show all apps")
                        }
                    }
                }
            }
            return@LazyColumn
        }

        groups.forEach { group ->
            group(
                { group.name },
                group.apps,
                enableHide = group.enableHide,
                shadowColor = shadowColor,
                onChange = onChange,
                onHeaderClick = { selectedGroup = group.name }
            )
        }
    }

    if (selectedGroup != null) {
        GroupSelectionDialog(
            groups = groups,
            selectedGroup = selectedGroup!!,
            listState = listState,
            scope = scope,
            onDismiss = { selectedGroup = null }
        )
    }
}

@Composable
internal fun GroupSelectionDialog(
    groups: List<Group>,
    selectedGroup: String,
    listState: LazyListState,
    scope: kotlinx.coroutines.CoroutineScope,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.jump_to_group)) },
        text = {
            Column {
                groups.forEach { group ->
                    val isSelected = group.name == selectedGroup
                    Button(
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(group.startIndex)
                            }
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = if (isSelected) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) {
                        Text(text = group.name)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(text = stringResource(R.string.close))
            }
        }
    )
}
