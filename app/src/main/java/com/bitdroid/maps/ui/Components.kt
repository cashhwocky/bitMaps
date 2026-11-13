package com.bitdroid.maps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.ForkLeft
import androidx.compose.material.icons.rounded.ForkRight
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.RampLeft
import androidx.compose.material.icons.rounded.RampRight
import androidx.compose.material.icons.rounded.RoundaboutRight
import androidx.compose.material.icons.rounded.Straight
import androidx.compose.material.icons.rounded.TurnLeft
import androidx.compose.material.icons.rounded.TurnRight
import androidx.compose.material.icons.rounded.TurnSharpLeft
import androidx.compose.material.icons.rounded.TurnSharpRight
import androidx.compose.material.icons.rounded.TurnSlightLeft
import androidx.compose.material.icons.rounded.TurnSlightRight
import androidx.compose.material.icons.rounded.UTurnLeft
import androidx.compose.material.icons.rounded.UTurnRight
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitdroid.maps.data.Place

/** Round glass button floating over the map. */
@Composable
fun GlassButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp,
    tint: Color = C.Text,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
) {
    Box(
        modifier
            .size(size)
            .shadow(10.dp, shape)
            .clip(shape)
            .background(C.Glass)
            .border(1.dp, C.Outline, shape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp)) }
}

/** The big violet → blue button. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 58.dp,
    enabled: Boolean = true,
    emoji: String? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .height(height)
            .shadow(if (enabled) 16.dp else 0.dp, shape, ambientColor = C.Violet, spotColor = C.Violet)
            .clip(shape)
            .background(if (enabled) C.gradient else Brush.linearGradient(listOf(C.Surface, C.Surface)))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (emoji != null) {
            Text(emoji, fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = if (enabled) Color.White else C.Dim, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/** Bottom sheet card. */
