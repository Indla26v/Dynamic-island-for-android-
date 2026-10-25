package dev.indla.island.ui.island

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.PersonPinCircle
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.indla.island.model.Tracking

/** The vehicle bringing it: a delivery scooter, a cab, a bus, a truck. */
internal fun vehicleIcon(tracking: Tracking): ImageVector = when (tracking) {
    Tracking.FOOD, Tracking.GROCERY -> Icons.Rounded.DeliveryDining
    Tracking.RIDE -> Icons.Rounded.LocalTaxi
    Tracking.BUS -> Icons.Rounded.DirectionsBus
    Tracking.PARCEL -> Icons.Rounded.LocalShipping
}

/** Where it started: the restaurant, the store, the pickup point, the warehouse. */
private fun originIcon(tracking: Tracking): ImageVector = when (tracking) {
    Tracking.FOOD -> Icons.Rounded.Restaurant
    Tracking.GROCERY -> Icons.Rounded.Storefront
    Tracking.RIDE, Tracking.BUS -> Icons.Rounded.PersonPinCircle
    Tracking.PARCEL -> Icons.Rounded.Inventory2
}

/**
 * A little route: origin on the left, home (or the drop point) on the right, the trip so far
 * solid in the app's colour and the rest dashed, with the vehicle on it. The vehicle glides to
 * each new position as the app updates; with no progress given, it waits halfway.
 */
@Composable
internal fun TrackingRoute(tracking: Tracking, fraction: Float?, accent: Color, modifier: Modifier, icon: Dp = 16.dp) {
    val at by animateFloatAsState(fraction ?: 0.5f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow), label = "vehicle")
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val inset = icon + 4.dp
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height / 2
            val start = inset.toPx()
            val end = size.width - inset.toPx()
            val here = start + (end - start) * at
            drawLine(accent, Offset(start, y), Offset(here, y), 3.dp.toPx(), StrokeCap.Round)
            drawLine(
                Color.White.copy(alpha = 0.35f),
                Offset(here, y),
                Offset(end, y),
                2.dp.toPx(),
                StrokeCap.Round,
                PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
        }
        Icon(originIcon(tracking), null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.align(Alignment.CenterStart).size(icon))
        Icon(Icons.Rounded.Home, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.align(Alignment.CenterEnd).size(icon))
        val track = w - inset * 2
        Box(Modifier.align(Alignment.CenterStart).offset(x = inset + track * at - (icon + 6.dp) / 2)) {
            Icon(vehicleIcon(tracking), null, tint = accent, modifier = Modifier.size(icon + 6.dp))
        }
    }
}
