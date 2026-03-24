package com.navalcombat.model

/**
 * Игровое поле одного игрока размером [SIZE]×[SIZE].
 *
 * Инкапсулирует логику размещения кораблей, валидации правил расстановки
 * и обработки выстрелов. Содержит внутренние коллекции для хранения
 * размещённых кораблей, промахов, попаданий и общей истории выстрелов.
 *
 * Основные правила, реализованные в классе:
 * - Корабли не могут выходить за границы поля.
 * - Корабли не могут пересекаться друг с другом.
 * - Корабли не могут касаться друг друга сторонами или углами.
 * - При потоплении корабля клетки вокруг него автоматически помечаются промахами.
 *
 * @see Ship
 * @see ShotResult
 */
class Board {

    /**
     * Список кораблей, успешно размещённых на поле.
     *
     * Добавление происходит через [placeShip], удаление — через
     * [removeLastShip], [removeShipAt] или полный сброс [clear].
     */
    private val ships: MutableList<Ship> = mutableListOf()

    /**
     * Клетки, по которым был произведён выстрел и зафиксирован промах.
     *
     * Обновляется в [processShot] и [markSurroundingAsMiss].
     */
    private val misses: MutableSet<Cell> = mutableSetOf()

    /**
     * Клетки, по которым был произведён выстрел и зафиксировано попадание.
     *
     * Обновляется в [processShot] при обнаружении корабля в целевой клетке.
     */
    private val hits: MutableSet<Cell> = mutableSetOf()

    /**
     * Полная история всех клеток, по которым стреляли (включая промахи и попадания).
     *
     * Используется для быстрой проверки повторных выстрелов в [isAlreadyShot] и [processShot].
     */
    private val shotsFired: MutableSet<Cell> = mutableSetOf()

    companion object {
        /** Размер стороны поля. Совпадает с [Cell.BOARD_SIZE] (10). */
        const val SIZE = Cell.BOARD_SIZE

        /**
         * Стандартный набор кораблей, которые необходимо расставить.
         *
         * По правилам классического «Морского боя»:
         * - 1 четырёхпалубный,
         * - 2 трёхпалубных,
         * - 3 двухпалубных,
         * - 4 однопалубных.
         *
         * Список упорядочен по убыванию размера для удобства UI-пула.
         */
        val REQUIRED_SHIPS = listOf(4, 3, 3, 2, 2, 2, 1, 1, 1, 1)
    }

    /**
     * Возвращает неизменяемую копию списка размещённых кораблей.
     *
     * @return Список [Ship], находящихся на поле в порядке размещения.
     */
    fun getShips(): List<Ship> = ships.toList()

    /**
     * Возвращает неизменяемую копию множества клеток-промахов.
     *
     * @return Множество [Cell], в которых был зарегистрирован промах.
     */
    fun getMisses(): Set<Cell> = misses.toSet()

    /**
     * Возвращает неизменяемую копию множества клеток-попаданий.
     *
     * @return Множество [Cell], в которых было зарегистрировано попадание.
     */
    fun getHits(): Set<Cell> = hits.toSet()

    /**
     * Проверяет, производился ли ранее выстрел по указанной клетке.
     *
     * @param cell Проверяемая клетка.
     * @return `true`, если клетка уже содержится в истории выстрелов.
     */
    fun isAlreadyShot(cell: Cell): Boolean = cell in shotsFired

    /**
     * Пытается разместить корабль на поле с учётом всех правил.
     *
     * Проверяет, что:
     * 1. Все палубы корабля попадают в границы поля ([Cell.isValid]).
     * 2. Ни одна палуба не пересекается с уже занятыми клетками.
     * 3. Ни одна палуба не попадает в «запретную зону» вокруг существующих кораблей.
     *
     * При успешном прохождении всех проверок корабль добавляется в [ships].
     *
     * @param ship Корабль для размещения.
     * @return `true`, если корабль был успешно размещён; `false` — при нарушении правил.
     *
     * @see canPlaceShip
     */
    fun placeShip(ship: Ship): Boolean {
        val cells = ship.getCells()

        if (!cells.all { it.isValid() }) return false

        val occupiedCells = getOccupiedCells()
        val forbiddenCells = getForbiddenCells()

        if (cells.any { it in occupiedCells }) return false

        if (cells.any { it in forbiddenCells }) return false

        ships.add(ship)
        return true
    }

