package com.linjing.shareku.ui.theme

enum class ThemeMode { LIGHT, DARK, SYSTEM;
    companion object {
        fun fromName(name: String?) = entries.find { it.name == name } ?: SYSTEM
    }
}