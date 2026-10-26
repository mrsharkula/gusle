package com.sharkula.gusle.ui.settings

import com.sharkula.gusle.FakePrefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedChoiceTest {
    private enum class Fruit(val id: String) {
        Apple("apple"),
        Pear("pear"),
        Plum("plum");

        companion object {
            fun fromId(id: String?): Fruit = entries.firstOrNull { it.id == id } ?: Apple
        }
    }

    /** The file on "disk"; a new choice on it is Gusle started again. */
    private val file = FakePrefs()
    private val prepared = mutableListOf<Fruit>()
    private var chosen = 0

    private fun choice() = SavedChoice<Fruit>("fruit", { it.id }, { Fruit.fromId(it) }, { chosen++ }, { file }, { prepared += it })

    private fun SavedChoice<Fruit>.loaded() = runBlocking { load() }

    @Test
    fun isTheDefaultBeforeAnyIsChosen() {
        val fruits = choice()
        assertNull(fruits.current)
        assertEquals(Fruit.Apple, fruits.loaded())
        assertEquals(Fruit.Apple, fruits.current)
    }

    @Test
    fun comesBackAfterARestart() {
        choice().choose(Fruit.Plum)
        assertEquals(Fruit.Plum, choice().loaded())
    }

    @Test
    fun isSavedByItsId() {
        choice().choose(Fruit.Pear)
        assertEquals("pear", file.values["fruit"])
    }

    @Test
    fun aChoiceIsCurrentAtOnce() {
        val fruits = choice()
        fruits.loaded()
        fruits.choose(Fruit.Plum)
        assertEquals(Fruit.Plum, fruits.current)
    }

    @Test
    fun choosingTheCurrentOneWritesNothing() {
        val fruits = choice()
        fruits.choose(Fruit.Pear)
        fruits.choose(Fruit.Pear)
        assertEquals(1, file.writes)
        assertEquals(1, chosen)
    }

    @Test
    fun aChoiceBeforeTheReadIsNewer() {
        choice().choose(Fruit.Pear)
        val fruits = choice()
        fruits.choose(Fruit.Plum)
        assertEquals(Fruit.Plum, fruits.loaded())
    }

    @Test
    fun anUnknownIdIsTheDefault() {
        // E.g. a value a later build no longer has.
        file.values["fruit"] = "quince"
        assertEquals(Fruit.Apple, choice().loaded())
    }

    @Test
    fun aValueOfAnotherTypeIsTheDefault() {
        file.values["fruit"] = 3
        assertEquals(Fruit.Apple, choice().loaded())
    }

    /** E.g. a theme's colors, made with the read. A chosen value is the chooser's to prepare. */
    @Test
    fun theReadValueIsPrepared() {
        file.values["fruit"] = "plum"
        val fruits = choice()
        fruits.loaded()
        fruits.choose(Fruit.Pear)
        assertEquals(listOf(Fruit.Plum), prepared)
    }
}
