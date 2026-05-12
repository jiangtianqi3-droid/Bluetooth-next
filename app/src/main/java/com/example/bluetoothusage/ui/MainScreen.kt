package com.example.bluetoothusage.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluetoothusage.data.UsageRecord
import com.example.bluetoothusage.repository.ActiveSessionInfo
import com.example.bluetoothusage.viewmodel.MainUiState
import com.example.bluetoothusage.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(
        enabled = state.isSelectingDevice || state.showHistoryOnly || state.showSettings || state.showCalendar
    ) {
        when {
            state.isSelectingDevice -> viewModel.closeDeviceSelector()
            state.showHistoryOnly -> viewModel.closeHistory()
            state.showSettings -> viewModel.closeSettings()
            state.showCalendar -> viewModel.closeCalendar()
        }
    }

    when {
        state.isSelectingDevice -> DeviceSelectScreen(
            devices = state.pairedDevices,
            selectedAddresses = state.targetDevices.map { it.address }.toSet(),
            selectionGeneration = state.deviceSelectionGeneration,
            onBack = viewModel::closeDeviceSelector,
            onRefresh = viewModel::refreshDeviceList,
            onConfirm = viewModel::selectDevices
        )
        state.showHistoryOnly -> HistoryScreen(
            records = state.history,
            onBack = viewModel::closeHistory,
            onUpdateRecord = viewModel::updateRecord,
            onDeleteRecord = viewModel::deleteRecord
        )
        state.showSettings -> SettingsScreen(
            state = state,
            onBack = viewModel::closeSettings,
            onDailyLimitHoursChange = viewModel::setDailyLimitHours,
            onDailyLimitShortcut = viewModel::setDailyLimitMillis,
            onSleepEnabledChange = viewModel::setSleepEnabled,
            onAdjustSleepStart = viewModel::adjustSleepStart,
            onAdjustSleepEnd = viewModel::adjustSleepEnd,
            onHideFromRecentsChange = viewModel::setHideFromRecents,
            onBootAutoStartChange = viewModel::setBootAutoStart,
            onExportDiagnostics = viewModel::exportDiagnostics,
            onCleanupInvalidRecords = viewModel::cleanupInvalidRecords
        )
        state.showCalendar -> CalendarScreen(
            state = state,
            onBack = viewModel::closeCalendar,
            onPreviousMonth = viewModel::previousCalendarMonth,
            onNextMonth = viewModel::nextCalendarMonth,
            onUpdateRecord = viewModel::updateRecord
        )
        else -> MainContent(
            state = state,
            onSelectDevice = viewModel::openDeviceSelector,
            onOpenSettings = viewModel::openSettings,
            onPreviousMonth = viewModel::previousCalendarMonth,
            onNextMonth = viewModel::nextCalendarMonth,
            onUpdateRecord = viewModel::updateRecord
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContent(
    state: MainUiState,
    onSelectDevice: () -> Unit,
    onOpenSettings: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onUpdateRecord: (UsageRecord, Long, Long, String) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    BackHandler(enabled = pagerState.currentPage == 1) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("蓝牙耳机计时") },
                actions = {
                    IconActionButton(
                        icon = Icons.Filled.Settings,
                        contentDescription = "设置",
                        onClick = onOpenSettings
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.height(56.dp),
                tonalElevation = 2.dp
            ) {
                NavigationBarItem(
                    selected = pagerState.settledPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "计时", modifier = Modifier.size(22.dp)) },
                    modifier = Modifier.heightIn(max = 56.dp)
                )
                NavigationBarItem(
                    selected = pagerState.settledPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = "日历", modifier = Modifier.size(22.dp)) },
                    modifier = Modifier.heightIn(max = 56.dp)
                )
            }
        }
    ) { padding ->
        HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) { page ->
            when (page) {
                0 -> HomePage(
                    state = state,
                    onSelectDevice = onSelectDevice,
                    onUpdateRecord = onUpdateRecord
                )
                1 -> CalendarPage(
                    state = state,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onUpdateRecord = onUpdateRecord
                )
            }
        }
    }
}

