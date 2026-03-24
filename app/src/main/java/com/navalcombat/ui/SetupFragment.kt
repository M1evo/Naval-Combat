package com.navalcombat.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.navalcombat.R
import com.navalcombat.databinding.FragmentSetupBinding
import com.navalcombat.model.Board
import com.navalcombat.model.Orientation
import com.navalcombat.model.Orientation.HORIZONTAL
import com.navalcombat.model.Orientation.VERTICAL
import com.navalcombat.viewmodel.SetupViewModel

/**
 * Экран расстановки кораблей.
 *
 * Каждый игрок поочерёдно размещает свой флот ([Board.REQUIRED_SHIPS])
 * на персональном поле. Между расстановками двух игроков отображается
 * промежуточный экран ([TurnTransitionFragment]).
 *
 * ### Функциональность
 * - Панель выбора корабля: кнопки с оставшимися размерами, сгруппированные по типу.
 * - Управление ориентацией размещения (горизонтальная/вертикальная).
 * - Одиночное нажатие по клетке — размещение выбранного корабля.
 * - Длительное нажатие по кораблю — удаление корабля и возврат в пул.
 * - Кнопка «Отмена» — удаление последнего размещённого корабля.
 * - Кнопка «Сброс» — полная очистка поля текущего игрока.
 * - Кнопка переключения языка приложения.
 *
 * ### Навигация
 * После завершения расстановки обоими игроками показывается диалог
 * выбора стартового игрока, а затем происходит переход к [GameFragment].
 *
 * @see SetupViewModel
 * @see BoardView
 */
class SetupFragment : Fragment() {

    /** Binding-обёртка для `fragment_setup.xml`. Обнуляется в [onDestroyView]. */
    private var _binding: FragmentSetupBinding? = null

    /** Non-null accessor для [_binding]; безопасен между [onCreateView] и [onDestroyView]. */
    private val binding get() = _binding!!

    /** Shared ViewModel, управляющий состоянием расстановки. */
    private val setupViewModel: SetupViewModel by activityViewModels()

    /**
     * Локальная копия списка неразмещённых кораблей.
     *
     * Хранится для передачи в [updateSelectedShipLabel], который
     * вызывается из нескольких наблюдателей.
     */
    private var latestUnplacedShips: List<Int> = Board.REQUIRED_SHIPS.toList()

    /**
     * Создаёт корневой [View] фрагмента из `fragment_setup.xml`.
     *
     * @return Корневой элемент иерархии view.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Инициализирует все наблюдатели LiveData и обработчики событий.
     *
     * Подключаемые наблюдатели:
     * - [SetupViewModel.currentPlayerIndex] — обновляет заголовок и поле.
     * - [SetupViewModel.unplacedShips] — перестраивает панель выбора кораблей.
     * - [SetupViewModel.selectedShipSize] — подсвечивает выбранный корабль.
     * - [SetupViewModel.currentOrientation] — обновляет текст кнопки ориентации.
     * - [SetupViewModel.boardUpdated] — перерисовывает [BoardView].
     * - [SetupViewModel.setupTransitionPlayerIndex] — навигирует к промежуточному экрану.
     * - [SetupViewModel.setupComplete] — инициирует выбор стартового игрока и переход к игре.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val boardView = binding.setupBoardView
        boardView.displayMode = BoardView.DisplayMode.SETUP

        binding.languageButton.setOnClickListener {
            toggleAppLanguage()
        }

        setupViewModel.currentPlayerIndex.observe(viewLifecycleOwner) { playerIndex ->
            val name = setupViewModel.getPlayerName(playerIndex)
            binding.setupTitle.text = getString(R.string.setup_player_title, name)
            boardView.setBoard(setupViewModel.getCurrentBoard())
        }

        setupViewModel.unplacedShips.observe(viewLifecycleOwner) { ships ->
            latestUnplacedShips = ships
            updateShipSelector(ships, setupViewModel.selectedShipSize.value)
            updateSelectedShipLabel(setupViewModel.selectedShipSize.value, ships)
        }

        setupViewModel.selectedShipSize.observe(viewLifecycleOwner) { selectedSize ->
            updateShipSelectionHighlight(selectedSize)
            updateSelectedShipLabel(selectedSize, latestUnplacedShips)
        }

        setupViewModel.currentOrientation.observe(viewLifecycleOwner) { orientation ->
            updateOrientationButtonText(orientation)
        }

        setupViewModel.boardUpdated.observe(viewLifecycleOwner) { _ ->
            boardView.setBoard(setupViewModel.getCurrentBoard())
        }

        setupViewModel.setupTransitionPlayerIndex.observe(viewLifecycleOwner) { nextPlayer ->
            if (nextPlayer != null && findNavController().currentDestination?.id == R.id.setupFragment) {
                findNavController().navigate(R.id.action_setup_to_turnTransition)
            }
        }

        setupViewModel.setupComplete.observe(viewLifecycleOwner) { complete ->
            if (complete && findNavController().currentDestination?.id == R.id.setupFragment) {
                if (setupViewModel.startingPlayerIndex.value == null) {
                    showStartingPlayerDialog()
                } else {
                    findNavController().navigate(R.id.action_setup_to_game)
                }
            }
        }

        binding.orientationButton.setOnClickListener {
            setupViewModel.toggleOrientation()
        }

        binding.undoButton.setOnClickListener {
            if (!setupViewModel.undoLastPlacement()) {
                showError(getString(R.string.setup_undo_unavailable))
            }
        }

        binding.resetButton.setOnClickListener {
            setupViewModel.resetCurrentBoard()
        }

        setupBoardInteractions()
    }

    /**
     * Перестраивает контейнер кнопок выбора кораблей.
     *
     * Кнопки группируются по размеру (от большего к меньшему)
     * и показывают оставшееся количество кораблей каждого типа.
     * При пустом списке выводится надпись «Все корабли расставлены».
     *
     * @param ships        Список размеров неразмещённых кораблей.
     * @param selectedSize Текущий выбранный размер (для подсветки).
     */
    private fun updateShipSelector(ships: List<Int>, selectedSize: Int?) {
        binding.shipSelectorContainer.removeAllViews()

        val grouped = ships.groupingBy { it }.eachCount().toSortedMap(reverseOrder())
        if (grouped.isEmpty()) {
            val emptyView = TextView(requireContext()).apply {
                text = getString(R.string.all_ships_placed)
                textSize = 14f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            }
            binding.shipSelectorContainer.addView(emptyView)
            return
        }

        for ((size, count) in grouped) {
            val button = MaterialButton(
                requireContext(),
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                tag = size
                text = getString(R.string.setup_ship_selector_item, size, count)
                isAllCaps = false
                isCheckable = true
                setOnClickListener { setupViewModel.setSelectedShipSize(size) }
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                val margin = dpToPx(6)
                setMargins(margin, margin, margin, margin)
            }
            binding.shipSelectorContainer.addView(button, params)
        }

        updateShipSelectionHighlight(selectedSize)
    }

