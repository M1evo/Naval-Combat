package com.navalcombat.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.navalcombat.model.Board
import com.navalcombat.model.Cell
import com.navalcombat.model.GameState
import com.navalcombat.model.Orientation
import com.navalcombat.model.Player
import com.navalcombat.model.Ship
import com.navalcombat.model.ShotResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Юнит-тесты для [GameViewModel].
 *
 * Проверяют основные сценарии игрового процесса:
 * - Инициализация и сброс матча.
 * - Выбор стартового игрока.
 * - Переход хода при промахе.
 * - Завершение игры при потоплении последнего корабля.
 *
 * Используется [InstantTaskExecutorRule] для синхронного выполнения
 * операций [LiveData][androidx.lifecycle.LiveData] в тестовом потоке.
 */
class GameViewModelTest {

    /**
     * Правило, заменяющее фоновый executor [LiveData]
     * на синхронный, чтобы `setValue` срабатывал немедленно.
     */
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    /**
     * Проверяет, что после [GameViewModel.resetGame] можно заново
     * инициализировать матч с другими игроками.
     *
     * Сценарий:
     * 1. Инициализировать матч с Alice и Bob → матч активен.
     * 2. Вызвать [resetGame] → матч сброшен.
     * 3. Инициализировать с Charlie и Diana → матч снова активен с новым именем.
     */
    @Test
    fun resetAllowsGameReinitialization() {
        val viewModel = GameViewModel()

        viewModel.initGame(
            arrayOf(
                createPlayer("Alice", 9, 9),
                createPlayer("Bob", 0, 0)
            )
        )
        assertTrue(viewModel.isGameInitialized())
        assertEquals("Alice", viewModel.getCurrentPlayerName())

        viewModel.resetGame()
        assertFalse(viewModel.isGameInitialized())

        viewModel.initGame(
            arrayOf(
                createPlayer("Charlie", 9, 9),
                createPlayer("Diana", 0, 0)
            )
        )
        assertTrue(viewModel.isGameInitialized())
        assertEquals("Charlie", viewModel.getCurrentPlayerName())
        assertTrue(viewModel.gameState.value is GameState.Playing)
    }

    /**
     * Проверяет, что параметр `startingPlayerIndex` корректно назначает
     * первый ход указанному игроку.
     *
     * Ожидание: при `startingPlayerIndex = 1` первым ходит Bob.
     */
    @Test
    fun selectedStartingPlayerGetsFirstTurn() {
        val viewModel = GameViewModel()
        viewModel.initGame(
            arrayOf(
                createPlayer("Alice", 9, 9),
                createPlayer("Bob", 0, 0)
            ),
            startingPlayerIndex = 1
        )

        assertEquals("Bob", viewModel.getCurrentPlayerName())
        assertTrue(viewModel.gameState.value is GameState.Playing)
        assertEquals(1, (viewModel.gameState.value as GameState.Playing).currentPlayerIndex)
    }

    /**
     * Проверяет полный цикл смены хода при промахе.
     *
     * Сценарий:
     * 1. Игрок 0 стреляет мимо → состояние [GameState.TurnTransition].
     * 2. Подтверждение перехода → состояние [GameState.Playing] для игрока 1.
     */
    @Test
    fun missMovesGameToTurnTransitionAndConfirmReturnsPlaying() {
        val viewModel = GameViewModel()
        viewModel.initGame(
            arrayOf(
                createPlayer("Alice", 9, 9),
                createPlayer("Bob", 0, 0)
            )
        )

        val result = viewModel.fireShot(5, 5)

        assertEquals(ShotResult.MISS, result)
        assertTrue(viewModel.gameState.value is GameState.TurnTransition)
        assertEquals(1, (viewModel.gameState.value as GameState.TurnTransition).nextPlayerIndex)

        viewModel.confirmTurnTransition()

        assertTrue(viewModel.gameState.value is GameState.Playing)
        assertEquals(1, (viewModel.gameState.value as GameState.Playing).currentPlayerIndex)
    }

    /**
     * Проверяет, что потопление единственного корабля завершает матч.
     *
     * У Bob стоит однопалубный корабль в (0,0). Alice стреляет в (0,0) →
     * результат [ShotResult.SUNK], состояние [GameState.GameOver], победитель — Alice.
     */
    @Test
    fun sinkingLastShipEndsGame() {
        val viewModel = GameViewModel()
        viewModel.initGame(
            arrayOf(
                createPlayer("Alice", 9, 9),
                createPlayer("Bob", 0, 0)
            )
        )

        val result = viewModel.fireShot(0, 0)

        assertEquals(ShotResult.SUNK, result)
        assertTrue(viewModel.gameState.value is GameState.GameOver)
        assertEquals(0, (viewModel.gameState.value as GameState.GameOver).winnerIndex)
        assertEquals("Alice", viewModel.getWinnerName())
    }

    /**
     * Вспомогательный метод: создаёт [Player] с одним однопалубным кораблём.
     *
     * @param name    Имя игрока.
     * @param shipRow Строка размещения корабля.
     * @param shipCol Столбец размещения корабля.
     * @return Игрок с готовым полем.
     * @throws IllegalStateException если размещение корабля не удалось.
     */
    private fun createPlayer(name: String, shipRow: Int, shipCol: Int): Player {
        val board = Board()
        val placed = board.placeShip(Ship(Cell(shipRow, shipCol), 1, Orientation.HORIZONTAL))
        check(placed) { "Test ship placement must succeed." }
        return Player(name, board)
    }
}