@Composable
private fun HomePage(
    state: MainUiState,
    onSelectDevice: () -> Unit,
    onUpdateRecord: (UsageRecord, Long, Long, String) -> Unit
) {
    var editingRecord by remember { mutableStateOf<UsageRecord?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        HeroStatusCard(state = state, onSelectDevice = onSelectDevice)
        GoalCard(
            state = state,
            onRecordClick = { record -> editingRecord = record },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
    editingRecord?.let { record ->
        EditRecordDialog(
            record = record,
            onDismiss = { editingRecord = null },
            onSave = { start, end, note ->
                onUpdateRecord(record, start, end, note)
                editingRecord = null
            }
        )
    }
}

@Composable
private fun HeroStatusCard(state: MainUiState, onSelectDevice: () -> Unit) {
    val heroColor = Color(0xFF075FE5)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = heroColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Headphones, contentDescription = null, tint = Color.White)
                    Text("目标设备", color = Color.White.copy(alpha = 0.92f), fontWeight = FontWeight.SemiBold)
                }
                StatusPill(text = if (state.isConnected) "已连接" else "未连接", active = state.isConnected)
            }
            if (state.targetDevices.isEmpty()) {
                Text(
                    text = "未选择",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            } else {
                Text(
                    text = "${state.targetDevices.size} 个设备",
                    color = Color.White.copy(alpha = 0.74f),
                    style = MaterialTheme.typography.labelLarge
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.targetDevices.forEach { device ->
                        DeviceStatusRow(
                            name = device.name,
                            connected = state.isDeviceActive(device.address),
                            batteryPercent = state.batteryFor(device.address),
                            durationMillis = state.currentSessionMillis
                        )
                    }
                }
            }
            Button(
                onClick = onSelectDevice,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = heroColor
                )
            ) {
                Text("选择已配对设备")
            }
        }
    }
}

