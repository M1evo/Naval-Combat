package com.navalcombat.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.navalcombat.R
import com.navalcombat.databinding.FragmentGameBinding
import com.navalcombat.model.ShotResult
import com.navalcombat.model.GameState
import com.navalcombat.viewmodel.GameViewModel
import com.navalcombat.viewmodel.SetupViewModel

/**
 * Экран основной стрельбы — центральный фрагмент игрового процесса.
 *
 * Отображает два поля:
 * - **Верхнее** ([BoardView.DisplayMode.OPPONENT_BOARD]) — поле соперника,
 *   по которому текущий игрок совершает выстрелы касанием.
 * - **Нижнее** ([BoardView.DisplayMode.OWN_BOARD]) — собственное поле
 *   для наблюдения за состоянием своих кораблей (только чтение).
 *
 * ### Жизненный цикл матча
 * При первом показе фрагмент проверяет, инициализирован ли матч
 * в [GameViewModel]. Если нет — вызывает [GameViewModel.initGame],
 * передавая готовых игроков из [SetupViewModel].
 *
 * ### Обработка состояний ([GameState])
 * - [GameState.Playing] — обновляет оба поля и текст хода.
 * - [GameState.TurnTransition] — навигирует к [TurnTransitionFragment].
 * - [GameState.GameOver] — навигирует к [GameOverFragment].
 *
 * ### Результат выстрела
 * Текст и цвет результата ([ShotResult]) обновляются через наблюдатель
 * [GameViewModel.lastShotResult].
 *
 * @see GameViewModel
 * @see BoardView
 */
class GameFragment : Fragment() {

    /** Binding-обёртка для `fragment_game.xml`. Обнуляется в [onDestroyView]. */
    private var _binding: FragmentGameBinding? = null

    /** Non-null accessor для [_binding]; безопасен между [onCreateView] и [onDestroyView]. */
    private val binding get() = _binding!!

    /** Shared ViewModel, управляющий логикой выстрелов и сменой ходов. */
    private val gameViewModel: GameViewModel by activityViewModels()

    /** Shared ViewModel, хранящий данные расстановки и имена игроков. */
    private val setupViewModel: SetupViewModel by activityViewModels()

    /**
     * Создаёт корневой [View] фрагмента из `fragment_game.xml`.
     *
     * @return Корневой элемент иерархии view.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Инициализирует игровой экран: подключает ViewModel к UI.
     *
     * Выполняемые действия:
     * 1. Инициализирует матч, если он ещё не запущен.
     * 2. Настраивает режимы отображения для обоих [BoardView].
     * 3. Подключает наблюдатели [GameState], [boardUpdated] и [lastShotResult].
     * 4. Привязывает обработчик нажатий по полю соперника к [GameViewModel.fireShot].
     * 5. Запрещает клики по собственному полю (только просмотр).
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!gameViewModel.isGameInitialized()) {
            gameViewModel.initGame(
                setupViewModel.createPlayers(
                    defaultPlayer1Name = getString(R.string.default_player_1),
                    defaultPlayer2Name = getString(R.string.default_player_2)
                ),
                startingPlayerIndex = setupViewModel.getStartingPlayerIndex()
            )
        }

        val opponentBoardView = binding.opponentBoardView
        val ownBoardView = binding.ownBoardView

        opponentBoardView.displayMode = BoardView.DisplayMode.OPPONENT_BOARD
        ownBoardView.displayMode = BoardView.DisplayMode.OWN_BOARD

        gameViewModel.gameState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is GameState.TurnTransition -> {
                    findNavController().navigate(R.id.action_game_to_turnTransition)
                }
                is GameState.GameOver -> {
                    findNavController().navigate(R.id.action_game_to_gameOver)
                }
                is GameState.Playing -> {
                    updateBoards()
                    updateTurnInfo()
                }
                else -> Unit
            }
        }

        gameViewModel.boardUpdated.observe(viewLifecycleOwner) { _ ->
            updateBoards()
        }

        gameViewModel.lastShotResult.observe(viewLifecycleOwner) { result ->
            binding.shotResultText.text = when (result) {
                ShotResult.HIT -> getString(R.string.shot_hit)
                ShotResult.MISS -> getString(R.string.shot_miss)
                ShotResult.SUNK -> getString(R.string.shot_sunk)
                ShotResult.ALREADY_SHOT -> getString(R.string.shot_already)
                null -> ""
            }
            binding.shotResultText.setTextColor(
                when (result) {
                    ShotResult.HIT, ShotResult.SUNK ->
                        ContextCompat.getColor(requireContext(), R.color.hit_color)
                    else ->
                        ContextCompat.getColor(requireContext(), R.color.text_secondary)
                }
            )
        }

        opponentBoardView.onCellClickListener = { row, col ->
            gameViewModel.fireShot(row, col)
        }

        ownBoardView.onCellClickListener = null

        updateBoards()
        updateTurnInfo()
    }

    /**
     * Обновляет данные обоих [BoardView] из [GameViewModel].
     *
     * Верхнему полю передаётся доска соперника, нижнему — доска текущего игрока.
     */
    private fun updateBoards() {
        binding.opponentBoardView.setBoard(gameViewModel.getOpponentBoard())
        binding.ownBoardView.setBoard(gameViewModel.getCurrentPlayerBoard())
    }

    /**
     * Обновляет текстовую подпись «Ход: <имя игрока>».
     */
    private fun updateTurnInfo() {
        binding.turnInfoText.text = getString(
            R.string.turn_info,
            gameViewModel.getCurrentPlayerName()
        )
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
