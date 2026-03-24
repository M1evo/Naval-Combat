package com.navalcombat.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment

/**
 * Переключает язык приложения между русским (`ru`) и английским (`en`).
 *
 * Текущий язык определяется из [AppCompatDelegate.getApplicationLocales].
 * Если локаль не была явно установлена (пустой список), используется
 * системная локаль из конфигурации ресурсов.
 *
 * Новый язык применяется глобально ко всему приложению через
 * [AppCompatDelegate.setApplicationLocales], что приводит к
 * пересозданию текущей Activity и обновлению всех строковых ресурсов.
 *
 * Функция реализована как расширение [Fragment], чтобы иметь доступ
 * к [Fragment.getResources] для определения текущей системной локали.
 *
 * @receiver Любой [Fragment], с экрана которого инициируется переключение языка.
 *
 * @see AppCompatDelegate.setApplicationLocales
 */
fun Fragment.toggleAppLanguage() {
    val currentLocales = AppCompatDelegate.getApplicationLocales()
    val currentLang = if (currentLocales.isEmpty) {
        resources.configuration.locales[0].language
    } else {
        currentLocales[0]?.language ?: "en"
    }

    val newLang = if (currentLang == "ru") "en" else "ru"
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newLang))
}
