package com.dd.daykit.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AvTimer
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dd.daykit.BuildConfig
import com.dd.daykit.GlobalInAppMessageManager
import com.dd.daykit.GlobalMessageType
import com.dd.daykit.InternalInAppBannerActionKind
import com.dd.daykit.rememberInAppNotificationsEnabled
import com.dd.daykit.R

/**
 * Shared dimensions/typography for all in-app status strips (timer, agenda, sluimer, sound, …).
 */
object InternalInAppBannerDim {
    val rowHeight = 36.dp
    val actionSlotSize = 36.dp
    /** Ruimte tussen opeenvolgende actie-iconen (vaste striphoogte blijft gelijk). */
    val actionIconGap = 12.dp
    const val GLOBAL_MESSAGE_ACTION_SLOTS = 3

    fun actionTrackWidth(slots: Int): Dp {
        if (slots <= 0) return 0.dp
        return actionSlotSize * slots + actionIconGap * (slots - 1).coerceAtLeast(0)
    }

    val leadingIconSlotWidth = 22.dp
    val leadingIconSize = 18.dp
    val endCloseSlotSize = 36.dp
    val horizontalContentPadding = 6.dp
    val iconTitleGap = 4.dp
    val titleTrailingGap = 6.dp
    val titleFontSize = 13.sp
    val trailingFontSize = 14.sp
    val actionIconSize = 20.dp
    val endCloseIconSize = 18.dp
}

object InternalInAppBannerLog {
    private const val TAG = "InternalInAppBanner"

    fun render(pipeline: String, slot: String, detail: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "compose pipeline=$pipeline slot=$slot | $detail")
        }
    }
}

fun globalMessageLeadingIcon(type: GlobalMessageType): ImageVector = when (type) {
    GlobalMessageType.TIMER -> Icons.Outlined.Timer
    GlobalMessageType.STOPWATCH -> Icons.Outlined.AvTimer
    GlobalMessageType.AGENDA_ALARM -> Icons.Outlined.Alarm
}

private fun internalBannerActionIcon(kind: InternalInAppBannerActionKind): ImageVector = when (kind) {
    InternalInAppBannerActionKind.ACTION_ROW_EMPTY -> Icons.Outlined.Star
    InternalInAppBannerActionKind.AGENDA_SNOOZE -> Icons.Outlined.Snooze
    InternalInAppBannerActionKind.AGENDA_STOP -> Icons.Filled.Stop
    InternalInAppBannerActionKind.TIMER_PLAY,
    InternalInAppBannerActionKind.STOPWATCH_RESUME -> Icons.Filled.PlayArrow
    InternalInAppBannerActionKind.TIMER_PAUSE,
    InternalInAppBannerActionKind.STOPWATCH_PAUSE -> Icons.Filled.Pause
    InternalInAppBannerActionKind.TIMER_STOP,
    InternalInAppBannerActionKind.STOPWATCH_STOP -> Icons.Filled.Stop
    InternalInAppBannerActionKind.TIMER_SAVE,
    InternalInAppBannerActionKind.STOPWATCH_SAVE -> Icons.Outlined.Save
    InternalInAppBannerActionKind.STOPWATCH_LAP -> Icons.Outlined.Flag
}

@Composable
private fun internalBannerActionContentDescription(kind: InternalInAppBannerActionKind): String = when (kind) {
    InternalInAppBannerActionKind.ACTION_ROW_EMPTY -> ""
    InternalInAppBannerActionKind.AGENDA_SNOOZE -> stringResource(R.string.internal_banner_cd_agenda_snooze)
    InternalInAppBannerActionKind.AGENDA_STOP -> stringResource(R.string.internal_banner_cd_agenda_stop)
    InternalInAppBannerActionKind.TIMER_PLAY -> stringResource(R.string.internal_banner_cd_timer_play)
    InternalInAppBannerActionKind.TIMER_PAUSE -> stringResource(R.string.internal_banner_cd_timer_pause)
    InternalInAppBannerActionKind.TIMER_STOP -> stringResource(R.string.internal_banner_cd_timer_stop)
    InternalInAppBannerActionKind.TIMER_SAVE -> stringResource(R.string.internal_banner_cd_timer_save)
    InternalInAppBannerActionKind.STOPWATCH_LAP -> stringResource(R.string.internal_banner_cd_stopwatch_lap)
    InternalInAppBannerActionKind.STOPWATCH_PAUSE -> stringResource(R.string.internal_banner_cd_stopwatch_pause)
    InternalInAppBannerActionKind.STOPWATCH_RESUME -> stringResource(R.string.internal_banner_cd_stopwatch_resume)
    InternalInAppBannerActionKind.STOPWATCH_SAVE -> stringResource(R.string.internal_banner_cd_stopwatch_save)
    InternalInAppBannerActionKind.STOPWATCH_STOP -> stringResource(R.string.internal_banner_cd_stopwatch_stop)
}

@Composable
private fun ActionSlotCell(
    kind: InternalInAppBannerActionKind,
    contentColor: Color,
    onAction: (InternalInAppBannerActionKind) -> Unit,
) {
    Box(
        modifier = Modifier.size(InternalInAppBannerDim.actionSlotSize),
        contentAlignment = Alignment.Center,
    ) {
        if (kind == InternalInAppBannerActionKind.ACTION_ROW_EMPTY) {
            Spacer(Modifier.size(InternalInAppBannerDim.actionSlotSize))
        } else {
            InternalInAppBannerIconButton(
                icon = internalBannerActionIcon(kind),
                contentDescription = internalBannerActionContentDescription(kind),
                tint = contentColor,
                onClick = { onAction(kind) },
            )
        }
    }
}

