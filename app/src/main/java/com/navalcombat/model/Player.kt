package com.navalcombat.model

/**
 * Модель игрока в партии «Морского боя».
 *
 * Связывает имя участника с его персональным игровым полем [Board].
 * Экземпляры создаются на этапе расстановки кораблей в [SetupViewModel][com.navalcombat.viewmodel.SetupViewModel]
 * и затем передаются в [GameViewModel][com.navalcombat.viewmodel.GameViewModel] для ведения матча.
 *
 * Реализован как `data class` для удобного сравнения при переинициализации игры.
 *
 * @property name  Отображаемое имя игрока (задаётся на экране приветствия).
 * @property board Персональное поле с расставленными кораблями; по умолчанию — пустое.
 */
data class Player(
    val name: String,
    val board: Board = Board()
)