@Composable
fun Sheet(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(24.dp, shape)
            .clip(shape)
            .background(C.Sheet)
            .border(1.dp, C.Outline, shape)
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 40.dp, height = 5.dp)
                .clip(CircleShape)
                .background(Color(0xFF2E3440)),
        )
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
fun Pill(text: String, color: Color, background: Color) {
    Text(
        text,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(background).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Food emoji that matches what a place sells, from its OSM type, well-known chains and words in its name. */
fun foodEmoji(kind: String, name: String): String? {
    val n = name.lowercase()
    fun has(vararg w: String) = w.any { n.contains(it) }
    when (kind) {
        "ice_cream" -> return "🍦"
        "bakery", "pastry" -> return if (has("donut", "doughnut")) "🍩" else "🥐"
        "cafe", "coffee" -> return if (has("donut", "doughnut", "dunkin", "krispy")) "🍩" else if (has("tea", "boba")) "🧋" else "☕"
        "bar", "pub", "biergarten" -> return if (has("wine")) "🍷" else "🍺"
        "confectionery", "chocolate" -> return "🍫"
        "butcher" -> return "🥩"
        "seafood" -> return "🦞"
        "alcohol", "wine" -> return "🍷"
    }
    if (kind !in setOf("restaurant", "fast_food", "food_court", "cafe", "deli", "food")) return null
    return when {
        has("pizza", "domino", "papa john", "little caesar", "papa murphy", "marco's", "hungry howie") -> "🍕"
        has("taco", "chipotle", "qdoba", "moe's", "burrito", "mexican", "cantina", "tex-mex", "los ") -> "🌮"
        has("sushi", "ramen", "japanese", "hibachi", "teriyaki", "poke") -> "🍣"
        has("chinese", "panda express", "wok", "dragon", "china", "szechuan", "dumpling") -> "🥡"
        has("thai", "pho", "vietnam", "noodle") -> "🍜"
        has("indian", "curry", "tandoori", "masala") -> "🍛"
        has("chick-fil-a", "chick fil a", "kfc", "popeyes", "bojangles", "raising cane", "zaxby", "wingstop", "wings", "chicken", "church's") -> "🍗"
        has("subway", "jersey mike", "jimmy john", "firehouse sub", "potbelly", "sandwich", "sub ", "subs", "deli") -> "🥪"
        has("dunkin", "krispy", "donut", "doughnut") -> "🍩"
        has("starbucks", "coffee", "espresso", "cafe", "café") -> "☕"
        has("dairy queen", "ice cream", "frozen yogurt", "froyo", "baskin", "cold stone", "gelato", "rita's") -> "🍦"
        has("boba", "bubble tea", "tea house") -> "🧋"
        has("smoothie", "juice", "tropical smoothie") -> "🥤"
        has("bbq", "barbecue", "smokehouse", "brisket") -> "🍖"
        has("steak", "outback", "longhorn", "texas roadhouse") -> "🥩"
        has("seafood", "crab", "lobster", "oyster", "fish", "red lobster", "captain d") -> "🦞"
        has("waffle", "ihop", "pancake", "denny", "breakfast", "cracker barrel", "brunch") -> "🥞"
        has("bagel") -> "🥯"
        has("bakery", "bread", "panera") -> "🥖"
        has("salad", "sweetgreen", "vegan", "vegetarian") -> "🥗"
        has("hot dog", "hotdog", "frank") -> "🌭"
        has("greek", "gyro", "mediterranean", "kebab", "shawarma", "falafel") -> "🥙"
        has("italian", "pasta", "olive garden", "spaghetti", "trattoria") -> "🍝"
        has("mcdonald", "burger", "wendy", "five guys", "sonic", "hardee", "carl's", "jack in the box", "whataburger", "culver", "shake shack", "smashburger", "cook out", "checkers", "rally's", "white castle", "in-n-out", "steak n shake") -> "🍔"
        kind == "fast_food" -> "🍔"
        else -> "🍽️"
    }
}

/** Emoji + soft tint for a kind of place (food places get an emoji for what they sell). */
fun placeEmoji(kind: String, name: String = ""): Pair<String, Color> {
    foodEmoji(kind, name)?.let { e ->
        val tint = when (e) {
            "☕", "🥐", "🥖", "🥯", "🥞", "🍩" -> Color(0xFFFDBA74)
            "🍦", "🧋", "🥤", "🍫" -> Color(0xFFF9A8D4)
            "🥗", "🥙" -> C.Green
            "🍺", "🍷" -> Color(0xFFFDE68A)
            else -> Color(0xFFFCA5A5)
        }
        return e to tint
    }
    return basicEmoji(kind)
}

private fun basicEmoji(kind: String): Pair<String, Color> = when (kind) {
    "cafe", "coffee" -> "☕" to Color(0xFFFDBA74)
    "restaurant", "food_court" -> "🍽️" to Color(0xFFFCA5A5)
    "fast_food" -> "🍔" to Color(0xFFFCA5A5)
    "bar", "pub" -> "🍻" to Color(0xFFFDE68A)
    "fuel" -> "⛽" to C.Cyan
    "charging_station" -> "🔌" to C.Cyan
    "parking" -> "🅿️" to Color(0xFF93C5FD)
    "supermarket", "convenience", "grocery" -> "🛒" to Color(0xFFF9A8D4)
    "mall", "department_store", "clothes" -> "🛍️" to Color(0xFFF9A8D4)
    "park", "garden", "nature_reserve", "playground" -> "🌳" to C.Green
    "station", "halt", "tram_stop", "subway_entrance" -> "🚆" to C.Lilac
    "bus_station", "bus_stop" -> "🚌" to C.Lilac
    "aerodrome", "airport" -> "✈️" to C.Lilac
    "hospital", "clinic" -> "🏥" to Color(0xFFFCA5A5)
    "pharmacy" -> "💊" to Color(0xFFFCA5A5)
    "school", "university", "college" -> "🏫" to Color(0xFFFDE68A)
    "hotel", "motel", "hostel" -> "🏨" to C.Lilac
    "house", "residential", "home" -> "🏠" to Color(0xFFFDBA74)
    "city", "town", "village", "suburb", "neighbourhood", "state", "country", "county" -> "🏙️" to C.Lilac
    "gym", "fitness_centre" -> "🏋️" to C.Green
    "cinema", "theatre" -> "🎬" to C.Lilac
    "bank", "atm" -> "🏦" to Color(0xFF93C5FD)
    "pin" -> "📍" to C.Pink
    else -> "📍" to C.Lilac
}

@Composable
fun PlaceIcon(kind: String, size: Dp = 46.dp, name: String = "") {
    val (emoji, tint) = placeEmoji(kind, name)
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(15.dp))
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.24f), tint.copy(alpha = 0.10f)))),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = (size.value * 0.46f).sp) }
}

@Composable
fun PlaceRow(place: Place, trailing: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PlaceIcon(place.kind, name = place.name)
        Column(Modifier.weight(1f)) {
            Text(place.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOf(place.category, place.detail).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, fontSize = 14.sp, color = C.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) Text(trailing, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Dim)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp)
}

@Composable
fun RowScope.Grow() = Spacer(Modifier.weight(1f))

/** Turn arrow for a Valhalla maneuver type. */
fun maneuverIcon(type: Int): ImageVector = when (type) {
    2, 9 -> Icons.Rounded.TurnSlightRight
    3, 16 -> Icons.Rounded.TurnSlightLeft
    4, 5, 6 -> Icons.Rounded.Flag
    10 -> Icons.Rounded.TurnRight
    11 -> Icons.Rounded.TurnSharpRight
    12 -> Icons.Rounded.UTurnRight
    13 -> Icons.Rounded.UTurnLeft
    14 -> Icons.Rounded.TurnSharpLeft
    15 -> Icons.Rounded.TurnLeft
    18, 20 -> Icons.Rounded.RampRight
    19, 21 -> Icons.Rounded.RampLeft
    23 -> Icons.Rounded.ForkRight
    24 -> Icons.Rounded.ForkLeft
    25, 37, 38 -> Icons.Rounded.Merge
    26, 27 -> Icons.Rounded.RoundaboutRight
    28, 29 -> Icons.Rounded.DirectionsBoat
    1 -> Icons.Rounded.Navigation
    else -> Icons.Rounded.Straight
}