/** Zelfde icoon-hit als de interne meldingsbalk; herbruikbaar op o.a. de stopwatch-pagina. */
@Composable
fun InternalInAppBannerIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(InternalInAppBannerDim.actionSlotSize)
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = false, radius = InternalInAppBannerDim.actionSlotSize / 2),
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(InternalInAppBannerDim.actionIconSize),
        )
    }
}

/**
 * One fixed-height in-app banner row. Used by overlay status bar, sluimer strip, embedded banners, sound strip.
 *
 * @param includeRowBackground when false, the caller paints the strip background (e.g. swipe-dismiss [Box]).
 * @param reservedActionSlots fixed icon slots on the trailing side (prevents layout shift when visible actions change).
 * @param onInternalBannerAction optional override; default runs [GlobalInAppMessageManager.performInternalBannerAction].
 */
@Composable
fun UnifiedInternalMessageBannerRow(
    pipeline: String,
    slot: String,
    contentColor: Color,
    leadingIcon: ImageVector?,
    title: String,
    trailingText: String?,
    actions: List<InternalInAppBannerActionKind>,
    modifier: Modifier = Modifier,
    includeRowBackground: Boolean = true,
    rowBackgroundColor: Color = Color.Transparent,
    reservedActionSlots: Int = 0,
    endClose: (() -> Unit)? = null,
    endCloseContentDescription: String? = null,
    onTitleAreaClick: (() -> Unit)? = null,
    onInternalBannerAction: ((Context, InternalInAppBannerActionKind) -> Unit)? = null,
) {
    if (!rememberInAppNotificationsEnabled()) return

    val context = LocalContext.current
    val handler = onInternalBannerAction ?: { ctx: Context, k: InternalInAppBannerActionKind ->
        GlobalInAppMessageManager.performInternalBannerAction(ctx, k)
    }

    SideEffect {
        InternalInAppBannerLog.render(
            pipeline,
            slot,
            "UnifiedInternalMessageBannerRow h=${InternalInAppBannerDim.rowHeight} " +
                "leadIcon=${leadingIcon != null} titleLen=${title.length} trailLen=${trailingText?.length ?: 0} " +
                "actions=${actions.size} reservedSlots=$reservedActionSlots close=${endClose != null} rowBg=$includeRowBackground titleClick=${onTitleAreaClick != null}",
        )
    }

    val base = Modifier
        .fillMaxWidth()
        .height(InternalInAppBannerDim.rowHeight)

    val withBg = if (includeRowBackground) {
        base.background(rowBackgroundColor)
    } else {
        base
    }

    Row(
        modifier = modifier.then(withBg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Box(
                modifier = Modifier
                    .width(InternalInAppBannerDim.leadingIconSlotWidth)
                    .height(InternalInAppBannerDim.rowHeight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.85f),
                    modifier = Modifier.size(InternalInAppBannerDim.leadingIconSize),
                )
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .height(InternalInAppBannerDim.rowHeight)
                .padding(horizontal = InternalInAppBannerDim.horizontalContentPadding)
                .then(
                    if (onTitleAreaClick != null) {
                        Modifier.clickable(onClick = onTitleAreaClick)
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = contentColor,
                fontSize = InternalInAppBannerDim.titleFontSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailingText != null) {
                Spacer(Modifier.width(InternalInAppBannerDim.titleTrailingGap))
                Text(
                    text = trailingText,
                    color = contentColor,
                    fontSize = InternalInAppBannerDim.trailingFontSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (reservedActionSlots > 0) {
            val useFixedTripleRow =
                actions.size == InternalInAppBannerDim.GLOBAL_MESSAGE_ACTION_SLOTS &&
                    reservedActionSlots == InternalInAppBannerDim.GLOBAL_MESSAGE_ACTION_SLOTS
            Box(
                modifier = Modifier
                    .width(InternalInAppBannerDim.actionTrackWidth(reservedActionSlots))
                    .height(InternalInAppBannerDim.rowHeight),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (useFixedTripleRow) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(InternalInAppBannerDim.rowHeight),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        actions.forEach { kind ->
                            ActionSlotCell(
                                kind = kind,
                                contentColor = contentColor,
                                onAction = { k -> handler(context, k) },
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            InternalInAppBannerDim.actionIconGap,
                            Alignment.End,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        actions.forEach { kind ->
                            ActionSlotCell(
                                kind = kind,
                                contentColor = contentColor,
                                onAction = { k -> handler(context, k) },
                            )
                        }
                    }
                }
            }
        }

        if (endClose != null) {
            val ix = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(InternalInAppBannerDim.endCloseSlotSize)
                    .clickable(
                        interactionSource = ix,
                        indication = rememberRipple(bounded = false, radius = InternalInAppBannerDim.endCloseSlotSize / 2),
                        role = Role.Button,
                        onClick = endClose,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = endCloseContentDescription,
                    tint = contentColor,
                    modifier = Modifier.size(InternalInAppBannerDim.endCloseIconSize),
                )
            }
        }
    }
}
