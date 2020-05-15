package com.promethist.core.dialogue

import com.promethist.core.Input
import com.promethist.core.language.English
import com.promethist.core.type.DateTime
import com.promethist.core.type.Dynamic
import com.promethist.core.type.Location
import com.promethist.core.type.TimeValue
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.*

abstract class BasicDialogue : Dialogue() {

    enum class Article { None, Indefinite, Definite }

    companion object {

        val pass: Transition? = null
        @Deprecated("Use pass instead, toIntent will be removed")
        val toIntent = pass
        val now: DateTime get() = with (threadContext()) { DateTime.now(context.turn.input.zoneId) }
        val today get() = now.toDay()
        val tomorrow get() = now.day(1)
        val yesterday get() = now.day(-1)

        infix fun DateTime.isSameDateAs(to: DateTime) = true

        fun DateTime.day(dayCount: Long): DateTime =
                plus(dayCount, ChronoUnit.DAYS).with(LocalTime.of(0, 0, 0, 0))

        fun DateTime.toDay() = day(0)

        fun DateTime.isDay(from: Long, to: Long = from): Boolean {
            val thisDay = day(0)
            return now.day(from) <= thisDay && thisDay < now.day(to + 1)
        }

        fun DateTime.isToday() = isDay(0, 0)
        fun DateTime.isTomorrow() = isDay(1, 1)
        fun DateTime.isYesterday() = isDay(-1, -1)
        fun DateTime.isWeekend() = now.let { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY }
        fun DateTime.isHoliday() = isWeekend()
        infix fun DateTime.differsInDaysFrom(dateTime: DateTime) =
                (year * 366 * 24 + hour) - (dateTime.year * 366 * 24 + dateTime.hour)
        infix fun DateTime.differsInHoursFrom(dateTime: DateTime) =
                (year * 366 * 24 + hour) - (dateTime.year * 366 * 24 + dateTime.hour)
        infix fun DateTime.differsInMonthsFrom(dateTime: DateTime) =
                (year * 12 + monthValue) - (dateTime.year * 12 + dateTime.monthValue)
    }

    val turnAttributes get() = with (threadContext()) { context.turn.attributes(dialogueNameWithoutVersion) }

    val sessionAttributes get() = with (threadContext()) { context.session.attributes(dialogueNameWithoutVersion) }

    val userAttributes get() = with (threadContext()) { context.userProfile.attributes(dialogueNameWithoutVersion) }

    fun communityAttributes(communityName: String) = with (threadContext()) { context.communityResource.get(communityName)?.attributes ?: Dynamic.EMPTY }

    fun addResponseItem(vararg value: Any, image: String? = null, audio: String? = null, video: String? = null, repeatable: Boolean = true) = with (threadContext()) {
        context.turn.addResponseItem(enumerate(*value), image, audio, video, repeatable)
    }

    private inline fun unsupportedLanguage(): Nothing {
        val stackTraceElement = Thread.currentThread().stackTrace[1]
        throw error("${stackTraceElement.className}.${stackTraceElement.methodName} does not support language ${language} of dialogue ${dialogueName}")
    }

    // subjective

    fun empty(subj: String) =
            when (language) {
                "en" -> "no"
                "de" -> "kein" //TODO male vs. female
                else -> unsupportedLanguage()
            } + " $subj"

    fun lemma(word: String) = word

    fun plural(word: String) = word.split(" ").joinToString(" ") {
        when (language) {
            "en" -> English.irregularPlurals.getOrElse(it) {
                when {
                    it.endsWith("y") ->
                        it.substring(0, it.length - 1) + "ies"
                    it.endsWith(listOf("s", "sh", "ch", "x", "z", "o")) ->
                        it + "es"
                    else ->
                        it + "s"
                }
            }
            else -> unsupportedLanguage()
        }
    }

    fun article(subj: String, article: Article = Article.Indefinite) =
            when (language) {
                "en" -> when (article) {
                    Article.Indefinite -> (if (subj.startsWithVowel()) "an " else "a ") + subj
                    Article.Definite -> "the $subj"
                    else -> subj
                }
                else -> subj
            }

    fun definiteArticle(subj: String) = article(subj, Article.Definite)

    fun indent(value: Any?) = (value?.let { " " + describe(value) } ?: "")

    fun greeting(name: String? = null) = (
        if (now.hour >= 18 || now.hour < 3)
            mapOf(
                    "en" to "good evening",
                    "de" to "guten abend",
                    "cs" to "dobrý večer",
                    "fr" to "bonsoir"
            )[language] ?: unsupportedLanguage()
        else if (now.hour < 12)
            mapOf(
                    "en" to "good morning",
                    "de" to "guten morgen",
                    "cs" to "dobré ráno",
                    "fr" to "bonjour"
            )[language] ?: unsupportedLanguage()
        else
            mapOf(
                    "en" to "good afternoon",
                    "de" to "guten tag",
                    "cs" to "dobré odpoledne",
                    "fr" to "bonne après-midi"
            )[language] ?: unsupportedLanguage()
        ) + indent(name)


