package com.navalcombat.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.navalcombat.R
import com.navalcombat.databinding.ActivityMainBinding

/**
 * Единственная Activity приложения «Морской бой».
 *
 * Выполняет роль контейнера для фрагментов навигации.
 * Весь экранный поток (приветствие → расстановка → игра → результат)
 * управляется через Navigation Component и граф `nav_graph.xml`,
 * а не через смену Activity.
 *
 * Используется Data Binding для подключения `activity_main.xml`,
 * содержащего единственный [NavHostFragment][androidx.navigation.fragment.NavHostFragment].
 *
 * @see com.navalcombat.ui.WelcomeFragment
 * @see com.navalcombat.ui.SetupFragment
 * @see com.navalcombat.ui.GameFragment
 * @see com.navalcombat.ui.GameOverFragment
 */
class MainActivity : AppCompatActivity() {

    /**
     * Инициализирует корневой layout через Data Binding.
     *
     * Дальнейшая навигация полностью делегируется
     * [NavHostFragment][androidx.navigation.fragment.NavHostFragment],
     * встроенному в `activity_main.xml`.
     *
     * @param savedInstanceState Сохранённое состояние (обрабатывается суперклассом).
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)
    }
}