    /**
     * Удаляет последний размещённый корабль с поля.
     *
     * Используется для реализации функции «Отмена» на экране расстановки.
     *
     * @return Удалённый [Ship], или `null`, если поле пустое.
     */
    fun removeLastShip(): Ship? {
        return if (ships.isNotEmpty()) ships.removeAt(ships.lastIndex) else null
    }

    /**
     * Удаляет корабль, одна из палуб которого занимает указанную клетку.
     *
     * Используется при удалении корабля длительным нажатием на экране расстановки.
     *
     * @param cell Клетка, принадлежащая целевому кораблю.
     * @return Удалённый [Ship], или `null`, если в клетке нет корабля.
     */
    fun removeShipAt(cell: Cell): Ship? {
        val ship = getShipAt(cell)
        if (ship != null) {
            ships.remove(ship)
        }
        return ship
    }

    /**
     * Ищет корабль, одна из палуб которого занимает указанную клетку.
     *
     * @param cell Клетка для поиска.
     * @return Найденный [Ship], или `null`, если клетка свободна.
     */
    fun getShipAt(cell: Cell): Ship? {
        return ships.find { cell in it.getCells() }
    }

    /**
     * Обрабатывает выстрел по указанной клетке.
     *
     * Логика обработки:
     * 1. Если клетка вне поля — промах.
     * 2. Если по клетке уже стреляли — возвращается [ShotResult.ALREADY_SHOT].
     * 3. Если в клетке обнаружен корабль — фиксируется попадание; при потоплении
     *    корабля окружающие клетки автоматически помечаются промахами.
     * 4. Иначе — фиксируется промах.
     *
     * @param cell Целевая клетка выстрела.
     * @return Результат выстрела.
     *
     * @see ShotResult
     * @see markSurroundingAsMiss
     */
    fun processShot(cell: Cell): ShotResult {
        if (!cell.isValid()) return ShotResult.MISS
        if (cell in shotsFired) return ShotResult.ALREADY_SHOT

        shotsFired.add(cell)

        val hitShip = ships.find { cell in it.getCells() }
        return if (hitShip != null) {
            hitShip.hit(cell)
            hits.add(cell)
            if (hitShip.isSunk()) {
                markSurroundingAsMiss(hitShip)
                ShotResult.SUNK
            } else {
                ShotResult.HIT
            }
        } else {
            misses.add(cell)
            ShotResult.MISS
        }
    }

    /**
     * Проверяет, все ли корабли на поле потоплены.
     *
     * Условие: поле содержит хотя бы один корабль, и каждый из них [Ship.isSunk].
     *
     * @return `true`, если флот полностью уничтожен.
     */
    fun allShipsSunk(): Boolean = ships.isNotEmpty() && ships.all { it.isSunk() }

    /**
     * Проверяет, можно ли разместить корабль, **не изменяя** состояние поля.
     *
     * Выполняет те же проверки, что и [placeShip], но без побочных эффектов.
     * Используется для визуальной подсветки допустимости размещения в UI.
     *
     * @param ship Проверяемый корабль.
     * @return `true`, если размещение допустимо.
     */
    fun canPlaceShip(ship: Ship): Boolean {
        val cells = ship.getCells()
        if (!cells.all { it.isValid() }) return false

        val occupiedCells = getOccupiedCells()
        val forbiddenCells = getForbiddenCells()

        if (cells.any { it in occupiedCells }) return false
        if (cells.any { it in forbiddenCells }) return false

        return true
    }

