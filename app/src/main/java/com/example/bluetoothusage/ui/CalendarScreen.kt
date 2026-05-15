package com.example.bluetoothusage.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.bluetoothusage.data.UsageRecord
import com.example.bluetoothusage.repository.DailyUsage
import com.example.bluetoothusage.viewmodel.MainUiState
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onUpdateRecord: (UsageRecord, Long, Long, String) -> Unit
) {
    var selectedDay by remember { mutableStateOf<DailyUsage?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("使用日历") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }
            )
        }
    ) { padding ->
        CalendarContent(
            state = state,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
            onDaySelected = { selectedDay = it },
            modifier = Modifier.padding(padding)
        )
    }

    selectedDay?.let { day ->
        DayUsageDialog(
            day = day,
            records = state.calendarRecords.recordsForDay(day.date),
            sleepEnabled = state.sleepEnabled,
            sleepStartMinutes = state.sleepStartMinutes,
            sleepEndMinutes = state.sleepEndMinutes,
            onUpdateRecord = onUpdateRecord,
            onDismiss = { selectedDay = null }
        )
    }
}

@Composable
fun CalendarContent(
    state: MainUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDaySelected: (DailyUsage) -> Unit,
    modifier: Modifier = Modifier
) {
    var showWeekView by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            CalendarModeHeader(
                monthText = state.calendarMonth.format(DateTimeFormatter.ofPattern("yyyy年 MM月")),
                showWeekView = showWeekView,
                onShowWeekView = { showWeekView = true },
                onShowMonthView = { showWeekView = false },
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth
            )
        }
        if (showWeekView) {
            item {
                WeekOverview(
                    startDay = LocalDate.now().with(DayOfWeek.MONDAY),
                    records = state.weekRecords(),
                    weekMillis = state.weekMillis,
                    dailyLimitMillis = state.dailyLimitMillis,
                    sleepEnabled = state.sleepEnabled,
                    sleepStartMinutes = state.sleepStartMinutes,
                    sleepEndMinutes = state.sleepEndMinutes
                )
            }
        } else {
            item {
                CalendarGrid(
                    days = state.calendarDays,
                    dailyLimitMillis = state.dailyLimitMillis,
                    onDayClick = onDaySelected
                )
            }
        }
    }
}

@Composable
fun CalendarPage(
    state: MainUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onUpdateRecord: (UsageRecord, Long, Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedDay by remember { mutableStateOf<DailyUsage?>(null) }
    CalendarContent(
        state = state,
        onPreviousMonth = onPreviousMonth,
        onNextMonth = onNextMonth,
        onDaySelected = { selectedDay = it },
        modifier = modifier
    )
    selectedDay?.let { day ->
        DayUsageDialog(
            day = day,
            records = state.calendarRecords.recordsForDay(day.date),
            sleepEnabled = state.sleepEnabled,
            sleepStartMinutes = state.sleepStartMinutes,
            sleepEndMinutes = state.sleepEndMinutes,
            onUpdateRecord = onUpdateRecord,
            onDismiss = { selectedDay = null }
        )
    }
}

@Composable
private fun CalendarModeHeader(
    monthText: String,
    showWeekView: Boolean,
    onShowWeekView: () -> Unit,
    onShowMonthView: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onPreviousMonth, enabled = !showWeekView) { Text("上月") }
                Text(
                    if (showWeekView) "本周记录" else monthText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onNextMonth, enabled = !showWeekView) { Text("下月") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip(text = "周视图", selected = showWeekView, onClick = onShowWeekView)
                ModeChip(text = "月视图", selected = !showWeekView, onClick = onShowMonthView)
            }
        }
    }
}

