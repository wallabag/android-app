package fr.gaulupeau.apps.Poche.ui

import androidx.compose.ui.platform.ComposeView
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

fun ComposeView.showAboutLibraries() {
    setContent {
        LibrariesContainer()
    }
}