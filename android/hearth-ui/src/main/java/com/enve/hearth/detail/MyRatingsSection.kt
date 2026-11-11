package com.enve.hearth.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enve.engine.personal.PersonalBookState
import com.enve.hearth.design.Hearth
import com.enve.hearth.design.HearthText
import com.enve.hearth.design.Overline

@Composable
internal fun MyRatingsSection(
    state: PersonalBookState,
    onFavorite: (Boolean) -> Unit,
    onStars: (Int) -> Unit,
    onSpice: (Int) -> Unit,
    onSplash: () -> Unit,
) {
    val palette = Hearth.palette
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Hearth.Spacing.XL)
            .padding(top = Hearth.Spacing.L)
            .clip(RoundedCornerShape(Hearth.Radius.Card))
            .background(palette.bgElevated)
            .border(1.dp, palette.hairline, RoundedCornerShape(Hearth.Radius.Card))
            .padding(Hearth.Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Hearth.Spacing.S),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline("My ratings")
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                Icon(
                    imageVector = if (state.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (state.favorite) "Remove from favorites" else "Add to favorites",
                    tint = if (state.favorite) palette.ember else palette.textTertiary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onFavorite(!state.favorite) }
                        .padding(6.dp),
                )
            }
        }
        RatingRow("General", "⭐", state.stars, onStars)
        RatingRow("Spicy", "🌶️", state.spice, onSpice)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Splash", style = HearthText.Caption, color = palette.textSecondary, modifier = Modifier.weight(1f))
            Text(
                "💦",
                fontSize = 26.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onSplash)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text("${state.splash}", style = HearthText.Caption, color = palette.text, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun RatingRow(label: String, glyph: String, value: Int, onSelect: (Int) -> Unit) {
    val palette = Hearth.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = HearthText.Caption, color = palette.textSecondary, modifier = Modifier.weight(1f))
        (1..5).forEach { step ->
            Text(
                glyph,
                fontSize = 24.sp,
                modifier = Modifier
                    .alpha(if (step <= value) 1f else 0.25f)
                    .clip(CircleShape)
                    .clickable { onSelect(if (step == value) 0 else step) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}
