package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.EcosystemApp
import com.example.data.model.AutomationActionType
import com.example.data.model.SmartDeviceType
import com.example.data.model.SmartHomeDevice
import com.example.data.model.TaskAutomation
import com.example.data.model.TodoCategory
import com.example.data.model.TodoReminder
import com.example.data.model.UiState
import com.example.ui.theme.AuraCobalt
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraMint
import com.example.ui.viewmodel.AuraViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutomationsScreen(viewModel: AuraViewModel) {
    val context = LocalContext.current
    val automationsState by viewModel.automationsState.collectAsStateWithLifecycle()
    val todosState by viewModel.todosState.collectAsStateWithLifecycle()
    val smartDevicesState by viewModel.smartDevicesState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val prefs by viewModel.localPrefs.collectAsStateWithLifecycle()

    var sectionTab by rememberSaveable { mutableIntStateOf(0) } // 0=Tasks, 1=Smart Home, 2=Device, 3=Routines
    var showCreateDialog by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.refreshLocationContext()
            viewModel.refreshContextualSuggestions()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Control Center",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Tasks, Smart Home, Device Controls, Apps & Voice Routines",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (sectionTab == 3) {
                FilledTonalButton(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("new_automation_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("New")
                }
            }
        }

        // 4-Way Compact Pill Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Tasks", "Home", "Device", "Routines").forEachIndexed { idx, label ->
                val selected = sectionTab == idx
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { sectionTab = idx }
                        .testTag("control_tab_$idx")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        when (sectionTab) {
            0 -> TasksProductivitySection(
                todosState = todosState,
                onAddTodo = { title, due, cat -> viewModel.addTodoItem(title, due, cat) },
                onToggleTodo = { id, done -> viewModel.toggleTodoItem(id, done) },
                onDeleteTodo = { id -> viewModel.deleteTodoItem(id) },
                onQuickAlarm = { viewModel.actionExecutor.launchAlarm(7, 30, "Aura Morning Alarm") },
                onQuickTimer = { viewModel.actionExecutor.launchTimer(300, "Aura 5-Min Timer") },
                onQuickCalendar = { title -> viewModel.actionExecutor.launchCalendarEvent(title, "Scheduled with Aura Assist") }
            )

            1 -> SmartHomeSection(
                smartDevicesState = smartDevicesState,
                onRunScene = { scene -> viewModel.runSmartScene(scene) },
                onUpdateDevice = { dev, power, lvl -> viewModel.updateSmartDeviceState(dev, power, lvl) },
                onOpenGoogleHome = { viewModel.openEcosystemApp(EcosystemApp.GOOGLE_HOME) }
            )

            2 -> DeviceAndAppsSection(
                onVolumeDown = { viewModel.actionExecutor.adjustMediaVolume(AudioManager.ADJUST_LOWER) },
                onVolumeUp = { viewModel.actionExecutor.adjustMediaVolume(AudioManager.ADJUST_RAISE) },
                onVolumeMute = { viewModel.actionExecutor.adjustMediaVolume(AudioManager.ADJUST_MUTE) },
                onOpenWifi = { viewModel.actionExecutor.openWifiSettings() },
                onOpenBluetooth = { viewModel.actionExecutor.openBluetoothSettings() },
                onOpenDisplay = { viewModel.actionExecutor.openDisplaySettings() },
                onOpenSound = { viewModel.actionExecutor.openSoundSettings() },
                onTakePhoto = { viewModel.actionExecutor.launchSystemCamera() },
                onPlayMedia = { q -> viewModel.actionExecutor.playMusicOrPodcast(q) },
                onOpenEcosystemApp = { app -> viewModel.openEcosystemApp(app) }
            )

            else -> RoutinesAndTelemetrySection(
                telemetry = telemetry,
                batteryMode = prefs.batteryMode,
                automationsState = automationsState,
                onSyncLocation = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        viewModel.refreshLocationContext()
                        viewModel.refreshContextualSuggestions()
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                },
                onToggleAutomation = { id, enabled -> viewModel.toggleAutomation(id, enabled) },
                onRunAutomation = { auto -> viewModel.runAutomation(auto) },
                onDeleteAutomation = { id -> viewModel.deleteAutomation(id) }
            )
        }
    }

    if (showCreateDialog) {
        CreateAutomationDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { title, trigger, type, payload, hint ->
                viewModel.createAutomation(title, trigger, type, payload, hint)
                showCreateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TasksProductivitySection(
    todosState: UiState<List<TodoReminder>>,
    onAddTodo: (String, String?, TodoCategory) -> Unit,
    onToggleTodo: (String, Boolean) -> Unit,
    onDeleteTodo: (String) -> Unit,
    onQuickAlarm: () -> Unit,
    onQuickTimer: () -> Unit,
    onQuickCalendar: (String) -> Unit
) {
    var todoTitle by remember { mutableStateOf("") }
    var todoDue by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf(TodoCategory.TASK) }

    // Quick Time & Calendar Row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalButton(
            onClick = onQuickAlarm,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("quick_alarm_button")
        ) {
            Icon(Icons.Rounded.Alarm, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(4.dp))
            Text("Alarm", maxLines = 1)
        }
        FilledTonalButton(
            onClick = onQuickTimer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("quick_timer_button")
        ) {
            Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(4.dp))
            Text("5m Timer", maxLines = 1)
        }
        FilledTonalButton(
            onClick = { onQuickCalendar(todoTitle.ifBlank { "Aura Event" }) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("quick_calendar_button")
        ) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(4.dp))
            Text("Event", maxLines = 1)
        }
    }

    // Add Synced Task / Reminder Card
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "ADD REMINDER OR TO-DO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedTextField(
                value = todoTitle,
                onValueChange = { todoTitle = it },
                placeholder = { Text("e.g., Buy groceries, Call dentist at 3 PM") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("todo_title_input")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = todoDue,
                    onValueChange = { todoDue = it },
                    placeholder = { Text("Due (e.g., Today 5 PM)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (todoTitle.isNotBlank()) {
                            onAddTodo(todoTitle.trim(), todoDue.trim().ifBlank { null }, selectedCat)
                            if (selectedCat == TodoCategory.CALENDAR) {
                                onQuickCalendar(todoTitle.trim())
                            }
                            todoTitle = ""
                            todoDue = ""
                        }
                    },
                    enabled = todoTitle.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("add_todo_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Add")
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TodoCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCat == cat,
                        onClick = { selectedCat = cat },
                        label = { Text(cat.label) }
                    )
                }
            }
        }
    }

    Text(
        text = "Synced To-Do & Reminders",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    when (todosState) {
        is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
        is UiState.Error -> Text(todosState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        is UiState.Success -> {
            if (todosState.data.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No tasks yet. Add one above or say \"Remind me to call Alex at 5 PM\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                todosState.data.forEach { todo ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = todo.completed,
                                onCheckedChange = { onToggleTodo(todo.id, it) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = todo.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    textDecoration = if (todo.completed) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (todo.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = listOfNotNull(todo.category, todo.dueLabel).joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { onDeleteTodo(todo.id) }, modifier = Modifier.size(44.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.cd_delete_item),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartHomeSection(
    smartDevicesState: UiState<List<SmartHomeDevice>>,
    onRunScene: (String) -> Unit,
    onUpdateDevice: (SmartHomeDevice, Boolean, Long) -> Unit,
    onOpenGoogleHome: () -> Unit
) {
    // Multi-Device Scenes
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MULTI-DEVICE SCENES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onOpenGoogleHome) {
                    Text("Google Home →")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { onRunScene("MOVIE_NIGHT") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("scene_movie_night")
                ) {
                    Text("Movie", maxLines = 1)
                }
                FilledTonalButton(
                    onClick = { onRunScene("GOOD_MORNING") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("scene_good_morning")
                ) {
                    Text("Morning", maxLines = 1)
                }
                FilledTonalButton(
                    onClick = { onRunScene("AWAY_MODE") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("scene_away_mode")
                ) {
                    Text("Away", maxLines = 1)
                }
            }
        }
    }

    Text(
        text = "Connected Smart Devices (Cloud Synced)",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    when (smartDevicesState) {
        is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
        is UiState.Error -> Text(smartDevicesState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        is UiState.Success -> {
            smartDevicesState.data.forEach { device ->
                SmartDeviceCard(
                    device = device,
                    onUpdate = { power, lvl -> onUpdateDevice(device, power, lvl) }
                )
            }
        }
    }
}

@Composable
private fun SmartDeviceCard(
    device: SmartHomeDevice,
    onUpdate: (Boolean, Long) -> Unit
) {
    val type = SmartDeviceType.fromRaw(device.deviceType)
    val icon = when (type) {
        SmartDeviceType.LIGHT -> Icons.Rounded.Lightbulb
        SmartDeviceType.LOCK -> if (device.poweredOn) Icons.Rounded.Lock else Icons.Rounded.LockOpen
        SmartDeviceType.THERMOSTAT -> Icons.Rounded.Thermostat
        SmartDeviceType.VACUUM -> Icons.Rounded.CleaningServices
        SmartDeviceType.TV -> Icons.Rounded.Tv
    }
    var sliderVal by remember(device.id, device.level) { mutableStateOf(device.level.toFloat()) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (device.poweredOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(18.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (device.poweredOn) AuraCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${device.room} • ${device.statusText}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (device.poweredOn) AuraMint else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = device.poweredOn,
                    onCheckedChange = { onUpdate(it, device.level) }
                )
            }

            if (type == SmartDeviceType.LIGHT || type == SmartDeviceType.THERMOSTAT || type == SmartDeviceType.TV) {
                val range = if (type == SmartDeviceType.THERMOSTAT) 16f..30f else 0f..100f
                val label = when (type) {
                    SmartDeviceType.LIGHT -> "Brightness: ${sliderVal.roundToInt()}%"
                    SmartDeviceType.THERMOSTAT -> "Target Temp: ${sliderVal.roundToInt()}°C"
                    else -> "Volume: ${sliderVal.roundToInt()}%"
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = sliderVal.coerceIn(range.start, range.endInclusive),
                    onValueChange = { sliderVal = it },
                    onValueChangeFinished = {
                        onUpdate(true, sliderVal.roundToInt().toLong())
                    },
                    valueRange = range
                )
            }
        }
    }
}

@Composable
private fun DeviceAndAppsSection(
    onVolumeDown: () -> Unit,
    onVolumeUp: () -> Unit,
    onVolumeMute: () -> Unit,
    onOpenWifi: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onOpenDisplay: () -> Unit,
    onOpenSound: () -> Unit,
    onTakePhoto: () -> Unit,
    onPlayMedia: (String) -> Unit,
    onOpenEcosystemApp: (EcosystemApp) -> Unit
) {
    var mediaQuery by remember { mutableStateOf("") }

    // Media & Camera Card
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "MEDIA, VOLUME & CAMERA",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onVolumeDown,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeDown, contentDescription = "Volume Down")
                }
                FilledTonalButton(
                    onClick = onVolumeUp,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "Volume Up")
                }
                FilledTonalButton(
                    onClick = onVolumeMute,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeOff, contentDescription = "Mute")
                }
                FilledTonalButton(
                    onClick = onTakePhoto,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = "Take Photo")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = mediaQuery,
                    onValueChange = { mediaQuery = it },
                    placeholder = { Text("Play music or podcast (e.g., Lo-Fi Beats)") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("play_media_input")
                )
                Button(
                    onClick = {
                        onPlayMedia(mediaQuery.trim().ifBlank { "Top Focus Playlist" })
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("play_media_button")
                ) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Play")
                }
            }
        }
    }

    // Device Settings Panels
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "QUICK DEVICE SETTINGS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenWifi,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Wi-Fi")
                }
                OutlinedButton(
                    onClick = onOpenBluetooth,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Rounded.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Bluetooth")
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenDisplay,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Rounded.Brightness6, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Display")
                }
                OutlinedButton(
                    onClick = onOpenSound,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Sound")
                }
            }
        }
    }

    // Ecosystem App Launcher Grid
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "ECOSYSTEM APPS & INTEGRATIONS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            EcosystemApp.entries.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { app ->
                        FilledTonalButton(
                            onClick = { onOpenEcosystemApp(app) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("ecosystem_app_${app.name.lowercase()}")
                        ) {
                            Icon(
                                imageVector = if (app == EcosystemApp.GOOGLE_HOME) Icons.Rounded.Home else Icons.Rounded.Apps,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(app.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutinesAndTelemetrySection(
    telemetry: com.example.data.model.DeviceTelemetry,
    batteryMode: String,
    automationsState: UiState<List<TaskAutomation>>,
    onSyncLocation: () -> Unit,
    onToggleAutomation: (String, Boolean) -> Unit,
    onRunAutomation: (TaskAutomation) -> Unit,
    onDeleteAutomation: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "EFFICIENCY & CONTEXT TELEMETRY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryTile(
                    icon = Icons.Rounded.Memory,
                    label = "App Memory",
                    value = "${telemetry.appHeapMb} / ${telemetry.maxHeapMb} MB",
                    accent = AuraCyan,
                    modifier = Modifier.weight(1f)
                )
                TelemetryTile(
                    icon = Icons.Rounded.BatteryChargingFull,
                    label = "Power ($batteryMode)",
                    value = "${telemetry.batteryPercent}%${if (telemetry.charging) " ⚡" else ""}",
                    accent = AuraMint,
                    modifier = Modifier.weight(1f)
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = AuraCobalt,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Coarse Location Context",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = telemetry.locationLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    TextButton(
                        onClick = onSyncLocation,
                        modifier = Modifier.testTag("sync_location_button")
                    ) {
                        Text("Update")
                    }
                }
            }
        }
    }

    Text(
        text = "Real-Time Task Automations",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    when (automationsState) {
        is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
        is UiState.Error -> Text(automationsState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        is UiState.Success -> {
            if (automationsState.data.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No task automations yet. Tap + New to create a voice-triggered routine.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                automationsState.data.forEach { auto ->
                    AutomationCard(
                        automation = auto,
                        onToggle = { onToggleAutomation(auto.id, it) },
                        onRun = { onRunAutomation(auto) },
                        onDelete = { onDeleteAutomation(auto.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryTile(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AutomationCard(
    automation: TaskAutomation,
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${AutomationActionType.fromRaw(automation.actionType).label.uppercase()} • ${automation.runCount} RUNS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = automation.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Switch(checked = automation.enabled, onCheckedChange = onToggle)
            }

            Text(
                text = "Voice trigger: \"${automation.triggerPhrase}\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = automation.actionPayload,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = automation.contextHint.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.cd_delete_item),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onRun,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("run_automation_${automation.id}")
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.cd_run_automation), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Run")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateAutomationDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, AutomationActionType, String, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("") }
    var actionType by remember { mutableStateOf(AutomationActionType.BRIEFING) }
    var payload by remember { mutableStateOf("") }
    var contextHint by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Task Automation") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Routine Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = trigger,
                    onValueChange = { trigger = it },
                    label = { Text("Voice Trigger Phrase") },
                    placeholder = { Text("e.g., drive to gym") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AutomationActionType.entries.forEach { type ->
                        FilterChip(
                            selected = actionType == type,
                            onClick = { actionType = type },
                            label = { Text(type.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = payload,
                    onValueChange = { payload = it },
                    label = {
                        Text(
                            when (actionType) {
                                AutomationActionType.ALARM -> "Time (HH:MM, e.g. 07:30)"
                                AutomationActionType.TIMER -> "Minutes (e.g. 15)"
                                AutomationActionType.WHATSAPP -> "Message Text"
                                AutomationActionType.MAPS -> "Destination Address or Place"
                                AutomationActionType.BRIEFING -> "Gemini Briefing Prompt"
                                AutomationActionType.WEB_SEARCH -> "Search Query"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contextHint,
                    onValueChange = { contextHint = it },
                    label = { Text("Context Hint (optional)") },
                    placeholder = { Text("e.g., Weekdays • Evening") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && trigger.isNotBlank() && payload.isNotBlank()) {
                        onSave(title, trigger, actionType, payload, contextHint.takeIf { it.isNotBlank() })
                    }
                },
                enabled = title.isNotBlank() && trigger.isNotBlank() && payload.isNotBlank()
            ) {
                Text("Save & Sync")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