@Composable
private fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun WeekOverview(
    startDay: LocalDate,
    records: List<UsageRecord>,
    weekMillis: Long,
    dailyLimitMillis: Long,
    sleepEnabled: Boolean,
    sleepStartMinutes: Int,
    sleepEndMinutes: Int
) {
    val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("本周累计", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatDurationCompact(weekMillis), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Text(
                    "日额度 ${formatDurationCompact(dailyLimitMillis)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                weekLabels.forEachIndexed { index, label ->
                    val day = startDay.plusDays(index.toLong())
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("周$label", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${day.monthValue}/${day.dayOfMonth}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        CompactDayStrip(
                            day = day,
                            records = records.recordsForDay(day),
                            sleepEnabled = sleepEnabled,
                            sleepStartMinutes = sleepStartMinutes,
                            sleepEndMinutes = sleepEndMinutes
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarGrid(
    days: List<DailyUsage>,
    dailyLimitMillis: Long,
    onDayClick: (DailyUsage) -> Unit
) {
    val first = days.firstOrNull()?.date ?: LocalDate.now().withDayOfMonth(1)
    val blanks = first.dayOfWeek.value - 1
    val cells = buildList<DailyUsage?> {
        repeat(blanks) { add(null) }
        addAll(days)
        while (size % 7 != 0) add(null)
    }
    val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                weekLabels.forEach {
                    Text(
                        text = it,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    week.forEach { day ->
                        CalendarDayCell(
                            day = day,
                            dailyLimitMillis = dailyLimitMillis,
                            onClick = { if (day != null) onDayClick(day) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: DailyUsage?,
    dailyLimitMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(0.72f)
            .then(if (day != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (day != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                SmallRing(
                    progress = if (dailyLimitMillis <= 0) 0f else day.durationMillis.toFloat() / dailyLimitMillis,
                    dayText = day.date.dayOfMonth.toString()
                )
                Text(
                    text = formatCalendarDuration(day.durationMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

@Composable
private fun SmallRing(progress: Float, dayText: String) {
    val color = if (progress >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            val inset = 4.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = stroke)
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = stroke)
        }
        Text(dayText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DayUsageDialog(
    day: DailyUsage,
    records: List<UsageRecord>,
    sleepEnabled: Boolean,
    sleepStartMinutes: Int,
    sleepEndMinutes: Int,
    onUpdateRecord: (UsageRecord, Long, Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    val dialogContainerColor = MaterialTheme.colorScheme.surface
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogContainerColor,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        title = {
            Text(day.date.format(DateTimeFormatter.ofPattern("M月d日 使用详情")))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("当天总使用时长：${formatDuration(day.durationMillis)}", fontWeight = FontWeight.SemiBold)
                SpectrumTimeline(
                    day = day.date,
                    records = records,
                    sleepEnabled = sleepEnabled,
                    sleepStartMinutes = sleepStartMinutes,
                    sleepEndMinutes = sleepEndMinutes,
                    onUpdateRecord = onUpdateRecord
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
fun WeekUsageDialog(
    startDay: LocalDate,
    records: List<UsageRecord>,
    sleepEnabled: Boolean,
    sleepStartMinutes: Int,
    sleepEndMinutes: Int,
    onDismiss: () -> Unit
) {
    val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text("本周使用条带") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                weekLabels.forEachIndexed { index, label ->
                    val day = startDay.plusDays(index.toLong())
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "周$label",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${day.monthValue}/${day.dayOfMonth}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        CompactDayStrip(
                            day = day,
                            records = records.recordsForDay(day),
                            sleepEnabled = sleepEnabled,
                            sleepStartMinutes = sleepStartMinutes,
                            sleepEndMinutes = sleepEndMinutes
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun SpectrumTimeline(
    day: LocalDate,
    records: List<UsageRecord>,
    sleepEnabled: Boolean,
    sleepStartMinutes: Int,
    sleepEndMinutes: Int,
    onUpdateRecord: (UsageRecord, Long, Long, String) -> Unit = { _, _, _, _ -> }
) = InteractiveUsageTimeline(
    day = day,
    records = records,
    sleepEnabled = sleepEnabled,
    sleepStartMinutes = sleepStartMinutes,
    sleepEndMinutes = sleepEndMinutes,
    onUpdateRecord = onUpdateRecord,
    modifier = Modifier
        .fillMaxWidth()
        .height(460.dp)
)

@Composable
fun InteractiveUsageTimeline(
    day: LocalDate,
    records: List<UsageRecord>,
    sleepEnabled: Boolean,
    sleepStartMinutes: Int,
    sleepEndMinutes: Int,
    modifier: Modifier = Modifier,
    initialWindowStartMinute: Int? = null,
    initialWindowEndMinute: Int? = null,
    edgeFadeColor: Color = MaterialTheme.colorScheme.surface,
    minZoom: Float = 0.88f,
    maxZoom: Float = 5f,
    doubleTapZoomToLatest: Boolean = false,
    initialAnchorLatest: Boolean = false,
    onUpdateRecord: (UsageRecord, Long, Long, String) -> Unit = { _, _, _, _ -> }
) {
    var zoom by remember { mutableFloatStateOf(minZoom) }
    var zoomAnimationJob by remember { mutableStateOf<Job?>(null) }
    var flingAnimationJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    var offsetY by remember { mutableFloatStateOf(0f) }
    var labelOffsetY by remember { mutableFloatStateOf(0f) }
    var labelLayerDirection by remember { mutableFloatStateOf(-1f) }
    var viewportHeight by remember { mutableFloatStateOf(0f) }
    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var canvasHeight by remember { mutableFloatStateOf(0f) }
    var windowInitialized by remember(day) { mutableStateOf(false) }
    var initialWindowLock by remember(day) { mutableStateOf<TimelineOffsetBounds?>(null) }
    var userInteracted by remember(day) { mutableStateOf(false) }
    var anchoredLatestRecordKey by remember(day) { mutableStateOf<String?>(null) }
    var activeExpandedLabelKey by remember(day) { mutableStateOf<String?>(null) }
    var renderedExpandedLabelKey by remember(day) { mutableStateOf<String?>(null) }
    var editingRecord by remember(day) { mutableStateOf<UsageRecord?>(null) }
    var pendingAnimatedSelectionAction by remember(day) { mutableStateOf<TimelineSelectionAction?>(null) }
    var currentMinuteOfDay by remember(day) { mutableStateOf(currentMinuteOfDay()) }
    val density = LocalDensity.current
    val dpPx = remember(density) { with(density) { 1.dp.toPx() } }
    val timelineInsetPx = remember(density) { with(density) { 18.dp.toPx() } }
    val zone = ZoneId.systemDefault()
    val palette = devicePalette()
    val trackDayColor = Color(0xFFE8F3FF)
    val trackNightColor = Color(0xFFDCE6F3)
    val tickColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val boundaryColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    val labelContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    val labelBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)
    val fadeColor = edgeFadeColor
    val labelColor = MaterialTheme.colorScheme.onSurface
    val supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
    val defaultLabelStyle = TimelineLabelStyle(
        background = labelContainerColor,
        border = labelBorderColor,
        text = labelColor,
        supportingText = supportingColor,
        appText = Color(0xFF0066CC)
    )
    
    val hourLabelPaint = remember(density, supportingColor) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = supportingColor.toArgb()
            textSize = with(density) { 10.sp.toPx() }
            textAlign = Paint.Align.RIGHT
        }
    }
    val titlePaint = remember(density, labelColor) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor.toArgb()
            textSize = with(density) { 14.sp.toPx() }
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    val detailPaint = remember(density, supportingColor) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = supportingColor.toArgb()
            textSize = with(density) { 11.sp.toPx() }
        }
    }
    val appNamePaint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFF0066CC).toArgb()
            textSize = with(density) { 11.sp.toPx() }
        }
    }
    val boundaryTimePaint = remember(density, supportingColor) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = supportingColor.copy(alpha = 0.88f).toArgb()
            textSize = with(density) { 9.sp.toPx() }
            textAlign = Paint.Align.LEFT
        }
    }
    fun isMinimumZoom(zoomValue: Float): Boolean = zoomValue <= minZoom + 0.025f
    fun timelineTopFor(zoomValue: Float): Float {
        val scaledHeight = viewportHeight * zoomValue
        return timelineInsetPx + (viewportHeight - scaledHeight).coerceAtLeast(0f) / 2f
    }
    fun visualOffsetFor(zoomValue: Float, storedOffset: Float): Float {
        return if (isMinimumZoom(zoomValue) || zoomValue <= 1f) 0f else storedOffset
    }
    fun contentYAtScreen(screenY: Float, zoomValue: Float, storedOffset: Float): Float {
        val safeZoom = zoomValue.takeIf { it > 0f } ?: minZoom
        return (screenY - timelineTopFor(safeZoom) - visualOffsetFor(safeZoom, storedOffset)) / safeZoom
    }
    fun offsetForFocal(screenY: Float, contentY: Float, zoomValue: Float): Float {
        if (isMinimumZoom(zoomValue) || zoomValue <= 1f) return 0f
        return screenY - timelineTopFor(zoomValue) - contentY * zoomValue
    }
    val sortedRecords = remember(records, day) {
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        records
            .filter { it.startTime < dayEnd && it.endTime > dayStart }
            .sortedBy { it.startTime }
            .dedupeTimelineRecords()
    }
    val awakeIntervals = remember(sleepEnabled, sleepStartMinutes, sleepEndMinutes) {
        awakeIntervals(sleepEnabled, sleepStartMinutes, sleepEndMinutes)
    }
    val visibleSegments = remember(sortedRecords, day, awakeIntervals) {
        sortedRecords.flatMap { record ->
            record.awakeSegments(day, zone, awakeIntervals)
        }
    }
    LaunchedEffect(day) {
        while (true) {
            currentMinuteOfDay = currentMinuteOfDay()
            delay(60_000L)
        }
    }
    val (labelMinOffset, labelMaxOffset) = calculateTimelineLabelOffsetBounds(
        segments = visibleSegments,
        awakeIntervals = awakeIntervals,
        canvasHeight = canvasHeight,
        contentHeight = viewportHeight,
        timelineInsetPx = timelineInsetPx,
        minZoom = minZoom,
        dpPx = dpPx
    )
    fun timelineBoundsFor(zoomValue: Float): TimelineOffsetBounds? = calculateUsageTimelineOffsetBounds(
        contentHeight = viewportHeight,
        zoom = zoomValue
    )
    val timelineBounds = timelineBoundsFor(zoom)
    fun applyDragDelta(delta: Float): Float {
        if (isMinimumZoom(zoom)) {
            val next = (labelOffsetY + delta).coerceIn(labelMinOffset, labelMaxOffset)
            val consumed = next - labelOffsetY
            labelOffsetY = next
            if (consumed != 0f) labelLayerDirection = consumed
            return consumed
        } else {
            val currentZoom = zoom
            val next = clampTimelineOffset(offsetY + delta, currentZoom, viewportHeight, timelineBoundsFor(currentZoom))
            val consumed = next - offsetY
            offsetY = next
            return consumed
        }
    }

    fun canDragTimeline(): Boolean {
        return !isMinimumZoom(zoom) || labelMinOffset < 0f || labelMaxOffset > 0f
    }

    fun endPinch() {
        offsetY = clampTimelineOffset(offsetY, zoom, viewportHeight, timelineBoundsFor(zoom))
        if (isMinimumZoom(zoom) || zoom <= 1f) {
            offsetY = 0f
        }
    }

    fun setZoomImmediately(value: Float) {
        zoomAnimationJob?.cancel()
        flingAnimationJob?.cancel()
        zoomAnimationJob = null
        flingAnimationJob = null
        zoom = value.coerceIn(minZoom, maxZoom)
    }

    fun animateZoomTo(value: Float, targetOffsetY: Float = offsetY) {
        val startZoom = zoom
        val endZoom = value.coerceIn(minZoom, maxZoom)
        val startOffset = offsetY
        val endOffset = if (isMinimumZoom(endZoom) || endZoom <= 1f) {
            0f
        } else {
            clampTimelineOffset(targetOffsetY, endZoom, viewportHeight, timelineBoundsFor(endZoom))
        }
        zoomAnimationJob?.cancel()
        flingAnimationJob?.cancel()
        zoomAnimationJob = scope.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(durationMillis = 360)
            ) { fraction, _ ->
                val animatedZoom = startZoom + (endZoom - startZoom) * fraction
                val animatedOffset = startOffset + (endOffset - startOffset) * fraction
                zoom = animatedZoom
                offsetY = if (isMinimumZoom(animatedZoom) || animatedZoom <= 1f) {
                    0f
                } else {
                    clampTimelineOffset(
                        animatedOffset,
                        animatedZoom,
                        viewportHeight,
                        timelineBoundsFor(animatedZoom)
                    )
                }
            }
            zoom = endZoom
            offsetY = endOffset
            zoomAnimationJob = null
        }
    }

    fun flingTimeline(velocityY: Float) {
        if (isMinimumZoom(zoom) || abs(velocityY) < 80f) return
        val currentZoom = zoom
        val bounds = timelineBoundsFor(currentZoom)
        val start = offsetY
        val target = clampTimelineOffset(
            start + velocityY * 0.28f,
            currentZoom,
            viewportHeight,
            bounds
        )
        if (abs(target - start) < 1f) return
        val duration = ((abs(target - start) / abs(velocityY)) * 1700f)
            .coerceIn(180f, 620f)
            .toInt()
        flingAnimationJob?.cancel()
        flingAnimationJob = scope.launch {
            animate(
                initialValue = start,
                targetValue = target,
                initialVelocity = velocityY,
                animationSpec = tween(durationMillis = duration)
            ) { value, _ ->
                offsetY = clampTimelineOffset(value, currentZoom, viewportHeight, bounds)
            }
            flingAnimationJob = null
        }
    }

    val latestRecordKey = remember(sortedRecords) {
        sortedRecords.maxByOrNull { it.endTime }?.let { "${it.id}:${it.endTime}" }
    }
    val expandedProgress by animateFloatAsState(
        targetValue = if (activeExpandedLabelKey != null) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "timeline-label-expand"
    )
    val autoExpandLabels = !isMinimumZoom(zoom) && zoom >= 2.35f
    val autoExpandProgress by animateFloatAsState(
        targetValue = if (autoExpandLabels) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "timeline-label-auto-expand"
    )
    val positionedLabels = buildTimelinePositionedLabels(
        segments = visibleSegments,
        awakeIntervals = awakeIntervals,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
        contentHeight = viewportHeight,
        zoom = zoom,
        timelineInsetPx = timelineTopFor(zoom),
        offsetY = visualOffsetFor(zoom, offsetY),
        labelOffsetY = if (isMinimumZoom(zoom)) labelOffsetY.coerceIn(labelMinOffset, labelMaxOffset) else 0f,
        stackLabels = isMinimumZoom(zoom),
        labelLayerDirection = labelLayerDirection,
        dpPx = dpPx,
        titlePaint = titlePaint,
        detailPaint = detailPaint,
        appNamePaint = appNamePaint,
        labelStyle = defaultLabelStyle,
        expandedLabelKey = renderedExpandedLabelKey,
        expandedProgress = expandedProgress,
        autoExpandProgress = autoExpandProgress,
        viewportFadeHeight = canvasHeight
    )
    val latestPositionedLabelsState = rememberUpdatedState(positionedLabels)

    fun expandLabel(item: TimelineLabelDrawItem) {
        renderedExpandedLabelKey = item.key
        activeExpandedLabelKey = item.key
    }

    fun collapseLabel() {
        activeExpandedLabelKey = null
    }

    fun applyPendingSelectionAction(action: TimelineSelectionAction?) {
        when (action) {
            null -> Unit
            TimelineSelectionAction.Collapse -> collapseLabel()
            is TimelineSelectionAction.Expand -> positionedLabels
                .firstOrNull { it.item.key == action.labelKey }
                ?.item
                ?.let(::expandLabel)
        }
    }

    fun performSelectionAction(action: TimelineSelectionAction?) {
        when (action) {
            null -> Unit
            TimelineSelectionAction.Collapse -> collapseLabel()
            is TimelineSelectionAction.Expand -> {
                if (activeExpandedLabelKey != null && activeExpandedLabelKey != action.labelKey) {
                    pendingAnimatedSelectionAction = action
                    collapseLabel()
                } else {
                    pendingAnimatedSelectionAction = null
                    applyPendingSelectionAction(action)
                }
            }
        }
    }

    fun requestSelectionAction(action: TimelineSelectionAction) {
        performSelectionAction(action)
    }

    LaunchedEffect(activeExpandedLabelKey, renderedExpandedLabelKey, expandedProgress) {
        if (
            activeExpandedLabelKey == null &&
            renderedExpandedLabelKey != null &&
            pendingAnimatedSelectionAction is TimelineSelectionAction.Expand &&
            expandedProgress <= 0.45f
        ) {
            val nextAction = pendingAnimatedSelectionAction
            renderedExpandedLabelKey = null
            pendingAnimatedSelectionAction = null
            applyPendingSelectionAction(nextAction)
        } else if (activeExpandedLabelKey == null && renderedExpandedLabelKey != null && expandedProgress <= 0.01f) {
            val nextAction = pendingAnimatedSelectionAction
            renderedExpandedLabelKey = null
            pendingAnimatedSelectionAction = null
            if (nextAction != null) {
                applyPendingSelectionAction(nextAction)
            }
        }
    }

    LaunchedEffect(sortedRecords) {
        if (renderedExpandedLabelKey != null && positionedLabels.none { it.item.key == renderedExpandedLabelKey }) {
            activeExpandedLabelKey = null
            renderedExpandedLabelKey = null
            pendingAnimatedSelectionAction = null
        }
    }

    fun anchorToLatestUsage(immediate: Boolean) {
        if (!initialAnchorLatest || userInteracted || canvasHeight <= 0f || viewportHeight <= 0f) return
        val latestY = latestUsageContentY(
            day = day,
            records = sortedRecords,
            sleepEnabled = sleepEnabled,
            sleepStartMinutes = sleepStartMinutes,
            sleepEndMinutes = sleepEndMinutes,
            contentHeight = viewportHeight
        ) ?: return
        val desiredZoom = maxOf(1.6f, minZoom).coerceAtMost(maxZoom)
        val targetOffset = clampTimelineOffset(
            canvasHeight * 0.56f - timelineTopFor(desiredZoom) - latestY * desiredZoom,
            desiredZoom,
            viewportHeight,
            timelineBoundsFor(desiredZoom)
        )
        initialWindowLock = TimelineOffsetBounds(targetOffset, targetOffset)
        anchoredLatestRecordKey = latestRecordKey
        if (immediate) {
            setZoomImmediately(desiredZoom)
            offsetY = targetOffset
        } else {
            animateZoomTo(desiredZoom, targetOffset)
        }
    }

    LaunchedEffect(initialAnchorLatest, latestRecordKey, viewportHeight, canvasHeight, userInteracted) {
        if (
            initialAnchorLatest &&
            latestRecordKey != null &&
            latestRecordKey != anchoredLatestRecordKey &&
            !userInteracted &&
            viewportHeight > 0f &&
            canvasHeight > 0f
        ) {
            anchorToLatestUsage(immediate = !windowInitialized)
            windowInitialized = true
        }
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged {
                canvasWidth = it.width.toFloat()
                canvasHeight = it.height.toFloat()
                viewportHeight = (canvasHeight - timelineInsetPx * 2f).coerceAtLeast(1f)
                if (!windowInitialized && viewportHeight > 0f) {
                    if (initialAnchorLatest && latestRecordKey != null) {
                        anchorToLatestUsage(immediate = true)
                    } else if (initialWindowStartMinute != null && initialWindowEndMinute != null) {
                        val intervals = awakeIntervals(sleepEnabled, sleepStartMinutes, sleepEndMinutes)
                        val startY = minuteToAwakeY(initialWindowStartMinute, intervals, viewportHeight)
                        val endY = minuteToAwakeY(initialWindowEndMinute, intervals, viewportHeight)
                        if (startY != null && endY != null && endY > startY) {
                            val desiredZoom = (viewportHeight / (endY - startY)).coerceIn(minZoom, maxZoom)
                            val centerY = (startY + endY) / 2f
                            setZoomImmediately(desiredZoom)
                            val initialOffset = canvasHeight / 2f - timelineTopFor(desiredZoom) - centerY * desiredZoom
                            offsetY = clampTimelineOffset(initialOffset, desiredZoom, viewportHeight, null)
                            initialWindowLock = TimelineOffsetBounds(initialOffset, initialOffset)
                        }
                    }
                    windowInitialized = true
                }
                val effectiveBounds = initialWindowLock ?: timelineBounds
                offsetY = clampTimelineOffset(offsetY, zoom, viewportHeight, effectiveBounds)
                labelOffsetY = labelOffsetY.coerceIn(labelMinOffset, labelMaxOffset)
            }
            .pointerInput(
                day,
                records,
                minZoom,
                maxZoom,
                sleepEnabled,
                sleepStartMinutes,
                sleepEndMinutes,
                doubleTapZoomToLatest,
                canvasWidth,
                canvasHeight,
                viewportHeight,
                labelMinOffset
            ) {
                var lastTapTime = 0L
                var lastTapPosition = Offset.Zero
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    userInteracted = true
                    initialWindowLock = null
                    flingAnimationJob?.cancel()
                    flingAnimationJob = null
                    val downPosition = down.position
                    val labelColumnLeft = latestPositionedLabelsState.value.minOfOrNull { it.itemLeft } ?: (canvasWidth * 0.42f)
                    val downStartedInLabelArea = downPosition.x >= labelColumnLeft
                    var lastDragPosition = downPosition
                    var lastDragTime = down.uptimeMillis
                    var lastPointerTime = down.uptimeMillis
                var lastPosition = downPosition
                var dragVelocityY = 0f
                var maxMovement = 0f
                var handledAsPinch = false
                var handledAsDrag = false
                var handledAsLongPress = false
                var verticalDragLocked = false
                var horizontalDragRejected = false
                val longPressEditMillis = 360L
                var pinchStartedFromMinimum = false
                var pinchAnchorContentY: Float? = null
                val dragSlop = if (downStartedInLabelArea) 18f * dpPx else 8f * dpPx
                fun hitTimelineLabel(position: Offset): PositionedTimelineLabel? {
                    return latestPositionedLabelsState.value
                        .asReversed()
                        .firstOrNull { placed ->
                            val top = placed.item.labelTop
                            val bottom = placed.item.labelTop + placed.item.labelBoxHeight
                            val left = placed.itemLeft
                            val right = placed.itemLeft + placed.item.labelBoxWidth
                            position.x in left..right && position.y in top..bottom && placed.item.alpha > 0.001f
                        }
                }
                val labelPressedAtDown = hitTimelineLabel(downPosition)
                while (true) {
                        val event = if (
                            !handledAsLongPress &&
                            !handledAsPinch &&
                            labelPressedAtDown != null &&
                            maxMovement < dragSlop
                        ) {
                            val remainingMillis = (longPressEditMillis - (lastPointerTime - down.uptimeMillis)).coerceAtLeast(1L)
                            withTimeoutOrNull(remainingMillis) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }
                        if (event == null) {
                            editingRecord = labelPressedAtDown?.item?.record
                            requestSelectionAction(TimelineSelectionAction.Collapse)
                            handledAsLongPress = true
                            continue
                        }
                        lastPointerTime = event.changes.maxOfOrNull { it.uptimeMillis } ?: lastPointerTime
                        val pressedChanges = event.changes.filter { it.pressed }
                        if (pressedChanges.isNotEmpty()) {
                            val position = pressedChanges.first().position
                            lastPosition = position
                            val dx = position.x - downPosition.x
                            val dy = position.y - downPosition.y
                            maxMovement = maxOf(maxMovement, kotlin.math.sqrt(dx * dx + dy * dy))
                            if (
                                !verticalDragLocked &&
                                !horizontalDragRejected &&
                                !handledAsPinch &&
                                !handledAsLongPress &&
                                maxMovement > dragSlop
                            ) {
                                val absDx = abs(dx)
                                val absDy = abs(dy)
                                when {
                                    absDx > absDy * 1.12f -> horizontalDragRejected = true
                                    absDy > absDx * 1.12f -> verticalDragLocked = true
                                }
                            }
                        }
                        if (
                            !handledAsLongPress &&
                            !handledAsPinch &&
                            labelPressedAtDown != null &&
                            pressedChanges.size == 1 &&
                            maxMovement < dragSlop &&
                            lastPointerTime - down.uptimeMillis >= longPressEditMillis
                        ) {
                            editingRecord = labelPressedAtDown.item.record
                            requestSelectionAction(TimelineSelectionAction.Collapse)
                            handledAsLongPress = true
                            pressedChanges.forEach { it.consume() }
                        }
                        if (handledAsLongPress) {
                            pressedChanges.forEach { it.consume() }
                            if (event.changes.none { it.pressed }) break
                            continue
                        }
                        if (pressedChanges.size > 1) {
                            handledAsPinch = true
                            dragVelocityY = 0f
                            flingAnimationJob?.cancel()
                            flingAnimationJob = null
                            lastDragPosition = pressedChanges.first().position
                            lastDragTime = pressedChanges.first().uptimeMillis
                            val gestureZoom = event.calculateZoom()
                            if (gestureZoom.isFinite() && gestureZoom > 0f) {
                                zoomAnimationJob?.cancel()
                                zoomAnimationJob = null
                                val oldZoom = zoom
                                val focalY = pressedChanges.map { it.position.y }.average().toFloat()
                                val rawContentYAtFocal = contentYAtScreen(focalY, oldZoom, offsetY)
                                if (pinchAnchorContentY == null) {
                                    pinchStartedFromMinimum = isMinimumZoom(oldZoom) || oldZoom <= 1f
                                    pinchAnchorContentY = if (pinchStartedFromMinimum) {
                                        nearestUsageContentY(
                                            day = day,
                                            records = sortedRecords,
                                            sleepEnabled = sleepEnabled,
                                            sleepStartMinutes = sleepStartMinutes,
                                            sleepEndMinutes = sleepEndMinutes,
                                            contentY = rawContentYAtFocal,
                                            contentHeight = viewportHeight
                                        ) ?: rawContentYAtFocal
                                    } else {
                                        rawContentYAtFocal
                                    }
                                }
                                val contentYAtFocal = if (pinchStartedFromMinimum) {
                                    pinchAnchorContentY
                                } else {
                                    rawContentYAtFocal
                                }
                                val newZoom = (oldZoom * gestureZoom).coerceIn(minZoom, maxZoom)
                                zoom = newZoom
                                initialWindowLock = null
                                if (isMinimumZoom(newZoom) || newZoom <= 1f) {
                                    offsetY = 0f
                                } else {
                                    labelOffsetY = 0f
                                    offsetY = clampTimelineOffset(
                                        offsetForFocal(focalY, contentYAtFocal, newZoom),
                                        newZoom,
                                        viewportHeight,
                                        timelineBoundsFor(newZoom)
                                    )
                                }
                            }
                            event.changes.forEach { it.consume() }
                        } else if (
                            !handledAsPinch &&
                            !horizontalDragRejected &&
                            verticalDragLocked &&
                            pressedChanges.size == 1 &&
                            canDragTimeline()
                        ) {
                            val change = pressedChanges.first()
                            val position = change.position
                            val deltaY = position.y - lastDragPosition.y
                            val elapsedMillis = (change.uptimeMillis - lastDragTime).coerceAtLeast(1L)
                            dragVelocityY = (deltaY / elapsedMillis.toFloat()) * 1000f
                            lastDragPosition = position
                            lastDragTime = change.uptimeMillis
                            if (maxMovement > dragSlop && deltaY != 0f) {
                                initialWindowLock = null
                                val consumed = applyDragDelta(deltaY)
                                if (consumed != 0f) {
                                    handledAsDrag = true
                                    change.consume()
                                }
                            }
                        }
                        if (event.changes.none { it.pressed }) break
                    }
                    if (handledAsPinch) {
                        endPinch()
                    }
                    if (!handledAsPinch && handledAsDrag && !isMinimumZoom(zoom)) {
                        flingTimeline(dragVelocityY)
                    }
                    val tapMovementLimit = if (downStartedInLabelArea) 48f * dpPx else 36f * dpPx
                    if (!handledAsPinch && !handledAsDrag && !handledAsLongPress && maxMovement < tapMovementLimit) {
                        val tapOffset = lastPosition
                        val now = System.currentTimeMillis()
                        val timeSinceLastTap = now - lastTapTime
                        val lastDx = tapOffset.x - lastTapPosition.x
                        val lastDy = tapOffset.y - lastTapPosition.y
                        val lastTapDistance = kotlin.math.sqrt(lastDx * lastDx + lastDy * lastDy)
                        val tappedLabel = hitTimelineLabel(downPosition) ?: hitTimelineLabel(tapOffset)
                        if (tappedLabel != null) {
                            when {
                                renderedExpandedLabelKey == tappedLabel.item.key -> Unit
                                else -> requestSelectionAction(TimelineSelectionAction.Expand(tappedLabel.item.key))
                            }
                            lastTapTime = now
                            lastTapPosition = tapOffset
                        } else if (!downStartedInLabelArea && timeSinceLastTap < 300 && lastTapDistance < 100f) {
                            zoomAnimationJob?.cancel()
                            zoomAnimationJob = null
                            val oldZoom = zoom
                            val newZoom = if (oldZoom <= minZoom + 0.08f) maxZoom else minZoom
                            val rawContentYAtTap = contentYAtScreen(tapOffset.y, oldZoom, offsetY)
                            val zoomingIn = newZoom > minZoom + 0.08f
                            val latestContentY = if (zoomingIn && doubleTapZoomToLatest) {
                                latestUsageContentY(
                                    day = day,
                                    records = sortedRecords,
                                    sleepEnabled = sleepEnabled,
                                    sleepStartMinutes = sleepStartMinutes,
                                    sleepEndMinutes = sleepEndMinutes,
                                    contentHeight = viewportHeight
                                )
                            } else {
                                null
                            }
                            val contentYAtTap = latestContentY ?: rawContentYAtTap
                            val focalY = if (latestContentY != null) canvasHeight * 0.56f else tapOffset.y
                            initialWindowLock = null
                            val targetOffsetY = if (isMinimumZoom(newZoom) || newZoom <= 1f) {
                                0f
                            } else {
                                clampTimelineOffset(
                                    offsetForFocal(focalY, contentYAtTap, newZoom),
                                    newZoom,
                                    viewportHeight,
                                    timelineBoundsFor(newZoom)
                                )
                            }
                            if (isMinimumZoom(newZoom) || newZoom <= 1f) {
                                labelOffsetY = labelOffsetY.coerceIn(labelMinOffset, labelMaxOffset)
                            } else {
                                labelOffsetY = 0f
                            }
                            animateZoomTo(newZoom, targetOffsetY)
                            lastTapTime = 0L
                        } else {
                            if (!autoExpandLabels && renderedExpandedLabelKey != null) {
                                requestSelectionAction(TimelineSelectionAction.Collapse)
                            }
                            lastTapTime = now
                            lastTapPosition = tapOffset
                        }
                    }
                }
            }
        ) {
        val hasUsage = sortedRecords.isNotEmpty()
        val contentHeight = (size.height - timelineInsetPx * 2f).coerceAtLeast(1f)
        val drawZoom = zoom
        val drawOffsetY = visualOffsetFor(zoom, offsetY)
        val stripWidth = (34.dp.toPx() + (drawZoom - 1f) * 10.dp.toPx()).coerceAtMost(76.dp.toPx())
        val centerX = if (hasUsage) maxOf(84.dp.toPx(), size.width * 0.22f) else size.width / 2f
        val stripLeft = centerX - stripWidth / 2f
        val stripRight = centerX + stripWidth / 2f
        val corner = CornerRadius(14.dp.toPx(), 14.dp.toPx())
        val timelineHeight = contentHeight * drawZoom
        val timelineTop = timelineTopFor(drawZoom)
        fun transformY(baseY: Float): Float = timelineTop + baseY * drawZoom + drawOffsetY

        clipRect(left = 0f, top = 0f, right = size.width, bottom = size.height) {
            val stripPath = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = stripLeft,
                        top = timelineTop + drawOffsetY,
                        right = stripLeft + stripWidth,
                        bottom = timelineTop + drawOffsetY + timelineHeight,
                        cornerRadius = corner
                    )
                )
            }
            clipPath(stripPath) {
                drawRect(
                    color = trackNightColor,
                    topLeft = Offset(stripLeft, timelineTop + drawOffsetY),
                    size = Size(stripWidth, timelineHeight)
                )
                drawAwakeTrackBands(
                    intervals = awakeIntervals,
                    stripLeft = stripLeft,
                    stripWidth = stripWidth,
                    contentHeight = contentHeight,
                    transformY = ::transformY,
                    dayColor = trackDayColor,
                    nightColor = trackNightColor
                )
            }

            timelineLightTickMinutes(awakeIntervals).forEach { minute ->
                val y = transformY(minuteToAwakeY(minute, awakeIntervals, contentHeight) ?: return@forEach)
                if (y < -12.dp.toPx() || y > size.height + 12.dp.toPx()) return@forEach
                drawLine(
                    color = tickColor.copy(alpha = 0.14f),
                    start = Offset(stripLeft, y),
                    end = Offset(stripRight, y),
                    strokeWidth = 0.7f
                )
            }

            timelineTickMinutes(awakeIntervals).forEach { minute ->
                val y = transformY(minuteToAwakeY(minute, awakeIntervals, contentHeight) ?: return@forEach)
                val major = minute % 360 == 0 || awakeIntervals.any { minute == it.startMinute || minute == it.endMinute }
                if (y < -16.dp.toPx() || y > size.height + 16.dp.toPx()) return@forEach
                drawLine(
                    color = tickColor,
                    start = Offset(stripLeft + if (major) 0f else 7.dp.toPx(), y),
                    end = Offset(stripRight - if (major) 0f else 7.dp.toPx(), y),
                    strokeWidth = if (major) 1.8f else 1f
                )
                if (major) {
                    drawContext.canvas.nativeCanvas.drawText(
                        formatMinuteOfDay(minute),
                        stripLeft - 7.dp.toPx(),
                        (y + 4.dp.toPx()).coerceIn(10.dp.toPx(), size.height - 2.dp.toPx()),
                        hourLabelPaint
                    )
                }
            }

            if (day == LocalDate.now()) {
                minuteToAwakeY(currentMinuteOfDay, awakeIntervals, contentHeight)?.let { baseY ->
                    val y = transformY(baseY)
                    if (y in -24.dp.toPx()..(size.height + 24.dp.toPx())) {
                        val lineColor = Color(0xFF1478FF)
                        drawLine(
                            color = lineColor.copy(alpha = 0.72f),
                            start = Offset(stripLeft - 8.dp.toPx(), y),
                            end = Offset(stripRight + 8.dp.toPx(), y),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawCircle(
                            color = lineColor,
                            radius = 4.dp.toPx(),
                            center = Offset(stripRight + 8.dp.toPx(), y)
                        )
                    }
                }
            }

            val labelCorner = CornerRadius(10.dp.toPx(), 10.dp.toPx())
            var lastRightBoundaryY = -10_000f
            val boundaryGapProgress = ((zoom - 2.25f) / 2.75f).coerceIn(0f, 1f)
            val boundaryMinGap = 24.dp.toPx() - boundaryGapProgress * 12.dp.toPx()

            fun drawBoundaryTime(minute: Int, y: Float) {
                if (zoom < 2.25f || y < 12.dp.toPx() || y > size.height - 8.dp.toPx()) return
                val fade = ((zoom - 2.25f) / 0.8f).coerceIn(0f, 1f) * rangeAlpha(y, size.height)
                if (fade <= 0.05f) return
                if (y - lastRightBoundaryY < boundaryMinGap) return

                boundaryTimePaint.color = supportingColor.copy(alpha = 0.88f * fade).toArgb()
                boundaryTimePaint.textAlign = Paint.Align.LEFT
                drawContext.canvas.nativeCanvas.drawText(
                    formatMinuteOfDay(minute),
                    stripRight + 5.dp.toPx(),
                    y + 3.dp.toPx(),
                    boundaryTimePaint
                )
                lastRightBoundaryY = y
            }

            visibleSegments.forEach { segment ->
                val y1 = transformY(minuteToAwakeY(segment.startMinute, awakeIntervals, contentHeight) ?: return@forEach)
                val y2 = transformY(minuteToAwakeY(segment.endMinute, awakeIntervals, contentHeight) ?: return@forEach)
                if (y2 < -80.dp.toPx() || y1 > size.height + 80.dp.toPx()) return@forEach
                val baseColor = palette[Math.floorMod(segment.record.deviceAddress.hashCode(), palette.size)]
                val durationMinutes = (segment.endMinute - segment.startMinute).coerceAtLeast(1)
                val color = baseColor.copy(alpha = durationAlpha(durationMinutes))

                clipPath(stripPath) {
                    drawRect(
                        color = color,
                        topLeft = Offset(stripLeft, y1),
                        size = Size(stripWidth, (y2 - y1).coerceAtLeast(5.dp.toPx()))
                    )
                    drawLine(
                        color = boundaryColor,
                        start = Offset(stripLeft, y1),
                        end = Offset(stripRight, y1),
                        strokeWidth = 1.4.dp.toPx()
                    )
                    drawLine(
                        color = boundaryColor,
                        start = Offset(stripLeft, y2),
                        end = Offset(stripRight, y2),
                        strokeWidth = 1.4.dp.toPx()
                    )
                }
                drawBoundaryTime(segment.startMinute, y1)
                drawBoundaryTime(segment.endMinute, y2)
            }

            positionedLabels.forEach { placed ->
                if (placed.item.alpha <= 0.01f) return@forEach
                val connectorStartY = placed.item.connectorY
                drawLine(
                    color = placed.item.color.copy(alpha = 0.82f * placed.item.alpha),
                    start = Offset(stripRight, connectorStartY),
                    end = Offset(placed.itemLeft, placed.item.labelCenterY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            positionedLabels.forEach { placed ->
                val item = placed.item
                if (item.alpha <= 0.01f) return@forEach
                val contentProgress = item.expansionProgress
                drawRoundRect(
                    color = item.style.background.copy(alpha = 0.92f * item.alpha),
                    topLeft = Offset(placed.itemLeft, item.labelTop),
                    size = Size(item.labelBoxWidth, item.labelBoxHeight),
                    cornerRadius = labelCorner
                )
                drawRoundRect(
                    color = item.style.border.copy(alpha = 0.72f * item.alpha),
                    topLeft = Offset(placed.itemLeft, item.labelTop),
                    size = Size(item.labelBoxWidth, item.labelBoxHeight),
                    cornerRadius = labelCorner,
                    style = Stroke(width = 1.dp.toPx())
                )

                titlePaint.textAlign = Paint.Align.LEFT
                titlePaint.color = item.style.text.copy(alpha = item.alpha).toArgb()
                if (contentProgress > 0.05f) {
                    val titleBaseline = item.labelTop + lerpFloat(item.labelBoxHeight / 2f + 4.dp.toPx(), 17.dp.toPx(), contentProgress)
                    drawContext.canvas.nativeCanvas.drawText(item.title, placed.textX, titleBaseline, titlePaint)

                    val detailAlpha = item.alpha * contentProgress
                    detailPaint.color = item.style.supportingText.copy(alpha = detailAlpha).toArgb()
                    drawContext.canvas.nativeCanvas.drawText(item.detail, placed.textX, item.labelTop + 33.dp.toPx(), detailPaint)

                    if (item.hasAppName) {
                        appNamePaint.color = item.style.appText.copy(alpha = detailAlpha).toArgb()
                        drawContext.canvas.nativeCanvas.drawText(item.appName, placed.textX, item.labelTop + 49.dp.toPx(), appNamePaint)
                    }
                } else {
                    val collapsedTextBaseline = item.labelTop + item.labelBoxHeight / 2f + 4.dp.toPx()
                    drawContext.canvas.nativeCanvas.drawText(item.title, placed.textX, collapsedTextBaseline, titlePaint)
                }
            }

            val fadeHeight = 44.dp.toPx()
            val fadeAlpha = ((zoom - 1f) / 1.2f).coerceIn(0f, 1f)
            if (fadeAlpha <= 0f) return@clipRect
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(fadeColor.copy(alpha = fadeAlpha), fadeColor.copy(alpha = 0f)),
                    startY = 0f,
                    endY = fadeHeight
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, fadeHeight)
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(fadeColor.copy(alpha = 0f), fadeColor.copy(alpha = fadeAlpha)),
                    startY = size.height - fadeHeight,
                    endY = size.height
                ),
                topLeft = Offset(0f, size.height - fadeHeight),
                size = Size(size.width, fadeHeight)
            )
        }
        }

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
private fun CompactDayStrip(
    day: LocalDate,
    records: List<UsageRecord>,
    sleepEnabled: Boolean,
    sleepStartMinutes: Int,
    sleepEndMinutes: Int
) {
    val zone = ZoneId.systemDefault()
    val palette = devicePalette()
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val tickColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.36f)
    val boundaryColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)

    Canvas(
        modifier = Modifier
            .width(30.dp)
            .height(300.dp)
    ) {
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val awakeIntervals = awakeIntervals(sleepEnabled, sleepStartMinutes, sleepEndMinutes)

        drawRoundRect(
            color = trackColor,
            topLeft = Offset.Zero,
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
        )

        timelineTickMinutes(awakeIntervals).forEach { minute ->
            val y = minuteToAwakeY(minute, awakeIntervals, size.height) ?: return@forEach
            drawLine(
                color = tickColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
        }

        records
            .filter { it.startTime < dayEnd && it.endTime > dayStart }
            .sortedBy { it.startTime }
            .flatMap { it.awakeSegments(day, zone, awakeIntervals) }
            .forEach { segment ->
                val y1 = minuteToAwakeY(segment.startMinute, awakeIntervals, size.height) ?: return@forEach
                val y2 = minuteToAwakeY(segment.endMinute, awakeIntervals, size.height) ?: return@forEach
                val baseColor = palette[Math.floorMod(segment.record.deviceAddress.hashCode(), palette.size)]
                val color = baseColor.copy(alpha = durationAlpha(segment.endMinute - segment.startMinute))

                drawRect(
                    color = color,
                    topLeft = Offset(0f, y1),
                    size = Size(size.width, (y2 - y1).coerceAtLeast(4.dp.toPx()))
                )
                drawLine(
                    color = boundaryColor,
                    start = Offset(0f, y1),
                    end = Offset(size.width, y1),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = boundaryColor,
                    start = Offset(0f, y2),
                    end = Offset(size.width, y2),
                    strokeWidth = 1.dp.toPx()
                )
            }
    }
}

private fun List<UsageRecord>.recordsForDay(day: LocalDate): List<UsageRecord> {
    val zone = ZoneId.systemDefault()
    val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return filter { it.startTime < end && it.endTime > start }.sortedBy { it.startTime }.dedupeTimelineRecords()
}

private fun List<UsageRecord>.dedupeTimelineRecords(): List<UsageRecord> {
    val toleranceMillis = 5_000L
    val result = mutableListOf<UsageRecord>()
    sortedWith(compareBy<UsageRecord> { it.startTime }.thenByDescending { it.durationMillis }).forEach { record ->
        val duplicate = result.any { existing ->
            abs(existing.startTime - record.startTime) <= toleranceMillis &&
                abs(existing.endTime - record.endTime) <= toleranceMillis
        }
        if (!duplicate) result += record
    }
    return result.sortedBy { it.startTime }
}

private data class AwakeInterval(val startMinute: Int, val endMinute: Int) {
    val duration: Int get() = (endMinute - startMinute).coerceAtLeast(0)
}

private data class AwakeRecordSegment(
    val record: UsageRecord,
    val startMinute: Int,
    val endMinute: Int
) {
    val durationMillis: Long get() = (endMinute - startMinute).coerceAtLeast(0) * 60_000L
}

private data class TimelineLabelDrawItem(
    val key: String,
    val record: UsageRecord,
    val color: Color,
    val midY: Float,
    val segmentTopY: Float,
    val segmentBottomY: Float,
    val connectorY: Float,
    val labelCenterY: Float,
    val labelTop: Float,
    val labelBoxWidth: Float,
    val labelBoxHeight: Float,
    val expansionProgress: Float,
    val alpha: Float,
    val title: String,
    val detail: String,
    val appName: String,
    val hasAppName: Boolean,
    val style: TimelineLabelStyle,
    val startMinute: Int,
    val endMinute: Int
)

private data class PositionedTimelineLabel(
    val item: TimelineLabelDrawItem,
    val itemLeft: Float,
    val textX: Float
)

private data class TimelineLabelStyle(
    val background: Color,
    val border: Color,
    val text: Color,
    val supportingText: Color,
    val appText: Color
)

private sealed interface TimelineSelectionAction {
    data object Collapse : TimelineSelectionAction
    data class Expand(val labelKey: String) : TimelineSelectionAction
}

private fun buildTimelinePositionedLabels(
    segments: List<AwakeRecordSegment>,
    awakeIntervals: List<AwakeInterval>,
    canvasWidth: Float,
    canvasHeight: Float,
    contentHeight: Float,
    zoom: Float,
    timelineInsetPx: Float,
    offsetY: Float,
    labelOffsetY: Float,
    stackLabels: Boolean,
    labelLayerDirection: Float,
    dpPx: Float,
    titlePaint: Paint,
    detailPaint: Paint,
    appNamePaint: Paint,
    labelStyle: TimelineLabelStyle,
    expandedLabelKey: String?,
    expandedProgress: Float,
    autoExpandProgress: Float,
    viewportFadeHeight: Float
): List<PositionedTimelineLabel> {
    if (canvasWidth <= 0f || canvasHeight <= 0f || contentHeight <= 0f || segments.isEmpty()) return emptyList()
    val stripWidth = (34f * dpPx + (zoom - 1f) * 10f * dpPx).coerceAtMost(76f * dpPx)
    val centerX = maxOf(84f * dpPx, canvasWidth * 0.22f)
    val stripRight = centerX + stripWidth / 2f
    val labelPadding = 12f * dpPx
    val labelBoxLeft = maxOf(stripRight + 44f * dpPx, canvasWidth * 0.36f).coerceAtMost(canvasWidth - 132f * dpPx)
    val labelBoxRight = canvasWidth - 4f * dpPx
    val labelAvailableWidth = (labelBoxRight - labelBoxLeft).coerceAtLeast(84f * dpPx)
    fun transformY(baseY: Float): Float = timelineInsetPx + baseY * zoom + offsetY

    var nextRightY = 28f * dpPx
    val collapsedHeight = 32f * dpPx
    val rawItems = segments.mapNotNull { segment ->
        val startY = minuteToAwakeY(segment.startMinute, awakeIntervals, contentHeight) ?: return@mapNotNull null
        val endY = minuteToAwakeY(segment.endMinute, awakeIntervals, contentHeight) ?: return@mapNotNull null
        val y1 = transformY(startY)
        val y2 = transformY(endY)
        val midY = (y1 + y2) / 2f
        val segmentTopY = minOf(y1, y2)
        val segmentBottomY = maxOf(y1, y2)
        val connectorY = if (stackLabels) {
            midY
        } else if (segmentBottomY >= 0f && segmentTopY <= canvasHeight) {
            (segmentTopY.coerceIn(0f, canvasHeight) + segmentBottomY.coerceIn(0f, canvasHeight)) / 2f
        } else {
            midY.coerceIn(0f, canvasHeight)
        }
        val hasAppName = segment.record.audioAppName.isNotBlank()
        val title = formatDuration(segment.durationMillis)
        val detail = "${formatMinuteOfDay(segment.startMinute)} - ${formatMinuteOfDay(segment.endMinute)}"
        val appNameText = if (hasAppName) segment.record.audioAppName else ""
        val style = timelineLabelStyleFor(segment.record, labelStyle)
        val collapsedWidth = (titlePaint.measureText(title) + labelPadding * 2f).coerceIn(98f * dpPx, labelAvailableWidth)
        val expandedTextWidth = maxOf(
            titlePaint.measureText(title),
            detailPaint.measureText(detail),
            if (hasAppName) appNamePaint.measureText(appNameText) else 0f
        ) + labelPadding * 2f
        val expandedWidth = expandedTextWidth.coerceIn(collapsedWidth, labelAvailableWidth)
        val expandedHeight = if (hasAppName) 68f * dpPx else 52f * dpPx
        val labelKey = "${segment.record.id}:${segment.startMinute}:${segment.endMinute}"
        val progress = maxOf(autoExpandProgress, if (labelKey == expandedLabelKey) expandedProgress else 0f)
        val currentWidth = lerpFloat(collapsedWidth, expandedWidth, progress)
        val currentHeight = lerpFloat(collapsedHeight, expandedHeight, progress)
        val baseLabelY = if (stackLabels) maxOf(midY, nextRightY) else midY
        if (stackLabels) {
            nextRightY = baseLabelY + maxOf(40f * dpPx, currentHeight + 8f * dpPx)
        }
        val labelCenterY = baseLabelY + if (stackLabels) labelOffsetY else 0f
        val alpha = if (stackLabels) {
            rangeAlpha(labelCenterY, viewportFadeHeight)
        } else {
            segmentVisibilityAlpha(segmentTopY, segmentBottomY, viewportFadeHeight)
        }
        TimelineLabelDrawItem(
            key = labelKey,
            record = segment.record,
            color = segmentColor(segment),
            midY = midY,
            segmentTopY = segmentTopY,
            segmentBottomY = segmentBottomY,
            connectorY = connectorY,
            labelCenterY = labelCenterY,
            labelTop = labelCenterY - currentHeight / 2f,
            labelBoxWidth = currentWidth,
            labelBoxHeight = currentHeight,
            expansionProgress = progress,
            alpha = alpha,
            title = title,
            detail = detail,
            appName = appNameText,
            hasAppName = hasAppName,
            style = style,
            startMinute = segment.startMinute,
            endMinute = segment.endMinute
        )
    }
    val laidOutLabels = if (!stackLabels) {
        layoutZoomedTimelineLabelItems(
            items = rawItems,
            viewportHeight = canvasHeight,
            gap = 10f * dpPx,
            topPadding = 12f * dpPx,
            bottomPadding = 12f * dpPx
        )
    } else {
        layoutStackedTimelineLabelItems(
            items = rawItems,
            gap = 10f * dpPx,
            viewportHeight = canvasHeight
        )
    }
    val orderedLabels = if (stackLabels && labelLayerDirection > 0f) laidOutLabels.asReversed() else laidOutLabels
    return orderedLabels.map { item ->
        val itemLeft = (labelBoxRight - item.labelBoxWidth).coerceAtLeast(labelBoxLeft)
        PositionedTimelineLabel(
            item = item,
            itemLeft = itemLeft,
            textX = itemLeft + labelPadding
        )
    }
}

private fun layoutTimelineLabelItems(
    items: List<TimelineLabelDrawItem>,
    stackLabels: Boolean,
    viewportHeight: Float,
    gap: Float,
    topPadding: Float,
    bottomPadding: Float
): List<TimelineLabelDrawItem> {
    return if (stackLabels) {
        items
    } else {
        items.avoidTimelineLabelOverlap(
            viewportHeight = viewportHeight,
            gap = gap,
            topPadding = topPadding,
            bottomPadding = bottomPadding
        )
    }
}

private fun layoutStackedTimelineLabelItems(
    items: List<TimelineLabelDrawItem>,
    gap: Float,
    viewportHeight: Float
): List<TimelineLabelDrawItem> {
    if (items.size < 2) return items
    val sorted = items.sortedBy { it.labelCenterY }
    val centers = FloatArray(sorted.size) { index -> sorted[index].labelCenterY }
    val halfHeights = sorted.map { it.labelBoxHeight / 2f }

    for (i in 1 until sorted.size) {
        val minCenter = centers[i - 1] + halfHeights[i - 1] + gap + halfHeights[i]
        if (centers[i] < minCenter) centers[i] = minCenter
    }

    val laidOut = sorted.mapIndexed { index, item ->
        val center = centers[index]
        item.copy(
            labelCenterY = center,
            labelTop = center - item.labelBoxHeight / 2f,
            alpha = rangeAlpha(center, viewportHeight)
        )
    }.associateBy { it.key }

    return items.map { laidOut[it.key] ?: it }
}

private fun layoutZoomedTimelineLabelItems(
    items: List<TimelineLabelDrawItem>,
    viewportHeight: Float,
    gap: Float,
    topPadding: Float,
    bottomPadding: Float
): List<TimelineLabelDrawItem> {
    if (items.isEmpty() || viewportHeight <= 0f) return items
    val visible = items.filter { it.alpha > 0.01f }
    if (visible.isEmpty()) return items.map { it.copy(alpha = 0f) }

    val sorted = visible.sortedBy { it.labelCenterY }
    val centers = FloatArray(sorted.size)
    val halfHeights = sorted.map { it.labelBoxHeight / 2f }
    val topLimit = topPadding
    val bottomLimit = viewportHeight - bottomPadding

    sorted.forEachIndexed { index, item ->
        centers[index] = item.labelCenterY.coerceIn(
            topLimit + halfHeights[index],
            bottomLimit - halfHeights[index]
        )
    }

    for (i in 1 until sorted.size) {
        val minCenter = centers[i - 1] + halfHeights[i - 1] + gap + halfHeights[i]
        if (centers[i] < minCenter) centers[i] = minCenter
    }
    for (i in sorted.size - 2 downTo 0) {
        val maxCenter = centers[i + 1] - halfHeights[i + 1] - gap - halfHeights[i]
        if (centers[i] > maxCenter) centers[i] = maxCenter
    }

    val laidOut = sorted.mapIndexed { index, item ->
        val center = centers[index]
        val anchorDistance = abs(center - item.labelCenterY)
        val elasticAlpha = 1f - (anchorDistance / (viewportHeight * 0.55f)).coerceIn(0f, 0.7f)
        item.copy(
            labelCenterY = center,
            labelTop = center - item.labelBoxHeight / 2f,
            alpha = item.alpha * rangeAlpha(center, viewportHeight) * elasticAlpha
        )
    }.associateBy { it.key }

    return items.map { laidOut[it.key] ?: it.copy(alpha = 0f) }
}

private fun List<TimelineLabelDrawItem>.avoidTimelineLabelOverlap(
    viewportHeight: Float,
    gap: Float,
    topPadding: Float,
    bottomPadding: Float
): List<TimelineLabelDrawItem> {
    if (size < 2 || viewportHeight <= 0f) {
        return map { item ->
            val center = item.labelCenterY.coerceIn(
                topPadding + item.labelBoxHeight / 2f,
                viewportHeight - bottomPadding - item.labelBoxHeight / 2f
            )
            item.withLabelCenter(center, viewportHeight)
        }
    }

    val sorted = sortedBy { it.labelCenterY }
    val expandedIndex = sorted.indexOfFirst { it.labelBoxHeight > 36f }
    if (expandedIndex >= 0) {
        val halfHeights = sorted.map { it.labelBoxHeight / 2f }
        val centers = FloatArray(sorted.size)
        val expanded = sorted[expandedIndex]
        centers[expandedIndex] = expanded.labelCenterY.coerceIn(
            topPadding + halfHeights[expandedIndex],
            viewportHeight - bottomPadding - halfHeights[expandedIndex]
        )

        for (i in expandedIndex - 1 downTo 0) {
            val maxCenter = centers[i + 1] - halfHeights[i + 1] - gap - halfHeights[i]
            centers[i] = minOf(sorted[i].labelCenterY, maxCenter)
        }

        for (i in expandedIndex + 1 until sorted.size) {
            val minCenter = centers[i - 1] + halfHeights[i - 1] + gap + halfHeights[i]
            centers[i] = maxOf(sorted[i].labelCenterY, minCenter)
        }

        return sorted.mapIndexed { index, item ->
            item.withLabelCenter(centers[index], viewportHeight)
        }
    }

    val centers = FloatArray(sorted.size)
    val halfHeights = sorted.map { it.labelBoxHeight / 2f }

    centers[0] = (topPadding + halfHeights[0]).coerceAtLeast(sorted[0].labelCenterY)

    for (i in 1 until sorted.size) {
        val prevBottom = centers[i - 1] + halfHeights[i - 1]
        val minCenter = prevBottom + gap + halfHeights[i]
        centers[i] = maxOf(minCenter, sorted[i].labelCenterY)
    }

    val bottomLimit = viewportHeight - bottomPadding
    val lastIdx = sorted.size - 1
    val overflow = centers[lastIdx] + halfHeights[lastIdx] - bottomLimit
    if (overflow > 0f) {
        for (i in 0..lastIdx) {
            centers[i] -= overflow
        }
        // Don't push the first label above the top bound; clamp it back
        val firstMin = topPadding + halfHeights[0]
        if (centers[0] < firstMin) {
            val correction = firstMin - centers[0]
            for (i in 0..lastIdx) {
                centers[i] += correction
            }
        }
    }

    return sorted.mapIndexed { index, item ->
        item.withLabelCenter(centers[index], viewportHeight)
    }
}

private fun TimelineLabelDrawItem.withLabelCenter(center: Float, viewportHeight: Float): TimelineLabelDrawItem {
    return copy(
        labelCenterY = center,
        labelTop = center - labelBoxHeight / 2f,
        alpha = rangeAlpha(center, viewportHeight)
    )
}

private fun timelineLabelStyleFor(record: UsageRecord, defaultStyle: TimelineLabelStyle): TimelineLabelStyle {
    val source = "${record.audioAppPackage} ${record.audioAppName}".lowercase()
    return when {
        source.contains("bilibili") ||
            source.contains("哔哩") ||
            source.contains("b站") -> TimelineLabelStyle(
            background = Color(0xFFFFE8F1),
            border = Color(0xFFFF6FAE),
            text = Color(0xFF7A1744),
            supportingText = Color(0xFF9D4567),
            appText = Color(0xFFE34B8B)
        )
        source.contains("netease") ||
            source.contains("cloudmusic") ||
            source.contains("网易云") -> TimelineLabelStyle(
            background = Color(0xFFFFEBEB),
            border = Color(0xFFE53935),
            text = Color(0xFF7F1616),
            supportingText = Color(0xFFA33B3B),
            appText = Color(0xFFD92323)
        )
        source.contains("xiaoyuzhou") ||
            source.contains("小宇宙") -> TimelineLabelStyle(
            background = Color(0xFFE7F5FF),
            border = Color(0xFF5AB6F2),
            text = Color(0xFF123D5A),
            supportingText = Color(0xFF3E6E8E),
            appText = Color(0xFF1687D9)
        )
        else -> defaultStyle
    }
}

private fun calculateTimelineLabelOffsetBounds(
    segments: List<AwakeRecordSegment>,
    awakeIntervals: List<AwakeInterval>,
    canvasHeight: Float,
    contentHeight: Float,
    timelineInsetPx: Float,
    minZoom: Float,
    dpPx: Float
): Pair<Float, Float> {
    if (canvasHeight <= 0f || contentHeight <= 0f || segments.isEmpty()) return Pair(0f, 0f)

    val minDrawZoom = minZoom
    val timelineTop = timelineInsetPx + (contentHeight - contentHeight * minDrawZoom).coerceAtLeast(0f) / 2f
    val minLabelGap = 40f * dpPx
    val collapsedHeight = 32f * dpPx
    var nextRightY = 28f * dpPx
    var lastLabelCenter = 0f
    var firstLabelCenter = Float.MAX_VALUE
    val sorted = segments
        .sortedWith(compareBy<AwakeRecordSegment> { it.startMinute }.thenBy { it.endMinute }.thenBy { it.record.id })
    sorted.forEach { segment ->
        val y1 = timelineTop + (minuteToAwakeY(segment.startMinute, awakeIntervals, contentHeight) ?: return@forEach) * minDrawZoom
        val y2 = timelineTop + (minuteToAwakeY(segment.endMinute, awakeIntervals, contentHeight) ?: return@forEach) * minDrawZoom
        val midY = (y1 + y2) / 2f
        val baseLabelY = maxOf(midY, nextRightY)
        nextRightY = baseLabelY + maxOf(minLabelGap, collapsedHeight + 8f * dpPx)
        lastLabelCenter = baseLabelY
        if (firstLabelCenter > 1e6f) firstLabelCenter = baseLabelY
    }
    val targetLastLabelY = canvasHeight * 0.75f
    val minOffset = minOf(0f, targetLastLabelY - lastLabelCenter)
    val targetFirstLabelY = canvasHeight * 0.25f
    val maxOffset = maxOf(0f, targetFirstLabelY - firstLabelCenter)
    return Pair(minOffset, maxOffset)
}

private fun awakeIntervals(enabled: Boolean, sleepStartMinutes: Int, sleepEndMinutes: Int): List<AwakeInterval> {
    val start = sleepStartMinutes.coerceIn(0, 1439)
    val end = sleepEndMinutes.coerceIn(0, 1439)
    if (!enabled || start == end) return listOf(AwakeInterval(0, 1440))
    return if (start < end) {
        buildList {
            if (start > 0) add(AwakeInterval(0, start))
            if (end < 1440) add(AwakeInterval(end, 1440))
        }
    } else {
        listOf(AwakeInterval(end, start))
    }.ifEmpty { listOf(AwakeInterval(0, 1440)) }
}

private fun timelineTickMinutes(intervals: List<AwakeInterval>): List<Int> {
    val ticks = mutableSetOf<Int>()
    intervals.forEach { interval ->
        ticks.add(interval.startMinute)
        ticks.add(interval.endMinute)
        var minute = ((interval.startMinute + 359) / 360) * 360
        while (minute < interval.endMinute) {
            ticks.add(minute)
            minute += 360
        }
    }
    return ticks.sorted()
}

private fun timelineLightTickMinutes(intervals: List<AwakeInterval>): List<Int> {
    val ticks = mutableSetOf<Int>()
    intervals.forEach { interval ->
        var minute = ((interval.startMinute + 59) / 60) * 60
        while (minute < interval.endMinute) {
            ticks.add(minute)
            minute += 60
        }
    }
    return ticks.sorted()
}

private fun currentMinuteOfDay(): Int {
    val now = LocalDateTime.now().toLocalTime()
    return now.hour * 60 + now.minute
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAwakeTrackBands(
    intervals: List<AwakeInterval>,
    stripLeft: Float,
    stripWidth: Float,
    contentHeight: Float,
    transformY: (Float) -> Float,
    dayColor: Color,
    nightColor: Color
) {
    intervals.forEach { interval ->
        var start = interval.startMinute
        while (start < interval.endMinute) {
            val end = minOf(start + 60, interval.endMinute)
            val y1 = transformY(minuteToAwakeY(start, intervals, contentHeight) ?: return@forEach)
            val y2 = transformY(minuteToAwakeY(end, intervals, contentHeight) ?: return@forEach)
            val mid = (start + end) / 2
            drawRect(
                color = if (mid in 360 until 1080) dayColor else nightColor,
                topLeft = Offset(stripLeft, y1),
                size = Size(stripWidth, (y2 - y1).coerceAtLeast(1f))
            )
            start = end
        }
    }
}

private fun minuteToAwakeY(minute: Int, intervals: List<AwakeInterval>, height: Float): Float? {
    val total = intervals.sumOf { it.duration }.takeIf { it > 0 } ?: return null
    var offset = 0
    intervals.forEach { interval ->
        if (minute in interval.startMinute..interval.endMinute) {
            val local = (minute - interval.startMinute).coerceIn(0, interval.duration)
            return ((offset + local).toFloat() / total.toFloat()) * height
        }
        offset += interval.duration
    }
    return null
}

private fun nearestUsageContentY(
    day: LocalDate,
    records: List<UsageRecord>,
    sleepEnabled: Boolean,
    sleepStartMinutes: Int,
    sleepEndMinutes: Int,
    contentY: Float,
    contentHeight: Float
): Float? {
    if (contentHeight <= 0f || records.isEmpty()) return null
    val zone = ZoneId.systemDefault()
    val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val awakeIntervals = awakeIntervals(sleepEnabled, sleepStartMinutes, sleepEndMinutes)
    var nearestY: Float? = null
    var nearestDistance = Float.MAX_VALUE
    records
        .filter { it.startTime < dayEnd && it.endTime > dayStart }
        .flatMap { it.awakeSegments(day, zone, awakeIntervals) }
        .forEach { segment ->
            val y1 = minuteToAwakeY(segment.startMinute, awakeIntervals, contentHeight) ?: return@forEach
            val y2 = minuteToAwakeY(segment.endMinute, awakeIntervals, contentHeight) ?: return@forEach
            val candidate = contentY.coerceIn(minOf(y1, y2), maxOf(y1, y2))
            val distance = abs(candidate - contentY)
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestY = candidate
            }
    }
    return nearestY
}

private fun latestUsageContentY(
    day: LocalDate,
    records: List<UsageRecord>,
    sleepEnabled: Boolean,
    sleepStartMinutes: Int,
    sleepEndMinutes: Int,
    contentHeight: Float
): Float? {
    if (contentHeight <= 0f || records.isEmpty()) return null
    val zone = ZoneId.systemDefault()
    val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val awakeIntervals = awakeIntervals(sleepEnabled, sleepStartMinutes, sleepEndMinutes)
    val latest = records
        .filter { it.startTime < dayEnd && it.endTime > dayStart }
        .flatMap { it.awakeSegments(day, zone, awakeIntervals) }
        .maxByOrNull { it.endMinute }
        ?: return null
    val startY = minuteToAwakeY(latest.startMinute, awakeIntervals, contentHeight) ?: return null
    val endY = minuteToAwakeY(latest.endMinute, awakeIntervals, contentHeight) ?: return null
    return (startY + endY) / 2f
}

private data class TimelineOffsetBounds(val min: Float, val max: Float)

private fun calculateUsageTimelineOffsetBounds(
    contentHeight: Float,
    zoom: Float
): TimelineOffsetBounds? {
    if (contentHeight <= 0f || zoom <= 1f) return null
    return TimelineOffsetBounds(
        min = contentHeight - contentHeight * zoom,
        max = 0f
    )
}

private fun clampTimelineOffset(
    offset: Float,
    zoom: Float,
    viewportHeight: Float,
    usageBounds: TimelineOffsetBounds? = null
): Float {
    if (viewportHeight <= 0f || zoom <= 1f) return 0f
    if (usageBounds != null) return offset.coerceIn(usageBounds.min, usageBounds.max)
    val minOffset = viewportHeight - viewportHeight * zoom
    return offset.coerceIn(minOffset, 0f)
}

private fun rangeAlpha(y: Float, viewportHeight: Float): Float {
    val fade = 72f
    return when {
        y < -fade || y > viewportHeight + fade -> 0f
        y < fade -> ((y + fade) / (fade * 2f)).coerceIn(0f, 1f)
        y > viewportHeight - fade -> ((viewportHeight + fade - y) / (fade * 2f)).coerceIn(0f, 1f)
        else -> 1f
    }
}

private fun segmentVisibilityAlpha(top: Float, bottom: Float, viewportHeight: Float): Float {
    val fade = 72f
    return when {
        bottom < -fade || top > viewportHeight + fade -> 0f
        bottom < fade -> ((bottom + fade) / (fade * 2f)).coerceIn(0f, 1f)
        top > viewportHeight - fade -> ((viewportHeight + fade - top) / (fade * 2f)).coerceIn(0f, 1f)
        else -> 1f
    }
}

private fun UsageRecord.awakeSegments(
    day: LocalDate,
    zone: ZoneId,
    intervals: List<AwakeInterval>
): List<AwakeRecordSegment> {
    val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val clippedStart = maxOf(startTime, dayStart)
    val clippedEnd = minOf(endTime, dayEnd)
    if (clippedEnd <= clippedStart) return emptyList()

    val startMinute = minuteOfDay(clippedStart, day, zone)
    val endMinute = if (clippedEnd >= dayEnd) 1440 else minuteOfDay(clippedEnd, day, zone)
    return intervals.mapNotNull { interval ->
        val start = maxOf(startMinute, interval.startMinute)
        val end = minOf(endMinute, interval.endMinute)
        if (end > start) AwakeRecordSegment(this, start, end) else null
    }
}

private fun minuteOfDay(timeMillis: Long, day: LocalDate, zone: ZoneId): Int {
    val time = Instant.ofEpochMilli(timeMillis).atZone(zone).toLocalDateTime()
    if (time.toLocalDate().isBefore(day)) return 0
    if (time.toLocalDate().isAfter(day)) return 1440
    return (time.hour * 60 + time.minute).coerceIn(0, 1439)
}

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction.coerceIn(0f, 1f)
}

private fun segmentColor(segment: AwakeRecordSegment): Color {
    val palette = devicePalette()
    val baseColor = palette[Math.floorMod(segment.record.deviceAddress.hashCode(), palette.size)]
    val durationMinutes = (segment.endMinute - segment.startMinute).coerceAtLeast(1)
    return baseColor.copy(alpha = durationAlpha(durationMinutes))
}

private fun durationAlpha(durationMinutes: Int): Float {
    return when {
        durationMinutes < 10 -> 0.36f
        durationMinutes < 30 -> 0.5f
        durationMinutes < 60 -> 0.66f
        durationMinutes < 120 -> 0.82f
        else -> 0.96f
    }
}

private fun formatMinuteOfDay(minute: Int): String {
    val clamped = minute.coerceIn(0, 1440)
    val hour = clamped / 60
    val min = clamped % 60
    return "%02d:%02d".format(hour, min)
}

private fun devicePalette(): List<Color> = listOf(
    Color(0xFF006CFF),
    Color(0xFFFF7A00),
    Color(0xFFE91E63),
    Color(0xFF00A859),
    Color(0xFF7C4DFF),
    Color(0xFFFFC400)
)

private fun formatCalendarDuration(durationMillis: Long): String {
    val totalMinutes = (durationMillis / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        totalMinutes <= 0 -> "0m"
        hours > 0 && minutes > 0 -> "${hours}h${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}
