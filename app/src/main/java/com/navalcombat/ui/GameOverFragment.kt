package com.navalcombat.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.navalcombat.R
import com.navalcombat.databinding.FragmentGameOverBinding
import com.navalcombat.viewmodel.GameViewModel

/**
 * Экран окончания партии.
 *
 * Показывает имя победителя и предоставляет кнопку возврата в главное меню.
 * При нажатии «Вернуться в меню» сбрасывает состояние матча
 * в [GameViewModel] и навигирует обратно к [WelcomeFragment].
 *
 * Фрагмент попадает в back-stack с параметром `popUpToInclusive`,
 * что очищает стек навигации до корня, предотвращая возврат
 * к игровому экрану кнопкой «Назад».
 *
 * @see GameViewModel.getWinnerName
 * @see GameViewModel.resetGame
 */
class GameOverFragment : Fragment() {

    /** Binding-обёртка для `fragment_game_over.xml`. Обнуляется в [onDestroyView]. */
    private var _binding: FragmentGameOverBinding? = null

    /** Non-null accessor для [_binding]; безопасен между [onCreateView] и [onDestroyView]. */
    private val binding get() = _binding!!

    /** Shared ViewModel, хранящий результат завершённой партии. */
    private val gameViewModel: GameViewModel by activityViewModels()

    /**
     * Создаёт корневой [View] фрагмента из `fragment_game_over.xml`.
     *
     * @return Корневой элемент иерархии view.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGameOverBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Показывает имя победителя и настраивает кнопку возврата в меню.
     *
     * Текст победителя формируется из строкового ресурса `winner_message`
     * с подстановкой имени через [GameViewModel.getWinnerName].
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val winnerName = gameViewModel.getWinnerName()
        binding.winnerText.text = getString(R.string.winner_message, winnerName)

        binding.returnToMenuButton.setOnClickListener {
            gameViewModel.resetGame()
            findNavController().navigate(R.id.action_gameOver_to_welcome)
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
