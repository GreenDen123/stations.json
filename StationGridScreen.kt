package com.allworld.radio

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Card
import androidx.compose.ui.graphics.luminance
import coil.compose.AsyncImage
import com.allworld.radio.model.Station
import androidx.compose.ui.graphics.Color

// Вставьте это СРАЗУ ПОСЛЕ импортов
object StationTranslations {
    val genres = mapOf(
        "genre_pop" to R.string.genre_pop,
        "genre_hits" to R.string.genre_hits,
        "genre_90s" to R.string.genre_90s,
        "genre_dance" to R.string.genre_dance,
        "genre_manele" to R.string.genre_manele,
        "genre_rock" to R.string.genre_rock,
        "genre_folk" to R.string.genre_folk,
        "genre_jazz" to R.string.genre_jazz,
        "genre_news" to R.string.genre_news,
        "genre_relax" to R.string.genre_relax,
        "genre_electronic" to R.string.genre_electronic,
        "genre_chanson" to R.string.genre_chanson,
        "genre_retro" to R.string.genre_retro,
        "genre_info" to R.string.genre_info,
        "genre_hiphop" to R.string.genre_hiphop,
        "genre_kids" to R.string.genre_kids,
        "genre_deephouse" to R.string.genre_deephouse,
        "genre_sport" to R.string.genre_sport,
        "genre_religious" to R.string.genre_religious,
        "genre_clasic" to R.string.genre_clasic,
        "genre_blues" to R.string.genre_blues
    )

    val countries = mapOf(
        "country_russia" to R.string.country_russia,
        "country_romania" to R.string.country_romania,
        "country_moldova" to R.string.country_moldova,
        "country_bulgaria" to R.string.country_bulgaria,
        "country_ucraina" to R.string.country_ucraina,
        "country_usa" to R.string.country_usa,
        "country_france" to R.string.country_france,
        "country_belarus" to R.string.country_belarus,
        "country_spain" to R.string.country_spain,
        "country_brazil" to R.string.country_brazil

    )
}

@Composable
fun StationGridScreen(
    stations: List<Station>,
    settings: SettingsViewModel,
    radioVM: RadioViewModel,
    currentUrl: String?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    title: String?,
    artist: String?,
    onClick: (Station) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val columns = if (configuration.screenWidthDp > 600) 2 else 1

    if (stations.isEmpty() && settings.searchQuery.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        // 1. Логика фильтрации (оставляем одну, правильную)
        val filteredStations = stations.filter { station ->
            val matchesSearch = station.name.contains(settings.searchQuery, ignoreCase = true)
            val matchesGenre = settings.selectedGenre == "Все" ||
                    settings.selectedGenre == stringResource(R.string.all_categories) ||
                    station.genre == settings.selectedGenre
            val matchesCountry = settings.selectedCountry == "Все" ||
                    settings.selectedCountry == stringResource(R.string.all_categories) ||
                    station.country == settings.selectedCountry
            matchesSearch && matchesGenre && matchesCountry
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // 2. ВЕРХНЯЯ ПАНЕЛЬ ПОИСКА
            OutlinedTextField(
                value = settings.searchQuery,
                onValueChange = { settings.searchQuery = it }, // Мгновенно обновляет список
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.nav_radio)) }, // Используем ресурс поиска
                leadingIcon = { Text("🔍", modifier = Modifier.padding(start = 8.dp)) },
                trailingIcon = {
                    if (settings.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { settings.searchQuery = "" }) {
                            Text("❌") // Кнопка быстрой очистки
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // 3. СЕТКА СТАНЦИЙ
            if (filteredStations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_stations_found), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyVerticalGrid(

                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filteredStations,
                        key = { it.id }
                    ) { station ->
                        StationListRow(
                            station = station,
                            isCurrent = station.streamUrl == currentUrl,
                            isPlaying = isPlaying && station.streamUrl == currentUrl,
                            isBuffering = isBuffering && station.streamUrl == currentUrl,
                            iconSize = settings.iconSize,
                            isFavorite = radioVM.favoriteUrls.contains(station.streamUrl),
                            onFavClick = { radioVM.toggleFavorite(station.streamUrl) },
                            onClick = { onClick(station) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StationListRow(
    station: Station,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    iconSize: Float,
    isFavorite: Boolean,
    onFavClick: () -> Unit,
    onClick: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val activeColor = if (isDarkTheme) {
        Color(0xFF3A3A3A)
    } else {
        Color(0xFFF0F0F0)
    }

    val cardColor = if (isCurrent) activeColor else Color.Transparent

    val mainTextColor = if (isCurrent) {
        if (isDarkTheme) Color.White else Color.Black
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 8.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Иконка станции с индикаторами
            Box(
                modifier = Modifier
                    .size(iconSize.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = station.imageUrl,
                    contentDescription = station.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(iconSize.dp / 2),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else if (isPlaying) {
                            PlayingAnimation(iconSize / 3)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Текстовый блок
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name,
                    fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Normal,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = mainTextColor
                )

                val genreResId = StationTranslations.genres[station.genre]
                val countryResId = StationTranslations.countries[station.country]
                val genreDisplay = genreResId?.let { stringResource(it) } ?: station.genre
                val countryDisplay = countryResId?.let { stringResource(it) } ?: station.country

                val subtitle = buildString {
                    append(genreDisplay)
                    if (genreDisplay.isNotEmpty() && countryDisplay.isNotEmpty()) append(" • ")
                    append(countryDisplay)
                }

                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = mainTextColor.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onFavClick) {
                Text(if (isFavorite) "❤️" else "🤍", fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun PlayingAnimation(size: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "eq")
    Row(
        modifier = Modifier.size(size.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val durations = listOf(400, 700, 500, 800, 200)
        durations.forEach { duration ->
            val heightFraction by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(duration, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(heightFraction)
                    .background(Color(0xFFF0F0F0), RoundedCornerShape(1.dp))
            )
        }
    }
}