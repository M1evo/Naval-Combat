package com.navalcombat.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.navalcombat.model.*

/**
 * ViewModel экрана расстановки кораблей.
 *
 * Управляет полным процессом расстановки для обоих игроков:
 * выбор размера корабля, размещение на поле, удаление, отмена последнего
 * действия, сброс и переход между игроками.
 *
 * ### Сохранение состояния
 * Имена игроков сохраняются через [SavedStateHandle], что позволяет
 * восстановить их после пересоздания процесса. Остальное состояние
 * (поля, пул кораблей) не сериализуется — при уничтожении ViewModel
 * расстановку придётся начать заново.
 *
 * ### Последовательность работы
 * 1. [startNewGame] — сохраняет имена и сбрасывает состояние.
 * 2. Игрок 0 размещает корабли → все размещены → [setupTransitionPlayerIndex] = 1.
 * 3. UI показывает [TurnTransitionFragment][com.navalcombat.ui.TurnTransitionFragment].
 * 4. [confirmSetupTransition] — переключает на игрока 1.
 * 5. Игрок 1 размещает корабли → все размещены → [setupComplete] = `true`.
 * 6. UI показывает диалог выбора стартового игрока → [setStartingPlayer].
 * 7. [createPlayers] — отдаёт готовых [Player] в [GameViewModel].
 *
 * @property savedStateHandle Хранилище для сохранения имён игроков между пересозданиями.
 *
 * @see com.navalcombat.ui.SetupFragment
 * @see GameViewModel
 */
class SetupViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    /** Имя первого игрока. Сохраняется через [SavedStateHandle]. */
    val player1Name = savedStateHandle.getLiveData("player1Name", "")

    /** Имя второго игрока. Сохраняется через [SavedStateHandle]. */
    val player2Name = savedStateHandle.getLiveData("player2Name", "")

    /**
     * Игровые поля обоих игроков.
     *
     * Индекс массива соответствует индексу игрока (0 или 1).
     * Поля хранятся в памяти и не сериализуются.
     */
    private val boards = arrayOf(Board(), Board())

    /** Индекс игрока, который сейчас расставляет корабли (0 или 1). */
    private val _currentPlayerIndex = MutableLiveData(0)

    /** Публичный неизменяемый доступ к [_currentPlayerIndex]. */
    val currentPlayerIndex: LiveData<Int> = _currentPlayerIndex

    /**
     * Список размеров кораблей, ещё не размещённых текущим игроком.
     *
     * Упорядочен по убыванию размера. Обновляется при размещении,
     * удалении, отмене и сбросе.
     */
    private val _unplacedShips = MutableLiveData<List<Int>>(Board.REQUIRED_SHIPS.toList())

    /** Публичный неизменяемый доступ к [_unplacedShips]. */
    val unplacedShips: LiveData<List<Int>> = _unplacedShips

    /**
     * Размер корабля, выбранного для размещения.
     *
     * `null` — если ни один размер не выбран (все размещены или сброшен выбор).
     */
    private val _selectedShipSize = MutableLiveData<Int?>(Board.REQUIRED_SHIPS.firstOrNull())

    /** Публичный неизменяемый доступ к [_selectedShipSize]. */
    val selectedShipSize: LiveData<Int?> = _selectedShipSize

    /** Текущая ориентация размещения (горизонтальная/вертикальная). */
    private val _currentOrientation = MutableLiveData(Orientation.HORIZONTAL)

    /** Публичный неизменяемый доступ к [_currentOrientation]. */
    val currentOrientation: LiveData<Orientation> = _currentOrientation

    /**
     * Индекс игрока для промежуточного экрана передачи устройства.
     *
     * `null` — промежуточный экран не нужен (расстановка ещё не завершена
     * или переход уже подтверждён). Устанавливается в `1` после завершения
     * расстановки первого игрока.
     */
    private val _setupTransitionPlayerIndex = MutableLiveData<Int?>(null)

    /** Публичный неизменяемый доступ к [_setupTransitionPlayerIndex]. */
    val setupTransitionPlayerIndex: LiveData<Int?> = _setupTransitionPlayerIndex

    /**
     * Флаг полного завершения расстановки обоими игроками.
     *
     * `true` — оба игрока разместили все корабли; можно переходить к матчу.
     */
    private val _setupComplete = MutableLiveData(false)

    /** Публичный неизменяемый доступ к [_setupComplete]. */
    val setupComplete: LiveData<Boolean> = _setupComplete

    /**
     * Индекс игрока, который будет стрелять первым.
     *
     * `null` — выбор ещё не сделан. Выбирается пользователем через диалог
     * в [SetupFragment][com.navalcombat.ui.SetupFragment] после завершения расстановки.
     */
    private val _startingPlayerIndex = MutableLiveData<Int?>(null)

    /** Публичный неизменяемый доступ к [_startingPlayerIndex]. */
    val startingPlayerIndex: LiveData<Int?> = _startingPlayerIndex

    /**
     * Счётчик обновления поля.
     *
     * Инкрементируется при любом изменении доски. Используется как триггер
     * для перерисовки [BoardView][com.navalcombat.ui.BoardView].
     */
    private val _boardUpdated = MutableLiveData(0)

    /** Публичный неизменяемый доступ к [_boardUpdated]. */
    val boardUpdated: LiveData<Int> = _boardUpdated

    /**
     * Возвращает имя игрока по индексу.
     *
     * @param playerIndex Индекс игрока (0 — первый, 1 — второй).
     * @return Имя игрока, или пустая строка, если LiveData ещё не содержит значения.
     */
    fun getPlayerName(playerIndex: Int): String {
        return if (playerIndex == 0) player1Name.value ?: "" else player2Name.value ?: ""
    }

    /**
     * Возвращает [Board] игрока, который сейчас расставляет корабли.
     *
     * @return Поле текущего игрока.
     */
    fun getCurrentBoard(): Board = boards[_currentPlayerIndex.value ?: 0]

    /**
     * Возвращает [Board] игрока по индексу.
     *
     * @param playerIndex Индекс игрока (0 или 1).
     * @return Поле указанного игрока.
     */
    fun getBoard(playerIndex: Int): Board = boards[playerIndex]

    /**
     * Сохраняет имена игроков и подготавливает ViewModel к новой партии.
     *
     * Имена записываются в [SavedStateHandle], затем вызывается
     * [resetForNewGame] с сохранением имён.
     *
     * @param name1 Имя первого игрока.
     * @param name2 Имя второго игрока.
     */
    fun startNewGame(name1: String, name2: String) {
        savedStateHandle["player1Name"] = name1
        savedStateHandle["player2Name"] = name2
        resetForNewGame(keepNames = true)
    }

    /**
     * Сбрасывает всё состояние расстановки в начальные значения.
     *
     * Очищает оба поля, восстанавливает полный пул кораблей,
     * сбрасывает ориентацию, выбор, флаги перехода и завершения.
     *
     * @param keepNames Если `true`, имена игроков в [SavedStateHandle] сохраняются.
     *                  Если `false` — имена очищаются (используется при полном сбросе).
     */
    fun resetForNewGame(keepNames: Boolean = true) {
        if (!keepNames) {
            savedStateHandle["player1Name"] = ""
            savedStateHandle["player2Name"] = ""
        }

        boards.forEach { it.clear() }
        _currentPlayerIndex.value = 0
        _unplacedShips.value = Board.REQUIRED_SHIPS.toList()
        _selectedShipSize.value = Board.REQUIRED_SHIPS.firstOrNull()
        _currentOrientation.value = Orientation.HORIZONTAL
        _setupTransitionPlayerIndex.value = null
        _setupComplete.value = false
        _startingPlayerIndex.value = null
        bumpBoardUpdate()
    }

    /**
     * Сохраняет индекс игрока, который будет стрелять первым.
     *
     * Значение ограничивается диапазоном `[0, 1]`.
     *
     * @param playerIndex Индекс стартового игрока.
     */
    fun setStartingPlayer(playerIndex: Int) {
        _startingPlayerIndex.value = playerIndex.coerceIn(0, 1)
    }

    /**
     * Возвращает индекс стартового игрока.
     *
     * @return Индекс выбранного стартового игрока, или `0` по умолчанию.
     */
    fun getStartingPlayerIndex(): Int = _startingPlayerIndex.value ?: 0

    /**
     * Устанавливает выбранный размер корабля для размещения.
     *
     * Если указанный размер отсутствует в текущем пуле неразмещённых кораблей,
     * автоматически выбирается первый доступный размер.
     *
     * @param size Желаемый размер корабля.
     */
    fun setSelectedShipSize(size: Int) {
        val availableShips = _unplacedShips.value ?: emptyList()
        _selectedShipSize.value = if (size in availableShips) size else availableShips.firstOrNull()
    }

    /**
     * Переключает ориентацию размещения между [Orientation.HORIZONTAL]
     * и [Orientation.VERTICAL].
     */
    fun toggleOrientation() {
        _currentOrientation.value = if (_currentOrientation.value == Orientation.HORIZONTAL) {
            Orientation.VERTICAL
        } else {
            Orientation.HORIZONTAL
        }
    }

    /**
     * Пытается разместить выбранный корабль в указанных координатах.
     *
     * Использует текущие [selectedShipSize] и [currentOrientation].
     *
     * @param row Индекс строки начальной клетки (0-based).
     * @param col Индекс столбца начальной клетки (0-based).
     * @return `true`, если корабль успешно размещён; `false` — при нарушении правил
     *         или отсутствии выбранного размера.
     */
    fun placeSelectedShip(row: Int, col: Int): Boolean {
        val size = _selectedShipSize.value ?: return false
        val orientation = _currentOrientation.value ?: Orientation.HORIZONTAL
        return placeShipFromPool(size, orientation, row, col)
    }

    /**
     * Внутренний метод размещения корабля из пула неразмещённых.
     *
     * Проверяет наличие корабля указанного размера в пуле, создаёт [Ship],
     * пытается разместить его на [Board]. При успехе обновляет пул и проверяет
     * прогресс расстановки.
     *
     * @param size        Размер корабля.
     * @param orientation Ориентация размещения.
     * @param row         Индекс строки начальной клетки.
     * @param col         Индекс столбца начальной клетки.
     * @return `true`, если корабль был успешно размещён.
     */
    private fun placeShipFromPool(size: Int, orientation: Orientation, row: Int, col: Int): Boolean {
        val currentList = (_unplacedShips.value ?: emptyList()).toMutableList()
        if (!currentList.contains(size)) return false

        val board = getCurrentBoard()
        val ship = Ship(Cell(row, col), size, orientation)

        if (!board.placeShip(ship)) return false

        currentList.remove(size)
        applyUnplacedShips(currentList)
        bumpBoardUpdate()
        checkSetupProgress()
        return true
    }

    /**
     * Удаляет корабль из указанной клетки и возвращает его размер в пул.
     *
     * Вызывается при длительном нажатии по полю на экране расстановки.
     *
     * @param row Индекс строки клетки (0-based).
     * @param col Индекс столбца клетки (0-based).
     * @return `true`, если корабль был найден и удалён; `false` — если клетка пуста.
     */
    fun removeShipAt(row: Int, col: Int): Boolean {
        val ship = getCurrentBoard().removeShipAt(Cell(row, col)) ?: return false
        val currentList = (_unplacedShips.value ?: emptyList()).toMutableList()
        currentList.add(ship.size)
        applyUnplacedShips(currentList)
        bumpBoardUpdate()
        checkSetupProgress()
        return true
    }

    /**
     * Отменяет последнее размещение корабля и возвращает его в пул.
     *
     * @return `true`, если было что отменять; `false` — если поле пустое.
     */
    fun undoLastPlacement(): Boolean {
        val ship = getCurrentBoard().removeLastShip() ?: return false
        val currentList = (_unplacedShips.value ?: emptyList()).toMutableList()
        currentList.add(ship.size)
        applyUnplacedShips(currentList)
        bumpBoardUpdate()
        checkSetupProgress()
        return true
    }

    /**
     * Подтверждает передачу устройства второму игроку для расстановки.
     *
     * Переключает [currentPlayerIndex] на следующего игрока, восстанавливает
     * полный пул кораблей и сбрасывает настройки ориентации и выбора.
     */
    fun confirmSetupTransition() {
        val nextPlayer = _setupTransitionPlayerIndex.value ?: return
        _currentPlayerIndex.value = nextPlayer
        _unplacedShips.value = Board.REQUIRED_SHIPS.toList()
        _selectedShipSize.value = Board.REQUIRED_SHIPS.firstOrNull()
        _currentOrientation.value = Orientation.HORIZONTAL
        _setupTransitionPlayerIndex.value = null
        _setupComplete.value = false
        _startingPlayerIndex.value = null
        bumpBoardUpdate()
    }

    /**
     * Проверяет прогресс расстановки и обновляет флаги перехода.
     *
     * - Если у текущего игрока ещё остались неразмещённые корабли — ничего не делает.
     * - Если игрок 0 завершил расстановку — устанавливает [setupTransitionPlayerIndex] = 1.
     * - Если игрок 1 завершил расстановку — устанавливает [setupComplete] = `true`.
     */
    private fun checkSetupProgress() {
        val remaining = _unplacedShips.value ?: emptyList()
        if (remaining.isNotEmpty()) {
            _setupTransitionPlayerIndex.value = null
            _setupComplete.value = false
            return
        }

        val currentPlayer = _currentPlayerIndex.value ?: 0
        if (currentPlayer == 0) {
            _setupTransitionPlayerIndex.value = 1
            _setupComplete.value = false
        } else {
            _setupTransitionPlayerIndex.value = null
            _setupComplete.value = true
        }
    }

    /**
     * Обновляет список неразмещённых кораблей и корректирует текущий выбор.
     *
     * Список сортируется по убыванию размера. Если ранее выбранный размер
     * больше не доступен в пуле, автоматически выбирается первый доступный.
     *
     * @param updated Обновлённый список размеров.
     */
    private fun applyUnplacedShips(updated: MutableList<Int>) {
        updated.sortDescending()
        _unplacedShips.value = updated

        _selectedShipSize.value = when {
            updated.isEmpty() -> null
            _selectedShipSize.value in updated -> _selectedShipSize.value
            else -> updated.first()
        }
    }

    /**
     * Инкрементирует счётчик [_boardUpdated], уведомляя наблюдателей
     * о необходимости перерисовки поля.
     */
    private fun bumpBoardUpdate() {
        _boardUpdated.value = (_boardUpdated.value ?: 0) + 1
    }

    /**
     * Полностью сбрасывает поле текущего игрока и его прогресс расстановки.
     *
     * Восстанавливает полный пул кораблей, сбрасывает ориентацию,
     * выбор, флаги перехода и завершения.
     */
    fun resetCurrentBoard() {
        getCurrentBoard().clear()
        _unplacedShips.value = Board.REQUIRED_SHIPS.toList()
        _selectedShipSize.value = Board.REQUIRED_SHIPS.firstOrNull()
        _currentOrientation.value = Orientation.HORIZONTAL
        _setupTransitionPlayerIndex.value = null
        _setupComplete.value = false
        _startingPlayerIndex.value = null
        bumpBoardUpdate()
    }

    /**
     * Создаёт массив [Player] из двух элементов для передачи в [GameViewModel.initGame].
     *
     * Каждый игрок получает имя из [SavedStateHandle] и поле с уже расставленными
     * кораблями. Если имя пустое — подставляется значение по умолчанию.
     *
     * @param defaultPlayer1Name Имя по умолчанию для первого игрока.
     * @param defaultPlayer2Name Имя по умолчанию для второго игрока.
     * @return Массив из двух [Player], готовых к началу матча.
     */
    fun createPlayers(defaultPlayer1Name: String, defaultPlayer2Name: String): Array<Player> {
        val name1 = player1Name.value?.ifBlank { defaultPlayer1Name } ?: defaultPlayer1Name
        val name2 = player2Name.value?.ifBlank { defaultPlayer2Name } ?: defaultPlayer2Name

        return arrayOf(
            Player(name1, boards[0]),
            Player(name2, boards[1])
        )
    }
}
