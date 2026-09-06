package com.vecu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.SensorDoor
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vecu.config.EcuProfile
import com.vecu.viewmodel.SimStatus
import kotlinx.coroutines.delay

private val SidebarBg = Color(0xFF0F141A)
private val CardBg = Color(0xFF141B23)
private val ItemText = Color(0xFFA8B5C0)
private val Muted = Color(0xFF5C6773)

val NAV_EXPANDED_WIDTH = 200.dp
val NAV_COLLAPSED_WIDTH = 48.dp

/**
 * Glyph tokens an [EcuProfile] can name. Kept as tokens rather than icon class
 * names so config/ never has to know what an ImageVector is, and so an unknown
 * token degrades to [DefaultEcuIcon] instead of failing to compile.
 */
private val EcuIcons: Map<String, ImageVector> = mapOf(
    "car" to Icons.Filled.DirectionsCar,
    "gearbox" to Icons.Filled.PrecisionManufacturing,
    "battery" to Icons.Filled.BatteryFull,
    "chip" to Icons.Filled.Memory,
    "charger" to Icons.Filled.EvStation,
    "wheel" to Icons.Filled.Adjust,
    "door" to Icons.Filled.SensorDoor,
    "climate" to Icons.Filled.AcUnit,
    "button" to Icons.Filled.TouchApp,
)

private val DefaultEcuIcon = Icons.Filled.DeveloperBoard

/** The non-ECU destinations. Selecting an ECU returns to the ECU view. */
enum class NavScreen(val label: String, val icon: ImageVector) {
    DIAGNOSTICS("Diagnostics", Icons.Filled.Analytics),
    LOGS("Logs", Icons.Filled.Article),
    SETTINGS("Settings", Icons.Filled.Settings),
}

/**
 * Left navigation: which ECU you are viewing, the other screens, and the live
 * ECU status card.
 *
 * Selecting an ECU does not choose what *runs* — every profile runs at once on
 * the shared bus (the run-all/view-one model). It chooses what you look at,
 * which is why the whole list is on screen instead of behind a dropdown.
 *
 * Collapses to an icon rail because it shares the left edge with the 250 dp
 * Properties panel, and the window opens at 1200 dp.
 */
@Composable
fun NavSidebar(
    profiles: List<EcuProfile>,
    activeProfile: String,
    onSelectProfile: (String) -> Unit,
    status: SimStatus,
    /** Which screen is showing; null = the ECU view. */
    activeScreen: NavScreen?,
    onSelectScreen: (NavScreen) -> Unit,
    canInterface: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    propertiesShown: Boolean,
    onToggleProperties: () -> Unit,
) {
    Column(
        Modifier
            .width(if (expanded) NAV_EXPANDED_WIDTH else NAV_COLLAPSED_WIDTH)
            .fillMaxHeight()
            .background(SidebarBg)
            .padding(vertical = 5.dp),
    ) {
        CollapseToggle(expanded, onToggleExpanded)

        // The ECU list scrolls: nine profiles today, and adding one is a config
        // edit, so this must not depend on them all fitting.
        Column(Modifier.weight(1f, fill = true).verticalScroll(rememberScrollState())) {
            profiles.forEach { p ->
                NavItem(
                    icon = p.icon?.let { EcuIcons[it] } ?: DefaultEcuIcon,
                    label = p.name,
                    selected = activeScreen == null && p.name == activeProfile,
                    expanded = expanded,
                    onClick = { onSelectProfile(p.name) },
                )
            }
        }

        Spacer(Modifier.height(2.dp))
        Divider()
        Spacer(Modifier.height(2.dp))
        NavScreen.entries.forEach { screen ->
            NavItem(
                icon = screen.icon,
                label = screen.label,
                selected = screen == activeScreen,
                expanded = expanded,
                onClick = { onSelectScreen(screen) },
            )
        }
        Spacer(Modifier.height(2.dp))
        Divider()
        Spacer(Modifier.height(6.dp))
        // The Properties panel's own edge rail folded in here, so the left edge
        // carries one control instead of a sidebar plus a 30 dp rail beside it.
        NavItem(
            icon = Icons.Filled.ViewList,
            label = "Properties",
            selected = propertiesShown,
            expanded = expanded,
            onClick = onToggleProperties,
        )
        Spacer(Modifier.height(6.dp))
        if (expanded) StatusCard(status, canInterface) else StatusPip(status)
    }
}

@Composable
private fun CollapseToggle(expanded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = if (expanded) Arrangement.End else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (expanded) Icons.Filled.ChevronLeft else Icons.Filled.ChevronRight,
            contentDescription = if (expanded) "Collapse sidebar" else "Expand sidebar",
            tint = Muted,
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onToggle),
        )
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> Muted
        selected -> VecuColors.rx
        else -> ItemText
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) VecuColors.rx.copy(alpha = 0.14f) else Color.Transparent)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        if (expanded) {
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                color = tint,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Live ECU state: the toolbar's two status dots now live here, once. */
@Composable
private fun StatusCard(status: SimStatus, canInterface: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(status.connected)
            Spacer(Modifier.width(7.dp))
            Text(
                if (status.ecuRunning) "Running" else if (status.connected) "Connected" else "Stopped",
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (status.ecuRunning) VecuColors.ok else if (status.connected) VecuColors.rx else VecuColors.idle,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(6.dp))
        StatusRow("CAN Interface", canInterface.ifBlank { "—" })
        // "active / loaded": every profile is loaded, but only the ones actually
        // transmitting are on the bus, and the difference is worth seeing.
        StatusRow("Active ECUs", "${status.activeEcus} / ${status.ecuCount}")
        StatusRow("Uptime", uptimeText(status.ecuStartedAt))
    }
}

/** Collapsed rail: no room for the card, but the run state still has to show. */
@Composable
private fun StatusPip(status: SimStatus) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Dot(status.ecuRunning || status.connected)
    }
}

@Composable
private fun Dot(on: Boolean) {
    Box(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (on) VecuColors.ok else VecuColors.idle),
    )
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 10.sp, lineHeight = 11.sp, color = Muted, maxLines = 1, softWrap = false)
        Spacer(Modifier.width(6.dp))
        Text(
            value,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFC4CCD3),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Ticks once a second, and only while an ECU is actually running. */
@Composable
private fun uptimeText(startedAt: Long?): String {
    if (startedAt == null) return "--:--:--"
    var now by remember(startedAt) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val secs = ((now - startedAt).coerceAtLeast(0L)) / 1000
    return "%02d:%02d:%02d".format(secs / 3600, (secs / 60) % 60, secs % 60)
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(Color(0xFF1E262F)))
}
