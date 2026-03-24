package com.navalcombat.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.navalcombat.model.*

/**
 * ViewModel основного игрового экрана.
 *
 * Управляет жизненным циклом матча: инициализация →
 * стрельба → смена хода → определение победителя → сброс.
 *
 * ### Ответственность
 * - Хранить массив [Player] и индекс текущего стрелка.
 * - Обрабатывать выстрелы через [fireShot] и обновлять [gameState].
 * - Управлять промежуточным экраном передачи хода ([confirmTurnTransition]).
 * - Определять победителя ([getWinnerIndex] / [getWinnerName]).
 * - Сбрасывать всё состояние при начале новой партии ([resetGame]).
 *
 * ### Связь с UI
 * Фрагменты наблюдают за [gameState], [lastShotResult] и [boardUpdated],
 * реагируя на изменения перерисовкой полей и текстов.
 *
 * @see GameState
 * @see com.navalcombat.ui.GameFragment
 */
class GameViewModel : ViewModel() {

    /**
     * Массив игроков текущей партии.
     *
     * Заполняется в [initGame]; содержит ровно 2 элемента.
     * Пустой массив означает, что матч ещё не инициализирован.
     */
    private var players: Array<Player> = emptyArray()

    /**
     * Текущее состояние матча.
     *
     * Начальное значение — [GameState.Setup]; переходы описаны в [GameState].
     */
    private val _gameState = MutableLiveData<GameState>(GameState.Setup(0))

    /** Публичный неизменяемый доступ к [_gameState]. */
    val gameState: LiveData<GameState> = _gameState

    /**
     * Индекс игрока, который сейчас совершает ход.
     *
     * `0` — первый игрок, `1` — второй. Обновляется при смене хода.
     */
    private val _currentPlayerIndex = MutableLiveData(0)

    /** Публичный неизменяемый доступ к [_currentPlayerIndex]. */
    val currentPlayerIndex: LiveData<Int> = _currentPlayerIndex

    /**
     * Результат последнего выстрела.
     *
     * `null` — если выстрел ещё не был совершён или ход только что начался.
     * Используется для отображения текста «Попал!» / «Мимо!» / «Потоплен!»
     * на экране [GameFragment][com.navalcombat.ui.GameFragment].
     */
    private val _lastShotResult = MutableLiveData<ShotResult?>()

    /** Публичный неизменяемый доступ к [_lastShotResult]. */
    val lastShotResult: LiveData<ShotResult?> = _lastShotResult

    /**
     * Счётчик обновления полей.
     *
     * Инкрементируется при любом изменении состояния доски.
     * Наблюдатели используют его как триггер для перерисовки [BoardView][com.navalcombat.ui.BoardView].
     */
    private val _boardUpdated = MutableLiveData(0)

    /** Публичный неизменяемый доступ к [_boardUpdated]. */
    val boardUpdated: LiveData<Int> = _boardUpdated

    /**
     * Флаг инициализации матча.
     *
     * Защищает от повторной инициализации при пересоздании фрагмента
     * (например, при смене ориентации экрана).
     */
    private var isInitialized = false

    /**
     * Инициализирует матч.
     *
     * Вызывается из [GameFragment.onViewCreated][com.navalcombat.ui.GameFragment.onViewCreated],
     * если матч ещё не был инициализирован. Повторный вызов без [forceReset]
     * и с теми же именами игнорируется.
     *
     * @param gamePlayers         Массив из двух [Player] с уже расставленными кораблями.
     * @param forceReset          Если `true`, матч будет инициализирован заново в любом случае.
     * @param startingPlayerIndex Индекс игрока, который делает первый ход (0 или 1).
     */
    fun initGame(
        gamePlayers: Array<Player>,
        forceReset: Boolean = false,
        startingPlayerIndex: Int = 0
    ) {
        if (gamePlayers.size < 2) return

        val shouldReinitialize = forceReset ||
            !isInitialized ||
            players.size != gamePlayers.size ||
            players.indices.any { players[it].name != gamePlayers[it].name }

        if (!shouldReinitialize) return

        val safeStartingIndex = startingPlayerIndex.coerceIn(0, 1)
        players = gamePlayers
        _currentPlayerIndex.value = safeStartingIndex
        _gameState.value = GameState.Playing(safeStartingIndex)
        _lastShotResult.value = null
        _boardUpdated.value = (_boardUpdated.value ?: 0) + 1
        isInitialized = true
    }

    /**
     * Проверяет, инициализирован ли матч.
     *
     * @return `true`, если [initGame] был успешно вызван.
     */
    fun isGameInitialized(): Boolean = isInitialized

    /**
     * Возвращает имя игрока по индексу.
     *
     * @param index Индекс игрока (0 или 1).
     * @return Имя игрока, или пустая строка, если индекс некорректен.
     */
    fun getPlayerName(index: Int): String = players.getOrNull(index)?.name.orEmpty()