@Composable
private fun DeviceStatusRow(
    name: String,
    connected: Boolean,
    batteryPercent: Int?,
    durationMillis: Long
) {
    val nameColor = if (connected) Color.White else Color.White.copy(alpha = 0.52f)
    val dotColor = if (connected) Color(0xFF28E679) else Color.White.copy(alpha = 0.34f)
    val metaColor = if (connected) Color.White.copy(alpha = 0.86f) else Color.White.copy(alpha = 0.48f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(dotColor)
        }
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = nameColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(
            modifier = Modifier.width(132.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (connected && batteryPercent != null) {
                Text(
                    text = "$batteryPercent%",
                    color = metaColor,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = if (connected) formatDurationCompact(durationMillis) else "未连接",
                color = metaColor,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun GoalCard(
    state: MainUiState,
    onRecordClick: (UsageRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    var showClockView by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val nowMinute = LocalDateTime.now().toLocalTime().let { it.hour * 60 + it.minute }
    val windowStart = (nowMinute - 120).coerceAtLeast(0)
    val windowEnd = (nowMinute + 120).coerceAtMost(1440)
    val cardColor = MaterialTheme.colorScheme.surface
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        today.format(DateTimeFormatter.ofPattern("M月d日")),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showClockView) {
                        VerticalRemainingBar(
                            progress = state.todayRemainingProgress,
                            active = state.isConnected,
                            modifier = Modifier
                                .size(width = 28.dp, height = 46.dp)
                                .clickable { showClockView = false }
                        )
                    } else {
                        GoalRing(
                            progress = state.todayRemainingProgress,
                            center = "",
                            label = "",
                            modifier = Modifier
                                .size(34.dp)
                                .clickable { showClockView = true },
                            strokeWidth = 3.dp,
                            active = state.isConnected
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "已用 ${formatDurationCompact(state.todayMillis)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "额度 ${formatDurationCompact(state.dailyLimitMillis)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Crossfade(
                targetState = showClockView,
                label = "today-view-switch",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { clockView ->
                if (clockView) {
                    TodayCircularTimeline(
                        day = today,
                        records = state.todayTimelineRecords(),
                        sleepEnabled = state.sleepEnabled,
                        sleepStartMinutes = state.sleepStartMinutes,
                        sleepEndMinutes = state.sleepEndMinutes,
                        onEditRecord = onRecordClick,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    InteractiveUsageTimeline(
                        day = today,
                        records = state.todayTimelineRecords(),
                        sleepEnabled = state.sleepEnabled,
                        sleepStartMinutes = state.sleepStartMinutes,
                        sleepEndMinutes = state.sleepEndMinutes,
                        initialWindowStartMinute = windowStart,
                        initialWindowEndMinute = windowEnd,
                        edgeFadeColor = cardColor,
                        minZoom = 0.88f,
                        doubleTapZoomToLatest = true,
                        initialAnchorLatest = true,
                        onRecordClick = onRecordClick,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun VerticalRemainingBar(progress: Float, active: Boolean, modifier: Modifier = Modifier) {
    val color = if (progress <= 0f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val pulseAlpha by rememberInfiniteTransition(label = "vertical-bar-dot").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "vertical-bar-dot-alpha"
    )
    Canvas(modifier = modifier) {
        val strokeWidth = 5.dp.toPx()
        val x = size.width / 2f
        val top = 4.dp.toPx()
        val bottom = size.height - 4.dp.toPx()
        drawLine(
            color = track,
            start = Offset(x, top),
            end = Offset(x, bottom),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        val fillTop = bottom - (bottom - top) * progress.coerceIn(0f, 1f)
        drawLine(
            color = color,
            start = Offset(x, bottom),
            end = Offset(x, fillTop),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        if (active && progress > 0f) {
            drawCircle(color.copy(alpha = 0.18f * pulseAlpha), radius = 7.dp.toPx(), center = Offset(x, fillTop))
            drawCircle(color.copy(alpha = 0.65f + 0.35f * pulseAlpha), radius = 3.dp.toPx(), center = Offset(x, fillTop))
        }
    }
}

@Composable
private fun TodayCircularTimeline(
    day: LocalDate,
    records: List<UsageRecord>,
    sleepEnabled: Boolean,
    sleepStartMinutes: Int,
    sleepEndMinutes: Int,
    onEditRecord: (UsageRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation = remember(day) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<RingSelection?>(null) }
    var isDraggingRing by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val tickTextPaint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF6F7F95.toInt()
            textSize = with(density) { 10.sp.toPx() }
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    LaunchedEffect(selected) {
        if (selected != null) {
            delay(4_000)
            selected = null
        }
    }
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(day, records) {
                    var previousAngle = Float.NaN
                    var angularVelocity = 0f
                    var lastTime = 0L
                    val velocitySamples = mutableListOf<Float>()
                    var tapStartTime = 0L
                    var initialDownPos = Offset.Zero
                    var isInRingArea = false

                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = minOf(size.width, size.height) * 0.34f
                        val strokeWidth = 34.dp.toPx()
                        val innerRadius = radius - strokeWidth / 2f
                        val outerRadius = radius + strokeWidth / 2f
                        val distance = kotlin.math.sqrt(
                            (down.position.x - center.x) * (down.position.x - center.x) +
                            (down.position.y - center.y) * (down.position.y - center.y)
                        )
                        if (distance !in innerRadius..outerRadius) {
                            return@awaitEachGesture
                        }

                        isDraggingRing = true
                        isInRingArea = true
                        previousAngle = Float.NaN
                        lastTime = System.currentTimeMillis()
                        velocitySamples.clear()
                        angularVelocity = 0f
                        tapStartTime = System.currentTimeMillis()
                        initialDownPos = down.position
                        var totalDragDistance = 0f
                        var hasMoved = false
                        var dragDeltaAccumulator = 0f

                        do {
                            val event = awaitPointerEvent()
                            dragDeltaAccumulator = 0f
                            event.changes.forEach { change ->
                                val currentDistance = kotlin.math.sqrt(
                                    (change.position.x - center.x) * (change.position.x - center.x) +
                                    (change.position.y - center.y) * (change.position.y - center.y)
                                )
                                
                                if (currentDistance !in innerRadius..outerRadius) {
                                    isInRingArea = false
                                } else {
                                    isInRingArea = true
                                }
                                
                                if (change.pressed && isInRingArea) {
                                    change.consume()
                                    val touchX = change.position.x
                                    val touchY = change.position.y
                                    val currentAngle = Math.toDegrees(
                                        atan2((touchY - center.y).toDouble(), (touchX - center.x).toDouble())
                                    ).toFloat()

                                    totalDragDistance += kotlin.math.sqrt(
                                        (touchX - initialDownPos.x) * (touchX - initialDownPos.x) +
                                        (touchY - initialDownPos.y) * (touchY - initialDownPos.y)
                                    )

                                    if (!previousAngle.isNaN()) {
                                        var delta = currentAngle - previousAngle
                                        if (delta > 180f) delta -= 360f
                                        if (delta < -180f) delta += 360f

                                        val currentTime = System.currentTimeMillis()
                                        if (lastTime > 0L && currentTime > lastTime) {
                                            val dt = (currentTime - lastTime).toFloat()
                                            if (dt > 0f && dt < 100f) {
                                                val sampleVelocity = delta / dt
                                                velocitySamples.add(sampleVelocity)
                                                if (velocitySamples.size > 10) {
                                                    velocitySamples.removeAt(0)
                                                }
                                            }
                                        }
                                        lastTime = currentTime

                                        dragDeltaAccumulator += delta
                                        hasMoved = true
                                    }
                                    previousAngle = currentAngle
                                }
                            }
                            if (dragDeltaAccumulator != 0f) {
                                scope.launch {
                                    rotation.snapTo(rotation.value + dragDeltaAccumulator)
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        isDraggingRing = false

                        if (velocitySamples.size >= 3) {
                            val recentSamples = velocitySamples.takeLast(5)
                            angularVelocity = recentSamples.average().toFloat()
                        }

                        val tapDuration = System.currentTimeMillis() - tapStartTime
                        val isTapGesture = totalDragDistance < 15f && tapDuration < 250L && !hasMoved

                        if (isTapGesture && isInRingArea) {
                            val canvasSize = Size(size.width.toFloat(), size.height.toFloat())
                            hitRingSegment(
                                tap = initialDownPos,
                                canvasSize = canvasSize,
                                day = day,
                                records = records,
                                sleepEnabled = sleepEnabled,
                                sleepStartMinutes = sleepStartMinutes,
                                sleepEndMinutes = sleepEndMinutes,
                                rotation = rotation.value
                            )?.let { segment ->
                                selected = RingSelection(
                                    segment = segment,
                                    alignment = alignmentForRingPopup(
                                        tap = initialDownPos,
                                        width = size.width.toFloat(),
                                        height = size.height.toFloat()
                                    )
                                )
                            }
                        } else if (kotlin.math.abs(angularVelocity) > 0.015f) {
                            scope.launch {
                                rotation.animateDecay(
                                    initialVelocity = angularVelocity * 1000f,
                                    animationSpec = exponentialDecay(
                                        frictionMultiplier = 0.7f,
                                        absVelocityThreshold = 0.005f
                                    )
                                )
                            }
                        }

                        angularVelocity = 0f
                        previousAngle = Float.NaN
                        lastTime = 0L
                        velocitySamples.clear()
                    }
                }
        ) {
            drawTodayRing(
                day = day,
                records = records,
                sleepEnabled = sleepEnabled,
                sleepStartMinutes = sleepStartMinutes,
                sleepEndMinutes = sleepEndMinutes,
                rotation = rotation.value,
                tickTextPaint = tickTextPaint
            )
        }
        selected?.let { selection ->
            val segment = selection.segment
            Surface(
                modifier = Modifier
                    .align(selection.alignment)
                    .padding(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "${formatMinutesOfDay(segment.startMinute)} - ${formatMinutesOfDay(segment.endMinute)}",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        segment.record.audioAppName.ifBlank { "播放应用未知" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = { onEditRecord(segment.record) }) { Text("编辑") }
                }
            }
        }
    }
}

@Composable
private fun SleepSummaryCard(state: MainUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("睡眠期间不记录", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    text = if (state.sleepEnabled) {
                        "${formatMinutesOfDay(state.sleepStartMinutes)} - ${formatMinutesOfDay(state.sleepEndMinutes)}"
                    } else {
                        "已关闭"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (state.sleepEnabled) "自动分段" else "持续记录",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun InfoPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DurationLine(label: String, durationMillis: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatDuration(durationMillis), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun UsageRecordRow(record: UsageRecord, trailing: @Composable (() -> Unit)? = null) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DeviceColorDot(record.deviceAddress, Modifier.size(12.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(record.deviceName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${formatTime(record.startTime)} - ${formatTime(record.endTime)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "时长 ${formatDuration(record.durationMillis)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (record.note.isNotBlank()) {
                    Text(record.note, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                if (record.audioAppName.isNotBlank()) {
                    Text(
                        text = "播放来源 ${record.audioAppName}${record.mediaTitleSnapshot.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}",
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun GoalRing(
    progress: Float,
    center: String,
    label: String,
    modifier: Modifier = Modifier.size(118.dp),
    strokeWidth: Dp = 10.dp,
    active: Boolean = false
) {
    val remainingProgress = progress.coerceIn(0f, 1f)
    val color = when {
        remainingProgress >= 0.995f -> Color(0xFF22C55E)
        remainingProgress >= 0.40f -> Color(0xFF1478FF)
        remainingProgress >= 0.20f -> Color(0xFFF5B301)
        else -> Color(0xFFE53935)
    }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val pulseAlpha by rememberInfiniteTransition(label = "goal-ring-dot").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "goal-ring-dot-alpha"
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = (strokeWidth / 2f + 1.dp).toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val progressSweep = 360f * remainingProgress
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = progressSweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke
            )
            if (active && progressSweep > 1f) {
                val angle = Math.toRadians((-90f + progressSweep).toDouble())
                val radius = arcSize.width / 2f
                val centerPoint = Offset(inset + radius, inset + radius)
                val headPoint = Offset(
                    x = centerPoint.x + cos(angle).toFloat() * radius,
                    y = centerPoint.y + sin(angle).toFloat() * radius
                )
                drawCircle(color = color.copy(alpha = 0.18f * pulseAlpha), radius = strokeWidth.toPx() * 2.1f, center = headPoint)
                drawCircle(color = color.copy(alpha = 0.65f + 0.35f * pulseAlpha), radius = strokeWidth.toPx() * 1.05f, center = headPoint)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(center, fontWeight = FontWeight.Bold)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
    }
}

fun batteryText(percent: Int?): String {
    return percent?.let { "电量 $it%" } ?: "电量未知"
}

fun MainUiState.batteryFor(address: String): Int? {
    return batteryPercents[address.uppercase()]
}

fun MainUiState.isDeviceActive(address: String): Boolean {
    return activeSessions.any { it.deviceAddress.equals(address, ignoreCase = true) }
}

fun audioSourceText(state: MainUiState): String {
    val info = state.currentAudioInfo ?: return "音频来源未知"
    return if (info.title.isBlank()) info.appName else "${info.appName} 播放中"
}

fun targetDeviceTitle(state: MainUiState): String {
    return when (state.targetDevices.size) {
        0 -> "未选择"
        1 -> state.targetDevices.first().name
        else -> "${state.targetDevices.size} 个设备：${state.targetDevices.joinToString("、") { it.name }}"
    }
}

fun MainUiState.todayRecords(): List<UsageRecord> {
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return (calendarRecords + history)
        .distinctBy { it.id }
        .filter { it.startTime < end && it.endTime > start }
        .sortedBy { it.startTime }
}

fun MainUiState.todayTimelineRecords(): List<UsageRecord> {
    val now = System.currentTimeMillis()
    val activeRecords = activeSessions.mapIndexed { index, session ->
        session.toActiveUsageRecord(
            id = -(index + 1L),
            endTime = now,
            audioAppName = currentAudioInfo?.appName.orEmpty(),
            audioPackageName = currentAudioInfo?.packageName.orEmpty(),
            mediaTitle = currentAudioInfo?.title.orEmpty()
        )
    }
    return (todayRecords() + activeRecords)
        .distinctBy { it.id }
        .sortedBy { it.startTime }
}

val MainUiState.todayRemainingProgress: Float
    get() = if (dailyLimitMillis <= 0) {
        0f
    } else {
        1f - (todayMillis.toFloat() / dailyLimitMillis).coerceIn(0f, 1f)
    }

private fun ActiveSessionInfo.toActiveUsageRecord(
    id: Long,
    endTime: Long,
    audioAppName: String,
    audioPackageName: String,
    mediaTitle: String
): UsageRecord {
    return UsageRecord(
        id = id,
        deviceName = deviceName,
        deviceAddress = deviceAddress,
        startTime = startTime,
        endTime = endTime,
        durationMillis = (endTime - startTime).coerceAtLeast(0),
        audioAppPackage = audioPackageName,
        audioAppName = audioAppName,
        mediaTitleSnapshot = mediaTitle
    )
}

private data class RingAwakeInterval(val startMinute: Int, val endMinute: Int) {
    val duration: Int get() = (endMinute - startMinute).coerceAtLeast(0)
}

private data class RingSegment(
    val record: UsageRecord,
    val startMinute: Int,
    val endMinute: Int
)

private data class RingSelection(
    val segment: RingSegment,
    val alignment: Alignment
)

private fun alignmentForRingPopup(tap: Offset, width: Float, height: Float): Alignment {
    val horizontalStart = tap.x < width / 2f
    val verticalTop = tap.y < height / 2f
    return when {
        horizontalStart && verticalTop -> Alignment.TopStart
        !horizontalStart && verticalTop -> Alignment.TopEnd
        horizontalStart && !verticalTop -> Alignment.BottomStart
        else -> Alignment.BottomEnd
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTodayRing(
    day: LocalDate,
    records: List<UsageRecord>,
    sleepEnabled: Boolean,
    sleepStartMinutes: Int,
    sleepEndMinutes: Int,
    rotation: Float,
    tickTextPaint: Paint
) {
    val intervals = ringAwakeIntervals(sleepEnabled, sleepStartMinutes, sleepEndMinutes)
    val totalAwake = intervals.sumOf { it.duration }.takeIf { it > 0 } ?: return
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = minOf(size.width, size.height) * 0.34f
    val strokeWidth = 34.dp.toPx()
    val arcSize = Size(radius * 2f, radius * 2f)
    val topLeft = Offset(center.x - radius, center.y - radius)
    val dayColor = Color(0xFFE6F3FF)
    val nightColor = Color(0xFFD8E2F0)
    val tickColor = Color(0xFF6F7F95)
    val palette = ringDevicePalette()
    val startAngle = -90f + rotation

    var minuteCursor = 0
    intervals.forEach { interval ->
        var minute = interval.startMinute
        while (minute < interval.endMinute) {
            val next = minOf(minute + 12, interval.endMinute)
            val sweep = ((next - minute).toFloat() / totalAwake) * 360f
            drawArc(
                color = if (((minute + next) / 2) in 360 until 1080) dayColor else nightColor,
                startAngle = startAngle + minuteCursor.toFloat() / totalAwake * 360f,
                sweepAngle = sweep + 0.5f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            minuteCursor += next - minute
            minute = next
        }
    }

    val smallTickStep = 6
    var tickIndex = 0
    intervals.forEach { interval ->
        var minute = interval.startMinute
        while (minute <= interval.endMinute) {
            val fraction = ringAwakeOffset(minute, intervals)?.let { it.toFloat() / totalAwake } ?: break
            val angle = Math.toRadians((startAngle + fraction * 360f).toDouble())
            val major = tickIndex % 10 == 0
            val outer = radius + strokeWidth / 2f - 4.dp.toPx()
            val inner = outer - if (major) 12.dp.toPx() else 6.dp.toPx()
            val p1 = Offset(center.x + kotlin.math.cos(angle).toFloat() * inner, center.y + kotlin.math.sin(angle).toFloat() * inner)
            val p2 = Offset(center.x + kotlin.math.cos(angle).toFloat() * outer, center.y + kotlin.math.sin(angle).toFloat() * outer)
            drawLine(tickColor.copy(alpha = if (major) 0.75f else 0.34f), p1, p2, strokeWidth = if (major) 1.8.dp.toPx() else 1.dp.toPx())
            if (major) {
                val textRadius = radius + strokeWidth / 2f - 19.dp.toPx()
                drawContext.canvas.nativeCanvas.drawText(
                    (minute / 60).coerceIn(0, 23).toString(),
                    center.x + kotlin.math.cos(angle).toFloat() * textRadius,
                    center.y + kotlin.math.sin(angle).toFloat() * textRadius + 4.dp.toPx(),
                    tickTextPaint
                )
            }
            minute += smallTickStep
            tickIndex++
        }
    }

    ringSegments(day, records, intervals).forEach { segment ->
        val startOffset = ringAwakeOffset(segment.startMinute, intervals) ?: return@forEach
        val endOffset = ringAwakeOffset(segment.endMinute, intervals) ?: return@forEach
        val color = palette[Math.floorMod(segment.record.deviceAddress.hashCode(), palette.size)]
        drawArc(
            color = color.copy(alpha = 0.82f),
            startAngle = startAngle + startOffset.toFloat() / totalAwake * 360f,
            sweepAngle = ((endOffset - startOffset).toFloat() / totalAwake * 360f).coerceAtLeast(1.5f),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth * 0.76f, cap = StrokeCap.Butt)
        )
    }
}

private fun hitRingSegment(
    tap: Offset,
    canvasSize: Size,
    day: LocalDate,
    records: List<UsageRecord>,
    sleepEnabled: Boolean,
    sleepStartMinutes: Int,
    sleepEndMinutes: Int,
    rotation: Float
): RingSegment? {
    val intervals = ringAwakeIntervals(sleepEnabled, sleepStartMinutes, sleepEndMinutes)
    val totalAwake = intervals.sumOf { it.duration }.takeIf { it > 0 } ?: return null
    val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
    val radius = minOf(canvasSize.width, canvasSize.height) * 0.34f
    val distance = kotlin.math.hypot(tap.x - center.x, tap.y - center.y)
    if (distance !in (radius - 28f)..(radius + 28f)) return null
    val rawAngle = Math.toDegrees(kotlin.math.atan2((tap.y - center.y).toDouble(), (tap.x - center.x).toDouble())).toFloat()
    val normalized = ((rawAngle - (-90f + rotation)) % 360f + 360f) % 360f
    val awakeMinuteOffset = (normalized / 360f * totalAwake).toInt()
    return ringSegments(day, records, intervals).firstOrNull { segment ->
        val start = ringAwakeOffset(segment.startMinute, intervals) ?: return@firstOrNull false
        val end = ringAwakeOffset(segment.endMinute, intervals) ?: return@firstOrNull false
        awakeMinuteOffset in start..end
    }
}

private fun ringDevicePalette(): List<Color> = listOf(
    Color(0xFF2563EB),
    Color(0xFF14B8A6),
    Color(0xFFF97316),
    Color(0xFFE11D48),
    Color(0xFF7C3AED),
    Color(0xFF16A34A)
)

private fun ringAwakeIntervals(enabled: Boolean, sleepStartMinutes: Int, sleepEndMinutes: Int): List<RingAwakeInterval> {
    val start = sleepStartMinutes.coerceIn(0, 1439)
    val end = sleepEndMinutes.coerceIn(0, 1439)
    if (!enabled || start == end) return listOf(RingAwakeInterval(0, 1440))
    return if (start < end) {
        buildList {
            if (start > 0) add(RingAwakeInterval(0, start))
            if (end < 1440) add(RingAwakeInterval(end, 1440))
        }
    } else {
        listOf(RingAwakeInterval(end, start))
    }.ifEmpty { listOf(RingAwakeInterval(0, 1440)) }
}

private fun ringAwakeOffset(minute: Int, intervals: List<RingAwakeInterval>): Int? {
    var offset = 0
    intervals.forEach { interval ->
        if (minute in interval.startMinute..interval.endMinute) {
            return offset + (minute - interval.startMinute).coerceIn(0, interval.duration)
        }
        offset += interval.duration
    }
    return null
}

private fun ringSegments(day: LocalDate, records: List<UsageRecord>, intervals: List<RingAwakeInterval>): List<RingSegment> {
    val zone = ZoneId.systemDefault()
    val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return records.filter { it.startTime < dayEnd && it.endTime > dayStart }.flatMap { record ->
        val startMinute = minuteOfDayForRing(maxOf(record.startTime, dayStart), day, zone)
        val endMinute = if (record.endTime >= dayEnd) 1440 else minuteOfDayForRing(minOf(record.endTime, dayEnd), day, zone)
        intervals.mapNotNull { interval ->
            val start = maxOf(startMinute, interval.startMinute)
            val end = minOf(endMinute, interval.endMinute)
            if (end > start) RingSegment(record, start, end) else null
        }
    }
}

private fun minuteOfDayForRing(timeMillis: Long, day: LocalDate, zone: ZoneId): Int {
    val time = Instant.ofEpochMilli(timeMillis).atZone(zone).toLocalDateTime()
    if (time.toLocalDate().isBefore(day)) return 0
    if (time.toLocalDate().isAfter(day)) return 1440
    return (time.hour * 60 + time.minute).coerceIn(0, 1439)
}

fun MainUiState.weekRecords(): List<UsageRecord> {
    val weekStart = LocalDate.now().with(DayOfWeek.MONDAY)
    val zone = ZoneId.systemDefault()
    val start = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = weekStart.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
    return (calendarRecords + history)
        .distinctBy { it.id }
        .filter { it.startTime < end && it.endTime > start }
        .sortedBy { it.startTime }
}

fun formatDuration(durationMillis: Long): String {
    val totalMinutes = durationMillis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        "%d小时%02d分".format(hours, minutes)
    } else {
        "%d分".format(minutes)
    }
}

fun formatDurationCompact(durationMillis: Long): String {
    val totalMinutes = durationMillis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h${minutes}m" else "${minutes}m"
}

fun formatMinutesOfDay(minutes: Int): String {
    val normalized = ((minutes % (24 * 60)) + (24 * 60)) % (24 * 60)
    return "%02d:%02d".format(normalized / 60, normalized % 60)
}

fun formatTime(timeMillis: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timeMillis))
}

fun formatDateTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timeMillis))
}
