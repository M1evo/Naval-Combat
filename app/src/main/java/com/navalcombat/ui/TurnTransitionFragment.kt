package com.navalcombat.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.navalcombat.R
import com.navalcombat.databinding.FragmentTurnTransitionBinding
import com.navalcombat.model.GameState
import com.navalcombat.viewmodel.GameViewModel
import com.navalcombat.viewmodel.SetupViewModel

/**
 * Промежуточный экран передачи устройства между игроками.
 *
 * Необходим для режима «hotseat» (два игрока на одном устройстве):
 * скрывает текущее поле перед тем, как устройство перейдёт в руки
 * следующего игрока. Показывает сообщение с именем следующего игрока
 * и кнопку «Продолжить».
 *
 * ### Два контекста использования
 *
 * | Контекст        | Определяющий ViewModel   | Навигация кнопки «Продолжить» |
 * |-----------------|--------------------------|-------------------------------|
 * | Смена хода      | [GameViewModel]          | → [GameFragment]              |
 * | Расстановка     | [SetupViewModel]         | → [SetupFragment]             |
 *
 * Фрагмент определяет контекст по текущему [GameState]:
 * если это [GameState.TurnTransition] — значит идёт игра;
 * иначе — используются данные [SetupViewModel].
 *
 * @see GameViewModel.confirmTurnTransition
 * @see SetupViewModel.confirmSetupTransition
 */
class TurnTransitionFragment : Fragment() {

    /** Binding-обёртка для `fragment_turn_transition.xml`. Обнуляется в [onDestroyView]. */
    private var _binding: FragmentTurnTransitionBinding? = null

    /** Non-null accessor для [_binding]; безопасен между [onCreateView] и [onDestroyView]. */
    private val binding get() = _binding!!

    /** Shared ViewModel для получения данных о текущем ходе во время игры. */
    private val gameViewModel: GameViewModel by activityViewModels()

    /** Shared ViewModel для получения данных о текущем этапе расстановки. */
    private val setupViewModel: SetupViewModel by activityViewModels()

    /**
     * Создаёт корневой [View] фрагмента из `fragment_turn_transition.xml`.
     *
     * @return Корневой элемент иерархии view.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTurnTransitionBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Определяет контекст перехода и настраивает UI.
     *
     * Если [GameViewModel.gameState] — [GameState.TurnTransition]:
     * - Сообщение содержит имя следующего стреляющего игрока.
     * - Кнопка подтверждает переход хода ([GameViewModel.confirmTurnTransition])
     *   и навигирует обратно к [GameFragment].
     *
     * Иначе (расстановка):
     * - Сообщение содержит имя игрока, которому нужно передать устройство.
     * - Кнопка подтверждает переход расстановки ([SetupViewModel.confirmSetupTransition])
     *   и навигирует обратно к [SetupFragment].
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val gameState = gameViewModel.gameState.value

        if (gameState is GameState.TurnTransition) {
            val nextPlayerName = gameViewModel.getPlayerName(gameState.nextPlayerIndex)
            binding.transitionMessage.text =
                getString(R.string.turn_transition_message, nextPlayerName)

            binding.continueButton.setOnClickListener {
                gameViewModel.confirmTurnTransition()
                findNavController().navigate(R.id.action_turnTransition_to_game)
            }
        } else {
            val setupNextPlayer = setupViewModel.setupTransitionPlayerIndex.value ?: 1
            val nextPlayerName = setupViewModel.getPlayerName(setupNextPlayer)
            binding.transitionMessage.text =
                getString(R.string.setup_transition_message, nextPlayerName)

            binding.continueButton.setOnClickListener {
                setupViewModel.confirmSetupTransition()
                findNavController().navigate(R.id.action_turnTransition_to_setup)
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
