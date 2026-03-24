package com.navalcombat.model

/**
 * Sealed-иерархия состояний партии «Морского боя».
 *
 * Каждый подкласс описывает конкретный этап игры и несёт
 * минимально необходимые данные (индексы игроков). Благодаря `sealed`
 * компилятор гарантирует исчерпывающий `when`-анализ во всех
 * наблюдателях [LiveData][androidx.lifecycle.LiveData].
 *
 * Последовательность состояний в типичной партии:
 * ```
 * Setup(0) → Setup(1) → Playing(X) ⇄ TurnTransition(Y) → GameOver(winner)
 * ```
 *
 * @see com.navalcombat.viewmodel.GameViewModel.gameState
 */
sealed class GameState {

    /**
     * Этап расстановки кораблей.
     *
     * Игроки поочерёдно размещают флот на своих полях.
     * Между расстановками первого и второго игрока отображается
     * промежуточный экран ([TurnTransitionFragment][com.navalcombat.ui.TurnTransitionFragment]).
     *
     * @property currentPlayerIndex Индекс игрока (0 или 1),
     *     который сейчас расставляет корабли.
     */
    data class Setup(val currentPlayerIndex: Int = 0) : GameState()

    /**
     * Этап активной стрельбы.
     *
     * Текущий игрок выбирает клетку на поле соперника.
     * При попадании или потоплении ход остаётся у того же игрока;
     * при промахе происходит переход в [TurnTransition].
     *
     * @property currentPlayerIndex Индекс игрока (0 или 1),
     *     совершающего выстрел.
     */
    data class Playing(val currentPlayerIndex: Int = 0) : GameState()

    /**
     * Промежуточный экран передачи устройства другому игроку.
     *
     * Требуется для режима «hotseat» — скрывает расположение
     * кораблей текущего игрока перед тем, как устройство
     * перейдёт в руки соперника.
     *
     * @property nextPlayerIndex Индекс игрока, которому
     *     будет передан ход после подтверждения.
     */
    data class TurnTransition(val nextPlayerIndex: Int) : GameState()

    /**
     * Завершение партии — все корабли одного из игроков потоплены.
     *
     * @property winnerIndex Индекс победившего игрока (0 или 1).
     *
     * @see com.navalcombat.ui.GameOverFragment
     */
    data class GameOver(val winnerIndex: Int) : GameState()
}
