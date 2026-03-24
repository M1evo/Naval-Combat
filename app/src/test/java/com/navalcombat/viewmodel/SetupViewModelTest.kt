package com.navalcombat.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.navalcombat.model.Board
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Юнит-тесты для [SetupViewModel].
 *
 * Проверяют основные сценарии расстановки кораблей:
 * - Полный цикл размещения флота первым и вторым игроком.
 * - Отмена и удаление размещённых кораблей.
 * - Завершение расстановки и хранение стартового игрока.
 * - Сброс состояния для новой партии.
 *
 * Используется [InstantTaskExecutorRule] для синхронного выполнения
 * операций [LiveData][androidx.lifecycle.LiveData] в тестовом потоке.
 */
class SetupViewModelTest {

    /**
     * Правило, заменяющее фоновый executor [LiveData]
     * на синхронный, чтобы `setValue` срабатывал немедленно.
     */
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    /**
     * Проверяет полный цикл расстановки первого игрока с последующей
     * передачей устройства второму.
     *
     * Ожидания после размещения всех кораблей игроком 0:
     * - [SetupViewModel.currentPlayerIndex] = 0.
     * - [SetupViewModel.setupTransitionPlayerIndex] = 1 (нужен промежуточный экран).
     * - Все корабли установлены на доску ([Board.REQUIRED_SHIPS.size]).
     * - [SetupViewModel.setupComplete] = `false` (второй ещё не расставлял).
     *
     * После [confirmSetupTransition]:
     * - Переключение на игрока 1.
     * - Пул неразмещённых кораблей восстановлен.
     * - Доска игрока 1 пуста.
     */
    @Test
    fun playerOnePlacementCompletesWithHandoverToPlayerTwo() {
        val viewModel = SetupViewModel(SavedStateHandle())
        viewModel.startNewGame("Alice", "Bob")

        placeDefaultFleet(viewModel)

        assertEquals(0, viewModel.currentPlayerIndex.value)
        assertEquals(1, viewModel.setupTransitionPlayerIndex.value)
        assertTrue(viewModel.unplacedShips.value.orEmpty().isEmpty())
        assertFalse(viewModel.setupComplete.value ?: false)
        assertEquals(Board.REQUIRED_SHIPS.size, viewModel.getBoard(0).getShips().size)

        viewModel.confirmSetupTransition()

        assertEquals(1, viewModel.currentPlayerIndex.value)
        assertNull(viewModel.setupTransitionPlayerIndex.value)
        assertEquals(Board.REQUIRED_SHIPS, viewModel.unplacedShips.value)
        assertEquals(0, viewModel.getBoard(1).getShips().size)
    }

    /**
     * Проверяет, что [undoLastPlacement] и [removeShipAt] корректно
     * возвращают корабль обратно в пул неразмещённых.
     *
     * Сценарий 1 (undo):
     * - Разместить 4-палубный → пул не содержит 4 → undo → пул снова содержит 4.
     *
     * Сценарий 2 (removeShipAt):
     * - Разместить 4-палубный → удалить по клетке (0,0) → пул снова содержит 4.
     */
    @Test
    fun undoAndRemoveReturnShipsBackToPool() {
        val viewModel = SetupViewModel(SavedStateHandle())
        viewModel.startNewGame("Alice", "Bob")

        viewModel.setSelectedShipSize(4)
        assertTrue(viewModel.placeSelectedShip(0, 0))
        assertEquals(0, viewModel.unplacedShips.value.orEmpty().count { it == 4 })

        assertTrue(viewModel.undoLastPlacement())
        assertEquals(1, viewModel.unplacedShips.value.orEmpty().count { it == 4 })
        assertTrue(viewModel.getCurrentBoard().getShips().isEmpty())

        viewModel.setSelectedShipSize(4)
        assertTrue(viewModel.placeSelectedShip(0, 0))
        assertTrue(viewModel.removeShipAt(0, 0))
        assertEquals(1, viewModel.unplacedShips.value.orEmpty().count { it == 4 })
        assertTrue(viewModel.getCurrentBoard().getShips().isEmpty())
    }

    /**
     * Проверяет, что после полной расстановки обоими игроками
     * флаг [SetupViewModel.setupComplete] становится `true`.
     */
    @Test
    fun playerTwoPlacementMarksSetupAsComplete() {
        val viewModel = SetupViewModel(SavedStateHandle())
        viewModel.startNewGame("Alice", "Bob")

        placeDefaultFleet(viewModel)
        viewModel.confirmSetupTransition()
        placeDefaultFleet(viewModel)

        assertNull(viewModel.setupTransitionPlayerIndex.value)
        assertTrue(viewModel.setupComplete.value ?: false)
    }

    /**
     * Проверяет сохранение и чтение индекса стартового игрока.
     *
     * По умолчанию [getStartingPlayerIndex] возвращает `0`,
     * а [startingPlayerIndex.value] — `null` (ещё не выбран).
     */
    @Test
    fun selectedStartingPlayerIsStored() {
        val viewModel = SetupViewModel(SavedStateHandle())
        viewModel.startNewGame("Alice", "Bob")

        assertEquals(0, viewModel.getStartingPlayerIndex())
        assertNull(viewModel.startingPlayerIndex.value)

        viewModel.setStartingPlayer(1)

        assertEquals(1, viewModel.getStartingPlayerIndex())
        assertEquals(1, viewModel.startingPlayerIndex.value)
    }

    /**
     * Проверяет, что [resetForNewGame] сбрасывает все доски и состояние,
     * но сохраняет имена игроков (режим `keepNames = true`).
     */
    @Test
    fun resetForNewGameClearsBoardsAndState() {
        val viewModel = SetupViewModel(SavedStateHandle())
        viewModel.startNewGame("Alice", "Bob")

        viewModel.setSelectedShipSize(4)
        assertTrue(viewModel.placeSelectedShip(0, 0))

        viewModel.resetForNewGame()

        assertEquals(0, viewModel.currentPlayerIndex.value)
        assertEquals(Board.REQUIRED_SHIPS, viewModel.unplacedShips.value)
        assertNull(viewModel.setupTransitionPlayerIndex.value)
        assertNull(viewModel.startingPlayerIndex.value)
        assertFalse(viewModel.setupComplete.value ?: false)
        assertTrue(viewModel.getBoard(0).getShips().isEmpty())
        assertTrue(viewModel.getBoard(1).getShips().isEmpty())
        assertEquals("Alice", viewModel.getPlayerName(0))
        assertEquals("Bob", viewModel.getPlayerName(1))
    }

    /**
     * Вспомогательный метод: размещает полный стандартный флот
     * для текущего игрока в предопределённых позициях.
     *
     * Расстановка гарантированно не нарушает правил (нет пересечений и касаний).
     * Все корабли размещаются горизонтально (ориентация по умолчанию).
     *
     * @param viewModel ViewModel, в котором выполняется расстановка.
     */
    private fun placeDefaultFleet(viewModel: SetupViewModel) {
        val placements = listOf(
            Triple(4, 0, 0),
            Triple(3, 2, 0),
            Triple(3, 4, 0),
            Triple(2, 6, 0),
            Triple(2, 8, 0),
            Triple(2, 6, 3),
            Triple(1, 0, 6),
            Triple(1, 2, 6),
            Triple(1, 4, 6),
            Triple(1, 8, 6)
        )

        for ((size, row, col) in placements) {
            viewModel.setSelectedShipSize(size)
            assertTrue(viewModel.placeSelectedShip(row, col))
        }
    }
}
