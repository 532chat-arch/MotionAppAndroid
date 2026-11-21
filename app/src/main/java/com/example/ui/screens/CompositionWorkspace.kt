package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.CompositionStage
import com.example.ui.components.CompositionTimeline
import com.example.ui.components.InspectorPanel
import com.example.ui.components.LayersPanel
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.StudioPrimary
import com.example.ui.theme.StudioTextPrimary
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel

@Composable
fun CompositionWorkspace(
    state: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier,
) {
    var timelineCollapsed by remember { mutableStateOf(false) }
    var desktopLeftOpen by remember { mutableStateOf(true) }
    var desktopRightOpen by remember { mutableStateOf(true) }
    var mobileDrawer by remember { mutableStateOf("none") } // "none", "layers", "inspector"

    BackHandler(enabled = mobileDrawer != "none") {
        mobileDrawer = "none"
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(StudioBg)) {
        val isWide = maxWidth >= 840.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Main Stage Area (3-pane on wide tablets, full-bleed + drawer buttons on phones)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isWide) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (desktopLeftOpen) {
                            LayersPanel(
                                state = state,
                                viewModel = viewModel,
                                modifier = Modifier.width(250.dp).fillMaxHeight(),
                            )
                            VerticalDivider(color = StudioBorder)
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            CompositionStage(state = state, viewModel = viewModel)
                            // Subtle top panel toggle pills on wide layouts
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                OverlayToggleChip("Layers", Icons.Default.Layers, desktopLeftOpen) {
                                    desktopLeftOpen = !desktopLeftOpen
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                OverlayToggleChip("Inspector", Icons.Default.Tune, desktopRightOpen) {
                                    desktopRightOpen = !desktopRightOpen
                                }
                            }
                        }
                        if (desktopRightOpen) {
                            VerticalDivider(color = StudioBorder)
                            InspectorPanel(
                                state = state,
                                viewModel = viewModel,
                                modifier = Modifier.width(305.dp).fillMaxHeight(),
                            )
                        }
                    }
                } else {
                    CompositionStage(state = state, viewModel = viewModel)

                    // Mobile Top Floating Drawers Bar
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        OverlayToggleChip(
                            label = "${stringResource(R.string.panel_layers)} (${state.activeComposition.layers.size})",
                            icon = Icons.Default.Layers,
                            active = mobileDrawer == "layers",
                            tag = "mobile_layers_drawer_btn",
                        ) {
                            mobileDrawer = if (mobileDrawer == "layers") "none" else "layers"
                        }
                        OverlayToggleChip(
                            label = stringResource(R.string.panel_inspector),
                            icon = Icons.Default.Tune,
                            active = mobileDrawer == "inspector",
                            tag = "mobile_inspector_drawer_btn",
                        ) {
                            mobileDrawer = if (mobileDrawer == "inspector") "none" else "inspector"
                        }
                    }

                    // Slide-over Sheet Overlay for Mobile Layers / Inspector
                    if (mobileDrawer != "none") {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { mobileDrawer = "none" },
                        ) {
                            Surface(
                                modifier = Modifier
                                    .align(if (mobileDrawer == "layers") Alignment.CenterStart else Alignment.CenterEnd)
                                    .fillMaxWidth(0.86f)
                                    .fillMaxHeight()
                                    .clickable(enabled = false) {},
                                color = StudioPanel,
                                tonalElevation = 8.dp,
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(StudioBg)
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = if (mobileDrawer == "layers") "Layers & Compositions" else "Inspector",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = StudioTextPrimary,
                                        )
                                        IconButton(onClick = { mobileDrawer = "none" }) {
                                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close), tint = StudioTextPrimary)
                                        }
                                    }
                                    if (mobileDrawer == "layers") {
                                        LayersPanel(state = state, viewModel = viewModel, modifier = Modifier.weight(1f))
                                    } else {
                                        InspectorPanel(state = state, viewModel = viewModel, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Composition Timeline Dock
            CompositionTimeline(
                state = state,
                viewModel = viewModel,
                collapsed = timelineCollapsed,
                onToggleCollapse = { timelineCollapsed = !timelineCollapsed },
            )
        }
    }
}

@Composable
private fun OverlayToggleChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    tag: String = "",
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) StudioPrimary else StudioPanel.copy(alpha = 0.92f))
            .border(1.dp, if (active) Color.White.copy(alpha = 0.35f) else StudioBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Medium)
    }
}