    // descriptive

    fun describe(data: Map<String, Any>): String {
        val list = mutableListOf<String>()
        val isWord = when (language) {
            "en" -> "is"
            "de" -> "ist"
            "cs" -> "je"
            else -> unsupportedLanguage()
        }
        data.forEach {
            list.add("${it.key} $isWord " + describe(it.value))
        }
        return enumerate(list)
    }

    fun describe(data: Collection<String>) = enumerate(data)

    fun describe(data: TimeValue<*>) = describe(data.value) + indent(describe(data.time, 2))

    fun describe(data: Any?, detail: Int = 0) =
        when (data) {
            is Location -> "latitude is ${data.latitude}, longitude is ${data.longitude}"
            is DateTime -> data.toString()
            is String -> data
            null -> "unknown"
            else -> data.toString()
        }

    fun describeMore(data: Any?) = describe(data, 1)

    fun describeMost(data: Any?) = describe(data, 2)

    // quantitative

    infix fun Number.of(subj: String) =
            when (this) {
                0 -> empty(subj.replace("+", ""))
                1 -> describe(this) + " " + subj.replace("+", "")
                else -> describe(this) + " " + (if (subj.indexOf('+') < 0) "$subj+" else subj)
                        .split(" ")
                        .joinToString(" ") {
                            if (it.endsWith("+")) plural(it.substring(0, it.length - 1)) else it
                        }
            }

    infix fun Array<*>.of(subj: String) = size of subj

    infix fun Map<*, *>.of(subj: String) = size of subj

    // enumerative

    infix fun Collection<String>.of(subj: String) = enumerate(this, subj)

    fun enumerate(vararg data: Any?, subjBlock: (Int) -> String, before: Boolean = false, conj: String = "", detail: Int = 0) =
            enumerate(data.asList().map { describe(it, detail) }, subjBlock, before, conj)

    fun enumerate(vararg data: Any?, subj: String = "", before: Boolean = false, conj: String = "", detail: Int = 0) =
            enumerate(data.asList().map { describe(it, detail) }, subj, before, conj)

    fun enumerate(data: Collection<String>, subjBlock: (Int) -> String, before: Boolean = false, conj: String = ""): String {
        val list = if (data is List<String>) data else data.toList()
        val subj = subjBlock(list.size).split(" ")
                .joinToString(" ") {
                    if (it.endsWith("+")) plural(it.substring(0, it.length - 1)) else it
                }
        when {
            list.isEmpty() ->
                return empty(subj)
            list.size == 1 ->
                return (if (before && subj.isNotEmpty()) "$subj " else "") +
                        list.first() +
                        (if (!before && subj.isNotEmpty()) " $subj" else "")
            else -> {
                val op = if (conj == "")
                    mapOf("en" to "and", "de" to "und", "cs" to "a")[language] ?: unsupportedLanguage()
                else
                    conj
                val str = StringBuilder()
                if (before && subj.isNotEmpty())
                    str.append(subj).append(' ')
                for (i in list.indices) {
                    if (i > 0)
                        str.append(if (i == list.size - 1) ", $op " else ", ")
                    str.append(list[i])
                }
                if (!before && subj.isNotEmpty())
                    str.append(' ').append(subj)
                return str.toString()
            }
        }
    }

    fun enumerate(subjBlock: (Int) -> String, data: Collection<String>, conj: String = "") =
            enumerate(data, subjBlock, true, conj)

    fun enumerate(subj: String, data: Collection<String>, conj: String = "") =
            enumerate(data, subj, true, conj)
    
    fun enumerate(data: Collection<String>, subj: String = "", before: Boolean = false, conj: String = "") =
            enumerate(data, { subj }, before, conj)

    fun enumerate(map: Map<String, Number>): String = enumerate(mutableListOf<String>().apply {
        map.forEach { add(it.value of it.key) }
    })

    fun enumerate(vararg pairs: Pair<String, Number>) = enumerate(pairs.toMap())
}

fun String.startsWithVowel() = Regex("[aioy].*").matches(this)

fun String.tokenize(): List<Input.Word> {
    val tokens = mutableListOf<Input.Word>()
    val tokenizer = StringTokenizer(this, " \t\n\r,.:;?![]'")
    while (tokenizer.hasMoreTokens()) {
        tokens.add(Input.Word(tokenizer.nextToken().toLowerCase()))
    }
    return tokens
}