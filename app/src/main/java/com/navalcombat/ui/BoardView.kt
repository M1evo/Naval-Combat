package com.navalcombat.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.navalcombat.R
import com.navalcombat.model.Board
import com.navalcombat.model.Cell
import com.navalcombat.model.CellState

/**
 * Кастомный [View] для отрисовки игрового поля «Морского боя».
 *
 * Отвечает исключительно за визуализацию: рисует сетку 10×10, подписи строк и столбцов,
 * состояние клеток (вода, корабль, попадание, промах, потопление) и обрабатывает
 * пользовательские касания, транслируя их в координаты клеток.
 *
 * Игровая логика **не хранится** внутри view: все данные поступают через [setBoard],
 * а события касаний передаются наружу через коллбэки [onCellClickListener] и
 * [onCellLongClickListener].
 *
 * ### Режимы отображения ([DisplayMode])
 *
 * | Режим              | Корабли видны | Результаты выстрелов | Используется |
 * |---------------------|:---:|:---:|-----|
 * | [DisplayMode.OWN_BOARD]      | ✔ | ✔ | Нижняя панель [GameFragment][com.navalcombat.ui.GameFragment] |
 * | [DisplayMode.OPPONENT_BOARD] | ✘ | ✔ | Верхняя панель [GameFragment][com.navalcombat.ui.GameFragment] |
 * | [DisplayMode.SETUP]          | ✔ | ✘ | Экран расстановки [SetupFragment][com.navalcombat.ui.SetupFragment] |
 *
 * @param context      Контекст Android.
 * @param attrs        XML-атрибуты, переданные из layout-файла.
 * @param defStyleAttr Атрибут стиля по умолчанию.
 *
 * @see Board
 * @see DisplayMode
 */
class BoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * Коллбэк одиночного нажатия по клетке поля.
     *
     * Вызывается после подтверждённого tap-жеста (не long-press).
     * Параметры — индексы строки и столбца (0-based).
     */
    var onCellClickListener: ((row: Int, col: Int) -> Unit)? = null

    /**
     * Текущий режим отрисовки поля.
     *
     * При изменении автоматически вызывает [invalidate] для перерисовки.
     */
    var displayMode: DisplayMode = DisplayMode.OWN_BOARD
        set(value) {
            field = value
            invalidate()
        }

    /** Ссылка на [Board], данные которого отрисовываются. */
    private var board: Board? = null

    /** Размер одной клетки в пикселях (рассчитывается в [onDraw]). */
    private var cellSize = 0f

    /** Горизонтальное смещение сетки от левого края view (с учётом подписей). */
    private var offsetX = 0f

    /** Вертикальное смещение сетки от верхнего края view (с учётом подписей). */
    private var offsetY = 0f

    /** Ширина/высота области, отведённой под подписи строк и столбцов. */
    private var labelAreaSize = 0f

    /** Зазор между подписью и краем сетки. */
    private var labelGap = 0f

    /** Кисть для линий сетки (тёмно-серый, обводка). */
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#455A64")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    /** Кисть для подписей столбцов (тёмный, центрированный текст). */
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#263238")
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }

    /** Кисть для подписей строк (тёмный, правоцентрированный текст). */
    private val rowLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#263238")
        textSize = 32f
        textAlign = Paint.Align.RIGHT
    }

    /** Кисть заливки «воды» (светло-голубой фон клетки). */
    private val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E3F2FD")
        style = Paint.Style.FILL
    }

    /** Кисть заливки палубы корабля (серо-синий). */
    private val shipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#546E7A")
        style = Paint.Style.FILL
    }

    /** Кисть заливки клетки с попаданием (красный). */
    private val hitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53935")
        style = Paint.Style.FILL
    }

    /** Кисть крестика-маркера попадания (белая обводка). */
    private val hitMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    /** Кисть точки-маркера промаха (тёмно-серый, заливка). */
    private val missMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#455A64")
        style = Paint.Style.FILL
    }

    /** Кисть заливки палубы потопленного корабля (тёмно-красный). */
    private val sunkShipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B71C1C")
        style = Paint.Style.FILL
    }

    /**
     * Устанавливает модель поля и запрашивает перерисовку.
     *
     * Должен вызываться из фрагмента каждый раз, когда данные [Board] изменяются
     * (выстрел, размещение/удаление корабля).
     *
     * @param newBoard Актуальное состояние поля.
     */
    fun setBoard(newBoard: Board) {
        board = newBoard
        invalidate()
    }

    /**
     * Делает view квадратным: выбирает меньшую из предложенных сторон.
     *
     * Это гарантирует, что клетки всегда будут квадратными и поле
     * не исказится при разных соотношениях сторон экрана.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val size = minOf(width, height)
        setMeasuredDimension(size, size)
    }

    /**
     * Перерисовывает всё содержимое view: подписи, клетки и сетку.
     *
     * Размеры ([cellSize], [offsetX]/[offsetY], [labelAreaSize]) пересчитываются
     * на каждый вызов, чтобы корректно реагировать на изменение размеров view.
     *
     * Порядок отрисовки:
     * 1. Подписи строк и столбцов ([drawLabels]).
     * 2. Содержимое клеток ([drawCells]) — зависит от [displayMode].
     * 3. Линии сетки ([drawGrid]) — рисуются поверх содержимого.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val totalSize = minOf(width, height).toFloat()
        val desiredLabelArea = maxOf(48f, totalSize * 0.12f)
        labelAreaSize = minOf(desiredLabelArea, totalSize * 0.22f)
        cellSize = (totalSize - (labelAreaSize * 2f)) / Board.SIZE
        offsetX = labelAreaSize
        offsetY = labelAreaSize
        labelGap = cellSize * 0.24f

        labelPaint.textSize = cellSize * 0.4f
        rowLabelPaint.textSize = labelPaint.textSize

        drawLabels(canvas)
        drawCells(canvas)
        drawGrid(canvas)
    }

    /**
     * Рисует подписи столбцов (А–К / A–J) над сеткой
     * и подписи строк (1–10) слева от сетки.
     *
     * Подписи столбцов получаются из строковых ресурсов через [getColumnLabels].
     */
    private fun drawLabels(canvas: Canvas) {
        val columnLabels = getColumnLabels()

        for (col in 0 until Board.SIZE) {
            val x = offsetX + col * cellSize + cellSize / 2
            val y = offsetY - labelGap
            canvas.drawText(columnLabels[col], x, y, labelPaint)
        }

        for (row in 0 until Board.SIZE) {
            val x = offsetX - labelGap
            val y = offsetY + row * cellSize + cellSize / 2 + rowLabelPaint.textSize / 3
            canvas.drawText((row + 1).toString(), x, y, rowLabelPaint)
        }
    }

    /**
     * Возвращает локализованные подписи столбцов из ресурса `board_column_labels`.
     *
     * Если ресурс содержит некорректное количество элементов (≠ [Board.SIZE]),
     * используется fallback-набор латинских букв `A`–`J`.
     *
     * @return Список строк длиной [Board.SIZE].
     */
    private fun getColumnLabels(): List<String> {
        val labels = resources.getStringArray(R.array.board_column_labels)
        if (labels.size == Board.SIZE) return labels.toList()
        return List(Board.SIZE) { index -> ('A' + index).toString() }
    }

    /**
     * Рисует содержимое всех клеток поля в зависимости от текущего [displayMode].
     *
     * Каждая клетка сначала заполняется цветом воды, а затем поверх
     * наносятся элементы, соответствующие состоянию:
     *
     * - **OWN_BOARD**: палубы кораблей (серые) + крестики попаданий + точки промахов.
     * - **OPPONENT_BOARD**: только крестики и точки; потопленные корабли выделены
     *   тёмно-красным фоном.
     * - **SETUP**: только палубы кораблей для визуального контроля расстановки.
     */
    private fun drawCells(canvas: Canvas) {
        val b = board ?: return

        for (row in 0 until Board.SIZE) {
            for (col in 0 until Board.SIZE) {
                val rect = getCellRect(row, col)
                val cell = Cell(row, col)

                canvas.drawRect(rect, waterPaint)

                when (displayMode) {
                    DisplayMode.OWN_BOARD -> {
                        if (b.hasShipAt(cell)) {
                            val state = b.getCellStateForOpponent(cell)
                            if (state == CellState.HIT) {
                                canvas.drawRect(rect, hitPaint)
                                drawCross(canvas, rect)
                            } else {
                                canvas.drawRect(rect, shipPaint)
                            }
                        } else {
                            val state = b.getCellStateForOpponent(cell)
                            if (state == CellState.MISS) {
                                drawDot(canvas, rect)
                            }
                        }
                    }
                    DisplayMode.OPPONENT_BOARD -> {
                        when (b.getCellStateForOpponent(cell)) {
                            CellState.HIT -> {
                                val ship = b.getShips().find { cell in it.getCells() }
                                if (ship != null && ship.isSunk()) {
                                    canvas.drawRect(rect, sunkShipPaint)
                                } else {
                                    canvas.drawRect(rect, hitPaint)
                                }
                                drawCross(canvas, rect)
                            }
                            CellState.MISS -> {
                                drawDot(canvas, rect)
                            }
                            CellState.UNKNOWN -> Unit
                        }
                    }
                    DisplayMode.SETUP -> {
                        if (b.hasShipAt(cell)) {
                            canvas.drawRect(rect, shipPaint)
                        }
                    }
                }
            }
        }
    }

    /**
     * Рисует вертикальные и горизонтальные линии сетки поля
     * поверх содержимого клеток.
     *
     * Всего рисуется `Board.SIZE + 1` линий в каждом направлении,
     * образующих [Board.SIZE]² ячеек.
     */
    private fun drawGrid(canvas: Canvas) {
        for (i in 0..Board.SIZE) {
            val x = offsetX + i * cellSize
            canvas.drawLine(x, offsetY, x, offsetY + Board.SIZE * cellSize, gridPaint)

            val y = offsetY + i * cellSize
            canvas.drawLine(offsetX, y, offsetX + Board.SIZE * cellSize, y, gridPaint)
        }
    }

    /**
     * Рисует крестик (×) внутри прямоугольника клетки — маркер попадания.
     *
     * Отступ от краёв составляет 20% от размера клетки, чтобы крестик
     * не сливался с линиями сетки.
     *
     * @param canvas Холст для отрисовки.
     * @param rect   Прямоугольник целевой клетки.
     */
    private fun drawCross(canvas: Canvas, rect: RectF) {
        val padding = cellSize * 0.2f
        canvas.drawLine(
            rect.left + padding, rect.top + padding,
            rect.right - padding, rect.bottom - padding,
            hitMarkerPaint
        )
        canvas.drawLine(
            rect.right - padding, rect.top + padding,
            rect.left + padding, rect.bottom - padding,
            hitMarkerPaint
        )
    }

    /**
     * Рисует маленький кружок в центре клетки — маркер промаха.
     *
     * Радиус составляет 12% от размера клетки.
     *
     * @param canvas Холст для отрисовки.
     * @param rect   Прямоугольник целевой клетки.
     */
    private fun drawDot(canvas: Canvas, rect: RectF) {
        val radius = cellSize * 0.12f
        canvas.drawCircle(rect.centerX(), rect.centerY(), radius, missMarkerPaint)
    }

    /**
     * Вычисляет [RectF] прямоугольника указанной клетки на холсте.
     *
     * @param row Индекс строки (0-based).
     * @param col Индекс столбца (0-based).
     * @return Прямоугольник клетки в системе координат view.
     */
    private fun getCellRect(row: Int, col: Int): RectF {
        val left = offsetX + col * cellSize
        val top = offsetY + row * cellSize
        return RectF(left, top, left + cellSize, top + cellSize)
    }

    /**
     * Коллбэк длительного нажатия по клетке поля.
     *
     * Активен только в режиме [DisplayMode.SETUP] — используется
     * для удаления ранее размещённого корабля.
     */
    var onCellLongClickListener: ((row: Int, col: Int) -> Unit)? = null

    /**
     * Определяет клетку поля по координатам касания в пикселях.
     *
     * Учитывает текущие смещения [offsetX]/[offsetY] и размер [cellSize].
     *
     * @param x Горизонтальная координата касания (px).
     * @param y Вертикальная координата касания (px).
     * @return [Cell], если касание попало в область сетки; `null` — иначе.
     */
    fun getCellAt(x: Float, y: Float): Cell? {
        val cx = x - offsetX
        val cy = y - offsetY
        if (cx >= 0 && cy >= 0) {
            val col = (cx / cellSize).toInt()
            val row = (cy / cellSize).toInt()
            if (row in 0 until Board.SIZE && col in 0 until Board.SIZE) {
                return Cell(row, col)
            }
        }
        return null
    }

    /**
     * Распознаватель жестов: одиночное нажатие и длительное нажатие.
     *
     * - **onSingleTapConfirmed** — срабатывает в режимах [DisplayMode.OPPONENT_BOARD]
     *   и [DisplayMode.SETUP]; транслирует координаты касания в клетку и вызывает
     *   [onCellClickListener].
     * - **onLongPress** — срабатывает только в [DisplayMode.SETUP];
     *   вызывает [onCellLongClickListener] для удаления корабля.
     * - **onDown** возвращает `true`, чтобы view получала последующие события жеста.
     */
    @Suppress("ClickableViewAccessibility")
    private val gestureDetector = android.view.GestureDetector(
        context,
        object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (displayMode != DisplayMode.OPPONENT_BOARD && displayMode != DisplayMode.SETUP) {
                    return super.onSingleTapConfirmed(e)
                }
                val cell = getCellAt(e.x, e.y)
                if (cell != null) {
                    onCellClickListener?.invoke(cell.row, cell.col)
                    return true
                }
                return super.onSingleTapConfirmed(e)
            }

            override fun onLongPress(e: MotionEvent) {
                if (displayMode == DisplayMode.SETUP) {
                    val cell = getCellAt(e.x, e.y)
                    if (cell != null) onCellLongClickListener?.invoke(cell.row, cell.col)
                }
            }

            override fun onDown(e: MotionEvent): Boolean = true
        }
    )

    /**
     * Делегирует все события касания внутреннему [gestureDetector].
     *
     * Если gesture detector не обработал событие, вызывается реализация [View].
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    /**
     * Переопределение необходимо для корректной работы accessibility-сервисов
     * при программных «кликах» по view.
     */
    override fun performClick(): Boolean {
        return super.performClick()
    }

    /**
     * Режим отображения данных [Board] в [BoardView].
     *
     * Определяет, какую визуальную информацию получает игрок:
     * только результаты выстрелов, расстановку кораблей или всё вместе.
     */
    enum class DisplayMode {
        /** Своё поле: корабли видны, результаты выстрелов показаны. */
        OWN_BOARD,

        /** Поле противника: корабли скрыты, видны только результаты выстрелов. */
        OPPONENT_BOARD,

        /** Режим расстановки: корабли видны, результаты выстрелов не показаны. */
        SETUP
    }
}
