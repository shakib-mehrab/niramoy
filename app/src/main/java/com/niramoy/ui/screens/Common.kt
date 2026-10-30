package com.niramoy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.niramoy.ui.MainViewModel
import com.niramoy.ui.components.ScreenColumn
import com.niramoy.ui.components.TopBar
import com.niramoy.ui.theme.AppColors

/** Standard screen: top bar (back, title, read-aloud, language) + scrolling body. */
@Composable
fun Page(
    vm: MainViewModel,
    title: String,
    onBack: (() -> Unit)? = null,
    speak: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val settings by vm.settings.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar(title, settings.language, onToggleLang = { vm.toggleLanguage() }, onBack = onBack, speakText = speak, trailing = trailing)
        ScreenColumn(content = content)
    }
}