    /**
     * Возвращает имя игрока, который сейчас совершает ход.
     *
     * @return Имя текущего игрока.
     */
    fun getCurrentPlayerName(): String {
        val currentIndex = _currentPlayerIndex.value ?: 0
        return players.getOrNull(currentIndex)?.name.orEmpty()
    }

    /**
     * Возвращает имя соперника текущего игрока.
     *
     * @return Имя соперника.
     */
    fun getOpponentName(): String {
        val opponentIndex = 1 - (_currentPlayerIndex.value ?: 0)
        return players.getOrNull(opponentIndex)?.name.orEmpty()
    }

    /**
     * Возвращает поле текущего игрока (для отображения своих кораблей).
     *
     * @return [Board] текущего игрока, или пустой [Board], если данные недоступны.
     */
    fun getCurrentPlayerBoard(): Board {
        val currentIndex = _currentPlayerIndex.value ?: 0
        return players.getOrNull(currentIndex)?.board ?: Board()
    }

    /**
     * Возвращает поле соперника (для отображения результатов выстрелов).
     *
     * @return [Board] соперника, или пустой [Board], если данные недоступны.
     */
    fun getOpponentBoard(): Board {
        val opponentIndex = 1 - (_currentPlayerIndex.value ?: 0)
        return players.getOrNull(opponentIndex)?.board ?: Board()
    }

    /**
     * Обрабатывает выстрел текущего игрока по полю соперника.
     *
     * ### Логика переходов после выстрела
     * - [ShotResult.MISS] — ход переходит к сопернику через [GameState.TurnTransition].
     * - [ShotResult.HIT] — ход остаётся у текущего игрока.
     * - [ShotResult.SUNK] — если все корабли потоплены, матч завершается
     *   ([GameState.GameOver]); иначе — ход остаётся.
     * - [ShotResult.ALREADY_SHOT] — никаких изменений состояния.
     *
     * @param row Индекс строки целевой клетки (0-based).
     * @param col Индекс столбца целевой клетки (0-based).
     * @return Результат выстрела, или `null`, если матч не инициализирован
     *         или текущее состояние не [GameState.Playing].
     */
    fun fireShot(row: Int, col: Int): ShotResult? {
        if (!isInitialized || players.size < 2) return null

        val state = _gameState.value
        if (state !is GameState.Playing) return null

        val cell = Cell(row, col)
        val opponentBoard = getOpponentBoard()

        val result = opponentBoard.processShot(cell)
        _lastShotResult.value = result
        _boardUpdated.value = (_boardUpdated.value ?: 0) + 1

        when (result) {
            ShotResult.ALREADY_SHOT -> Unit
            ShotResult.MISS -> {
                val currentIndex = _currentPlayerIndex.value ?: 0
                val nextIndex = 1 - currentIndex
                _gameState.value = GameState.TurnTransition(nextIndex)
            }
            ShotResult.HIT -> Unit
            ShotResult.SUNK -> {
                if (opponentBoard.allShipsSunk()) {
                    val winnerIndex = _currentPlayerIndex.value ?: 0
                    _gameState.value = GameState.GameOver(winnerIndex)
                }
            }
        }

        return result
    }

    /**
     * Подтверждает передачу хода следующему игроку.
     *
     * Переводит матч из [GameState.TurnTransition] обратно в [GameState.Playing]
     * со сменой текущего игрока. Сбрасывает результат последнего выстрела.
     *
     * Вызывается из [TurnTransitionFragment][com.navalcombat.ui.TurnTransitionFragment]
     * при нажатии кнопки «Продолжить».
     */
    fun confirmTurnTransition() {
        val state = _gameState.value
        if (state is GameState.TurnTransition) {
            _currentPlayerIndex.value = state.nextPlayerIndex
            _gameState.value = GameState.Playing(state.nextPlayerIndex)
            _lastShotResult.value = null
            _boardUpdated.value = (_boardUpdated.value ?: 0) + 1
        }
    }

    /**
     * Возвращает индекс победителя.
     *
     * @return Индекс (0 или 1) при состоянии [GameState.GameOver]; `-1` — иначе.
     */
    fun getWinnerIndex(): Int {
        val state = _gameState.value
        return if (state is GameState.GameOver) state.winnerIndex else -1
    }

    /**
     * Возвращает имя победителя.
     *
     * @return Имя победившего игрока, или пустая строка, если матч не завершён.
     */
    fun getWinnerName(): String {
        val index = getWinnerIndex()
        return if (index >= 0) players.getOrNull(index)?.name.orEmpty() else ""
    }

    /**
     * Полностью сбрасывает состояние матча.
     *
     * Очищает массив игроков, сбрасывает все LiveData в начальные значения.
     * Должен вызываться перед началом новой партии из [WelcomeFragment][com.navalcombat.ui.WelcomeFragment]
     * или [GameOverFragment][com.navalcombat.ui.GameOverFragment].
     */
    fun resetGame() {
        players = emptyArray()
        isInitialized = false
        _currentPlayerIndex.value = 0
        _lastShotResult.value = null
        _gameState.value = GameState.Setup(0)
        _boardUpdated.value = (_boardUpdated.value ?: 0) + 1
    }
}
