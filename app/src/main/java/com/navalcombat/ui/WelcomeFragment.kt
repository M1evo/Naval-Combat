package com.navalcombat.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.navalcombat.R
import com.navalcombat.databinding.FragmentWelcomeBinding
import com.navalcombat.viewmodel.GameViewModel
import com.navalcombat.viewmodel.SetupViewModel

/**
 * Экран приветствия — стартовый фрагмент приложения.
 *
 * Предоставляет пользователям поля ввода для задания имён двух игроков,
 * кнопку переключения языка и кнопку запуска новой партии.
 *
 * ### Валидация ввода
 * - Оба имени должны быть непустыми.
 * - Имена не должны совпадать.
 *
 * При успешной валидации сбрасывается предыдущая партия в [GameViewModel],
 * инициализируется новая расстановка в [SetupViewModel], и происходит
 * навигация к [SetupFragment].
 *
 * @see SetupFragment
 */
class WelcomeFragment : Fragment() {

    /** Binding-обёртка для `fragment_welcome.xml`. Обнуляется в [onDestroyView]. */
    private var _binding: FragmentWelcomeBinding? = null

    /** Non-null accessor для [_binding]; безопасен между [onCreateView] и [onDestroyView]. */
    private val binding get() = _binding!!

    /** Shared ViewModel для передачи имён и данных расстановки между фрагментами. */
    private val setupViewModel: SetupViewModel by activityViewModels()

    /** Shared ViewModel для управления игровым процессом. */
    private val gameViewModel: GameViewModel by activityViewModels()

    /**
     * Создаёт корневой [View] фрагмента из layout-файла `fragment_welcome.xml`.
     *
     * @return Корневой элемент иерархии view.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWelcomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Инициализирует UI-компоненты экрана приветствия.
     *
     * Выполняемые действия:
     * 1. Подключает кнопку переключения языка через [toggleAppLanguage].
     * 2. Восстанавливает ранее введённые имена игроков из [SetupViewModel].
     * 3. Настраивает обработчик кнопки «Начать игру» с валидацией имён.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.languageButton.setOnClickListener {
            toggleAppLanguage()
        }

        binding.player1NameInput.setText(setupViewModel.getPlayerName(0))
        binding.player2NameInput.setText(setupViewModel.getPlayerName(1))

        binding.startGameButton.setOnClickListener {
            val name1 = binding.player1NameInput.text.toString().trim()
            val name2 = binding.player2NameInput.text.toString().trim()

            when {
                name1.isEmpty() || name2.isEmpty() -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.error_empty_names),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                name1 == name2 -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.error_same_names),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> {
                    gameViewModel.resetGame()
                    setupViewModel.startNewGame(name1, name2)
                    findNavController().navigate(R.id.action_welcome_to_setup)
                }
            }
        }
    }

    /**
     * Очищает ссылку на binding, чтобы избежать утечек памяти
     * после уничтожения иерархии view фрагмента.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
