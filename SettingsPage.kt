package com.allworld.radio

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.allworld.radio.model.Station

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(settings: SettingsViewModel, allStations: List<Station>) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val allLabel = stringResource(R.string.all_categories)

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineLarge)

        // --- Выбор языка ---
        val languages = listOf(
            "en" to "English",
            "ru" to "Русский",
            "ro" to "Română",
            "fr" to "Français",
            "es" to "Español",
            "pt" to "Português"
        )
        val currentLangName = languages.find { it.first == settings.language }?.second ?: "English"

        SettingsDropdown(
            label = stringResource(R.string.settings_lang),
            selected = currentLangName,
            options = languages.map { it.second }
        ) { selectedName ->
            val selectedCode = languages.find { it.second == selectedName }?.first ?: "en"
            if (settings.language != selectedCode) {
                settings.updateLanguage(selectedCode)
                activity?.recreate()
            }
        }

        // --- Выбор темы ---
        Text(stringResource(R.string.settings_theme), Modifier.padding(top = 16.dp), fontWeight = FontWeight.Bold)

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppTheme.entries.forEach { t ->
                FilterChip(
                    selected = settings.theme == t,
                    onClick = { settings.updateTheme(t) },
                    label = {
                        Text(when(t) {
                            AppTheme.LIGHT -> stringResource(R.string.theme_light)
                            AppTheme.DARK -> stringResource(R.string.theme_dark)
                            AppTheme.AUTO -> stringResource(R.string.theme_auto)
                        })
                    }
                )
            }
        }

        // --- ПАНЕЛЬ НАСТРОЙКИ ВРЕМЕНИ (Показывается только если выбрано AUTO) ---
        if (settings.theme == AppTheme.AUTO) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.settings_auto_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )

                    // Слайдер начала ночи с подстановкой числа
                    Text(
                        text = stringResource(R.string.settings_night_start, settings.nightStartHour.toInt()),
                        modifier = Modifier.padding(top = 8.dp),
                        fontSize = 14.sp
                    )
                    Slider(
                        value = settings.nightStartHour,
                        onValueChange = { settings.updateNightStart(it) },
                        valueRange = 0f..23f,
                        steps = 23
                    )

                    // Слайдер конца ночи с подстановкой числа
                    Text(
                        text = stringResource(R.string.settings_night_end, settings.nightEndHour.toInt()),
                        fontSize = 14.sp
                    )
                    Slider(
                        value = settings.nightEndHour,
                        onValueChange = { settings.updateNightEnd(it) },
                        valueRange = 0f..23f,
                        steps = 23
                    )

                    Text(
                        text = stringResource(R.string.settings_auto_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- Фильтры Жанров и Стран ---
        val genreOptions = remember(allStations, allLabel) {
            val uniqueGenres = allStations.map { it.genre }.distinct().filter { it.isNotEmpty() }
            val translated = uniqueGenres.map { key ->
                val resId = StationTranslations.genres[key]
                if (resId != null) context.getString(resId) else key
            }.sorted()
            listOf(allLabel) + translated
        }

        val countryOptions = remember(allStations, allLabel) {
            val uniqueCountries = allStations.map { it.country }.distinct().filter { it.isNotEmpty() }
            val translated = uniqueCountries.map { key ->
                val resId = StationTranslations.countries[key]
                if (resId != null) context.getString(resId) else key
            }.sorted()
            listOf(allLabel) + translated
        }

        SettingsDropdown(
            label = stringResource(R.string.settings_genre),
            selected = if (settings.selectedGenre == "Все") allLabel
            else StationTranslations.genres[settings.selectedGenre]?.let { stringResource(it) } ?: settings.selectedGenre,
            options = genreOptions
        ) { selectedDisplayName ->
            if (selectedDisplayName == allLabel) {
                settings.selectedGenre = "Все"
            } else {
                val key = StationTranslations.genres.filterValues { context.getString(it) == selectedDisplayName }.keys.firstOrNull()
                settings.selectedGenre = key ?: selectedDisplayName
            }
        }

        SettingsDropdown(
            label = stringResource(R.string.settings_country),
            selected = if (settings.selectedCountry == "Все") allLabel
            else StationTranslations.countries[settings.selectedCountry]?.let { stringResource(it) } ?: settings.selectedCountry,
            options = countryOptions
        ) { selectedDisplayName ->
            if (selectedDisplayName == allLabel) {
                settings.selectedCountry = "Все"
            } else {
                val key = StationTranslations.countries.filterValues { context.getString(it) == selectedDisplayName }.keys.firstOrNull()
                settings.selectedCountry = key ?: selectedDisplayName
            }
        }

        // --- Буфер ---
        Text(stringResource(R.string.settings_buffer, (settings.bufferSizeMs / 1000).toInt()), Modifier.padding(top = 16.dp))
        Slider(
            value = settings.bufferSizeMs,
            onValueChange = { settings.updateBuffer(it) },
            valueRange = 5000f..60000f
        )

        // --- Наушники ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp).clickable { settings.toggleHeadset(!settings.stopOnHeadsetDisconnect) }
        ) {
            Checkbox(
                checked = settings.stopOnHeadsetDisconnect,
                onCheckedChange = { settings.toggleHeadset(it) }
            )
            Text(stringResource(R.string.settings_headset), modifier = Modifier.padding(start = 8.dp))
        }

        // --- Не отключать экран при работе приложения ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Checkbox(
                checked = settings.keepScreenOn,
                onCheckedChange = { settings.toggleKeepScreenOn(it) }
            )
            Text(
                text = stringResource(R.string.settings_keep_screen_on),
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // --- Размер иконок ---
        Text(stringResource(R.string.settings_icon_size, settings.iconSize.toInt()), Modifier.padding(top = 16.dp))
        Slider(
            value = settings.iconSize,
            onValueChange = { settings.updateIconSize(it) },
            valueRange = 40f..100f
        )

        Spacer(Modifier.height(32.dp))

        // --- Кнопка поддержки ---
        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/All_World_Radio"))
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📢", fontSize = 20.sp)
                Spacer(Modifier.width(12.dp))
                Text(text = stringResource(R.string.settings_support), fontWeight = FontWeight.Bold)
            }
        }

        Text(
            text = "v 1.1",
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDropdown(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt) },
                        onClick = {
                            onSelect(opt)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}