    /**
     * Настраивает обработчики нажатий по полю в режиме расстановки.
     *
     * - Одиночное нажатие: размещает выбранный корабль в указанной клетке.
     * - Длительное нажатие: удаляет корабль из указанной клетки.
     */
    private fun setupBoardInteractions() {
        val boardView = binding.setupBoardView

        boardView.onCellClickListener = { row, col ->
            if (setupViewModel.selectedShipSize.value == null) {
                showError(getString(R.string.setup_select_ship_prompt))
            } else if (!setupViewModel.placeSelectedShip(row, col)) {
                showError(getString(R.string.ship_placement_error))
            }
        }

        boardView.onCellLongClickListener = { row, col ->
            if (!setupViewModel.removeShipAt(row, col)) {
                showError(getString(R.string.setup_remove_error))
            }
        }
    }

    /**
     * Обновляет визуальную подсветку кнопки выбранного размера корабля.
     *
     * Выбранная кнопка получает увеличенную обводку и полную непрозрачность,
     * остальные — ослабленную (`alpha = 0.8`).
     *
     * @param selectedSize Текущий выбранный размер, или `null` если ничего не выбрано.
     */
    private fun updateShipSelectionHighlight(selectedSize: Int?) {
        for (i in 0 until binding.shipSelectorContainer.childCount) {
            val button = binding.shipSelectorContainer.getChildAt(i) as? MaterialButton ?: continue
            val buttonSize = button.tag as? Int ?: continue
            val selected = buttonSize == selectedSize
            button.isChecked = selected
            button.strokeWidth = if (selected) dpToPx(2) else dpToPx(1)
            button.alpha = if (selected) 1f else 0.8f
        }
    }

    /**
     * Обновляет текстовую подпись о выбранном корабле и оставшемся количестве.
     *
     * @param selectedSize Выбранный размер, или `null`.
     * @param ships        Текущий список неразмещённых кораблей.
     */
    private fun updateSelectedShipLabel(selectedSize: Int?, ships: List<Int>) {
        binding.selectedShipText.text = if (selectedSize == null) {
            getString(R.string.setup_selected_ship_none)
        } else {
            val remaining = ships.count { it == selectedSize }
            getString(R.string.setup_selected_ship, selectedSize, remaining)
        }
    }

    /**
     * Обновляет текст кнопки переключения ориентации.
     *
     * @param orientation Текущая ориентация размещения.
     */
    private fun updateOrientationButtonText(orientation: Orientation) {
        val orientationText = when (orientation) {
            HORIZONTAL -> getString(R.string.orientation_horizontal)
            VERTICAL -> getString(R.string.orientation_vertical)
        }
        binding.orientationButton.text = getString(R.string.setup_orientation_button, orientationText)
    }

    /**
     * Показывает диалог выбора игрока, который будет стрелять первым.
     *
     * Диалог не может быть отменён — пользователь обязан сделать выбор.
     * После выбора сохраняет индекс в [SetupViewModel.setStartingPlayer]
     * и выполняет навигацию к [GameFragment].
     */
    private fun showStartingPlayerDialog() {
        val playerOneName = setupViewModel.getPlayerName(0)
        val playerTwoName = setupViewModel.getPlayerName(1)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.select_starting_player_title)
            .setMessage(R.string.select_starting_player_message)
            .setCancelable(false)
            .setPositiveButton(playerOneName) { dialog, _ ->
                setupViewModel.setStartingPlayer(0)
                dialog.dismiss()
                if (findNavController().currentDestination?.id == R.id.setupFragment) {
                    findNavController().navigate(R.id.action_setup_to_game)
                }
            }
            .setNegativeButton(playerTwoName) { dialog, _ ->
                setupViewModel.setStartingPlayer(1)
                dialog.dismiss()
                if (findNavController().currentDestination?.id == R.id.setupFragment) {
                    findNavController().navigate(R.id.action_setup_to_game)
                }
            }
            .show()
    }

    /**
     * Конвертирует аппаратно-независимые пиксели (dp) в физические пиксели (px).
     *
     * @param dp Значение в dp.
     * @return Значение в px, округлённое до целого.
     */
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    /**
     * Показывает короткий [Toast] с сообщением об ошибке.
     *
     * @param msg Текст сообщения.
     */
    private fun showError(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
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