    /**
     * Определяет видимое состояние клетки для поля **соперника**.
     *
     * На поле соперника корабли не видны — отображаются только
     * результаты выстрелов.
     *
     * @param cell Запрашиваемая клетка.
     * @return [CellState.HIT], [CellState.MISS] или [CellState.UNKNOWN].
     */
    fun getCellStateForOpponent(cell: Cell): CellState {
        return when {
            cell in hits -> CellState.HIT
            cell in misses -> CellState.MISS
            else -> CellState.UNKNOWN
        }
    }

    /**
     * Проверяет, занята ли указанная клетка каким-либо кораблём.
     *
     * @param cell Проверяемая клетка.
     * @return `true`, если хотя бы одна палуба любого корабля совпадает с [cell].
     */
    fun hasShipAt(cell: Cell): Boolean = ships.any { cell in it.getCells() }

    /**
     * Собирает множество всех клеток, занятых палубами кораблей.
     *
     * @return [Set] занятых [Cell].
     */
    private fun getOccupiedCells(): Set<Cell> = ships.flatMap { it.getCells() }.toSet()

    /**
     * Вычисляет «запретную зону» — клетки, смежные с существующими кораблями
     * (по стороне или диагонали), в которые нельзя ставить новый корабль.
     *
     * Запретная зона не включает сами занятые клетки —
     * они обрабатываются отдельно через [getOccupiedCells].
     *
     * @return [Set] клеток, запрещённых для размещения.
     */
    private fun getForbiddenCells(): Set<Cell> {
        val forbidden = mutableSetOf<Cell>()
        for (ship in ships) {
            for (cell in ship.getCells()) {
                for (dr in -1..1) {
                    for (dc in -1..1) {
                        if (dr == 0 && dc == 0) continue
                        val neighbor = Cell(cell.row + dr, cell.col + dc)
                        if (neighbor.isValid() && neighbor !in getOccupiedCells()) {
                            forbidden.add(neighbor)
                        }
                    }
                }
            }
        }
        return forbidden
    }

    /**
     * Помечает клетки вокруг потопленного корабля как промахи.
     *
     * Автоматически вызывается в [processShot] при потоплении.
     * Предотвращает бессмысленные выстрелы по клеткам,
     * которые гарантированно пусты по правилам расстановки.
     *
     * @param ship Потопленный корабль, вокруг которого выполняется пометка.
     */
    private fun markSurroundingAsMiss(ship: Ship) {
        for (cell in ship.getCells()) {
            for (dr in -1..1) {
                for (dc in -1..1) {
                    if (dr == 0 && dc == 0) continue
                    val neighbor = Cell(cell.row + dr, cell.col + dc)
                    if (neighbor.isValid() && neighbor !in shotsFired &&
                        !hasShipAt(neighbor)
                    ) {
                        shotsFired.add(neighbor)
                        misses.add(neighbor)
                    }
                }
            }
        }
    }

    /**
     * Полностью сбрасывает поле: удаляет все корабли и очищает историю выстрелов.
     *
     * Используется при сбросе расстановки текущего игрока
     * и при начале новой партии.
     */
    fun clear() {
        ships.clear()
        misses.clear()
        hits.clear()
        shotsFired.clear()
    }
}

/**
 * Результат обработки выстрела по клетке поля.
 *
 * Возвращается методом [Board.processShot] и используется в
 * [GameViewModel][com.navalcombat.viewmodel.GameViewModel] для определения
 * дальнейшего хода игры и отображения результата в UI.
 *
 * @see Board.processShot
 */
enum class ShotResult {
    /** Выстрел в пустую клетку — ход переходит к сопернику. */
    MISS,

    /** Попадание в палубу корабля, который ещё не потоплен — ход продолжается. */
    HIT,

    /** Попадание, приведшее к потоплению корабля — ход продолжается. */
    SUNK,

    /** Повторный выстрел по уже атакованной клетке — ход не расходуется. */
    ALREADY_SHOT
}
