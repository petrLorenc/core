package com.promethist.core.builder

import com.promethist.core.model.SttConfig
import com.promethist.core.model.TtsConfig
import com.promethist.core.model.Voice
import com.promethist.core.type.PropertyMap
import com.promethist.util.LoggerDelegate
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

class DialogueSourceCodeBuilder(val dialogueId: String, val buildId: String, val name: String, val version: Int) {

    // builder configuration:
    var parameters: PropertyMap = mapOf()
    var properties: PropertyMap = mapOf()
    var initCode: CharSequence = ""
    var extensionCode: CharSequence = ""
    var parentClass: String? = null
    val className: String
    var code: String = ""
        get() {
            if (field.isEmpty()) error("Code has not been build yet.")
            return field
        }
    val scriptCode get() = "$code\n//--export-class\n$className::class\n"
    val source = StringBuilder()
    private val logger by LoggerDelegate()
    private val names: MutableList<String>

    init {
        if (!name.matches(Regex("([\\w\\-]+)/([\\w\\-]+)")))
            error("dialogue name $name does not conform to naming convention (product-name/dialogue-name)")
        names = name.split("/").toMutableList()
        className = "Model"// + names.removeAt(names.size - 1)
    }

    fun build() {
        if (parentClass == null) {
            val voice = if (properties.containsKey("voice")) properties["voice"] as Voice else Voice.Grace
            val config = TtsConfig.forVoice(voice)
            parentClass = when (config.locale.language) {
                "en" -> "BasicEnglishDialogue"
                "cs" -> "BasicCzechDialogue"
                else -> "BasicDialogue"
            }
        }

        logger.info("building source code for dialogue $name")
        logger.info("class $className : $parentClass")

        source.clear()
        writeHeader()
        writeClassSignature()
        writeInit()

        goBacks.forEach { write(it) }
        sleeps.forEach { write(it) }
        globalIntents.forEach { write(it) }
        intents.forEach { write(it) }
        actions.forEach { write(it) }
        globalActions.forEach { write(it) }
        userInputs.forEach { write(it) }
        speeches.forEach { write(it) }
        images.forEach { write(it) }
        sounds.forEach { write(it) }
        commands.forEach { write(it) }
        functions.forEach { write(it) }
        subDialogues.forEach { write(it) }

        writeTransitions()

        source.appendln('}')
        if (extensionCode.isNotBlank()) {
            source.append(extensionCode)
        }
        logger.info("built source code for dialogue $name")

        code = source.toString()
    }

    private val intents = mutableListOf<Intent>()
    private val globalIntents = mutableListOf<GlobalIntent>()
    private val userInputs = mutableListOf<UserInput>()
    private val speeches = mutableListOf<Speech>()
    private val sounds = mutableListOf<Sound>()
    private val images = mutableListOf<Image>()
    private val commands = mutableListOf<Command>()
    private val functions = mutableListOf<Function>()
    private val subDialogues = mutableListOf<SubDialogue>()
    private val goBacks = mutableListOf<GoBack>()
    private val sleeps = mutableListOf<Sleep>()
    private val actions = mutableListOf<Action>()
    private val globalActions = mutableListOf<GlobalAction>()
    private val transitions = mutableMapOf<String, String>()

    interface Node

    data class Intent(val nodeId: Int, val nodeName: String, val threshold: Float, val utterances: List<String>, val entities: List<String>) : Node
    data class GlobalIntent(val nodeId: Int, val nodeName: String, val threshold: Float,  val utterances: List<String>, val entities: List<String>) : Node
    data class UserInput(val nodeId: Int, val nodeName: String, val intentNames: List<String>, val actionNames: List<String>, val sttMode: SttConfig.Mode? = null, val skipGlobalIntents: Boolean, val transitions: Map<String, String>, val expectedPhrases: CharSequence = "", val code: CharSequence = "") : Node
    data class Speech(val nodeId: Int, val nodeName: String, val background: String? = null, val repeatable: Boolean, val texts: List<String>) : Node
    data class Sound(val nodeId: Int, val nodeName: String, val source: String, val repeatable: Boolean) : Node
    data class Image(val nodeId: Int, val nodeName: String, val source: String) : Node
    data class Command(val nodeId: Int, val nodeName: String, val command: String, val code: CharSequence) : Node
    data class Function(val nodeId: Int, val nodeName: String, val transitions: Map<String, String>, val code: CharSequence) : Node
    data class SubDialogue(val nodeId: Int, val nodeName: String, val subDialogueId: String, val code: CharSequence = "") : Node
    data class GoBack(val nodeId: Int, val nodeName: String, val repeat: Boolean) : Node
    data class Sleep(val nodeId: Int, val nodeName: String, val timeout: Int) : Node
    data class Action(val nodeId: Int, val nodeName: String, val action: String) : Node
    data class GlobalAction(val nodeId: Int, val nodeName: String, val action: String) : Node

    fun addNode(node: UserInput) = userInputs.add(node)
    fun addNode(node: Intent) = intents.add(node)
    fun addNode(node: GlobalIntent) = globalIntents.add(node)
    fun addNode(node: Speech) = speeches.add(node)
    fun addNode(node: Image) = images.add(node)
    fun addNode(node: Sound) = sounds.add(node)
    fun addNode(node: Command) = commands.add(node)
    fun addNode(node: Function) = functions.add(node)
    fun addNode(node: SubDialogue) = subDialogues.add(node)
    fun addNode(node: GoBack) = goBacks.add(node)
    fun addNode(node: Sleep) = sleeps.add(node)
    fun addNode(node: Action) = actions.add(node)
    fun addNode(node: GlobalAction) = globalActions.add(node)
    fun addTransition(transition: Pair<String, String>) = transitions.put(transition.first, transition.second)

    private fun writeHeader() {
        source
                .appendln("//--dialogue-model;name:$name;time:" + LocalDateTime.now())
                .append("package model.$buildId")
                .appendln()
                .appendln("import com.promethist.core.*")
                .appendln("import com.promethist.core.type.*")
                .appendln("import com.promethist.core.type.value.*")
                .appendln("import com.promethist.core.model.*")
                .appendln("import com.promethist.core.dialogue.*")
                .appendln("import com.promethist.core.runtime.*")
                .appendln()
    }

    private fun writeInit() {
        source.appendln("\toverride val dialogueId = \"$dialogueId\"")
        source.appendln("\toverride val buildId = \"$buildId\"")
        source.appendln("\toverride val dialogueName = \"$name\"")
        source.appendln("\toverride val version = $version")

        properties.forEach {
            val className = it.value::class.simpleName
            source.append("override val ${it.key}: $className = ")
            when (it.value) {
                is String -> {
                    source.append('"').append((it.value as String).trim().replace("\"", "\\\"")).append('"')
                }
                is Enum<*> -> with (it.value as Enum<*>) {
                    source.append(className).append('.').append(name)
                }
                else -> source.append(it.value.toString())
            }
            source.appendln()
        }

        if (initCode.isNotBlank()) {
            source.appendln("//--code-start;type:init")
            source.appendln(initCode.replace(Regex("//\\#include ([^\\s]+)")) {
                val path = it.groupValues[1]
                (if (path.startsWith("http://") || path.startsWith("https://")) {
                    val conn = URL(path).openConnection() as HttpURLConnection
                    if (conn.responseCode != 200)
                        error("include from url $path returned code ${conn.responseCode}")
                    else
                        conn.inputStream
                } else {
                    FileInputStream(path)
                }).use { input ->
                    val buf = ByteArrayOutputStream()
                    input.copyTo(buf)
                    "//--include-start;url:$path\n" + String(buf.toByteArray(), StandardCharsets.UTF_8) + "\n//--include-end\n"
                }
            })
            source.appendln("//--code-end;type:init")
        }
    }

    private fun writeClassSignature() {
        source.append("class $className(")

        var comma = ""
        parameters.forEach {
            if (it.value !is Int && it.value !is Long && it.value !is Float && it.value !is Double && it.value !is String && it.value !is Boolean)
                error("arg ${it.key} is if on unsupported type ${it.value::class.simpleName} (only Int, Long, Float, Double, String, Boolean supported)")
            source.append("${comma}val ${it.key}: ${it.value::class.simpleName} = ")
            if (it.value is String)
                source.append('"').append((it.value as String).trim().replace("\"", "\\\"")).append('"')
            else
                source.append(it.value.toString())
            comma = ", "
        }
        source.appendln(") : $parentClass() {")
    }

    private fun write(goBack: GoBack) = with(goBack) {
        source.appendln("\tval $nodeName = GoBack($nodeId, $repeat)")
    }

    private fun write(sleep: Sleep) = with(sleep) {
        source.appendln("\tval $nodeName = Sleep($nodeId, $timeout)")
    }

    private fun write(intent: Intent) = with(intent) {
        source.append("\tval $nodeName = Intent($nodeId, \"$nodeName\", ${threshold}F, listOf(${entities.joinToString(", ") { "\"" + it + "\"" }})")
        utterances.forEach {
            source.append(", ").append('"').append(it.trim().replace("\"", "\\\"")).append('"')
        }
        source.appendln(')')
    }

    private fun write(intent: GlobalIntent) = with(intent) {
        source.append("\tval $nodeName = GlobalIntent($nodeId, \"$nodeName\", ${threshold}F, listOf(${entities.joinToString(", ") { "\"" + it + "\"" }})")
        utterances.forEach {
            source.append(", ").append('"').append(it.trim().replace("\"", "\\\"")).append('"')
        }
        source.appendln(')')
    }

    private fun write(userInput: UserInput) = with(userInput) {
        val intents  = intentNames.joinToString(", ")
        val actions = actionNames.joinToString(", ")
        val expectedPhrases = this.expectedPhrases.toString().let { s ->
            if (s.isBlank())
                ""
            else
                s.replace('\n', ',')
                    .split(',').joinToString(", ") { """ExpectedPhrase("${it.trim()}")""" }
        }
        source.append("\tval $nodeName = UserInput($nodeId, $skipGlobalIntents, "
                + (if (userInput.sttMode == null) "null" else "SttConfig.Mode.${userInput.sttMode}")
                + ", listOf($expectedPhrases), arrayOf($intents), arrayOf($actions) ) {")
        transitions.forEach { source.appendln("\t\tval ${it.key} = Transition(${it.value})") }
        source.appendln("//--code-start;type:userInput;name:$nodeName")
        if (code.isNotEmpty()) {
            source.appendln(code)
        } else {
            source.appendln("pass")
        }
        source.appendln("//--code-end;type:userInput;name:$nodeName").appendln("\t}")
    }

    fun enumerateExpressions(s: String): String {
        var i = 0
        var l = 0
        val b = StringBuilder()
        while (++i < s.length) {
            if (s[i - 1] == '#' && s[i] == '{') {
                b.append(s.substring(l, i - 1))
                l = i + 1
                var c = 1
                while (c > 0 && ++i < s.length) {
                    if (s[i - 1] != '\\') {
                        if (s[i] == '{')
                            c++
                        if (s[i] == '}')
                            c--
                    }
                }
                b.append("\${enumerate(").append(s.substring(l, i)).append(")}")
                l = ++i
            }
        }
        if (l < s.length)
            b.append(s.substring(l, s.length))
        return b.toString()
    }

    private fun write(speech: Speech) = with(speech) {
        source.append("\tval $nodeName = Response($nodeId, $repeatable, " +
                (if (background != null) "\"$background\"" else "null"))
        texts.forEach { text ->
            source.append(", { \"\"\"").append(enumerateExpressions(text)).append("\"\"\" }")
        }
        source.appendln(')')
    }

    private fun write(image: Image) = with(image) {
        this@DialogueSourceCodeBuilder.source.appendln("\tval $nodeName = Response($nodeId, image = \"${source}\")")
    }

    private fun write(sound: Sound) = with(sound) {
        this@DialogueSourceCodeBuilder.source.appendln("\tval $nodeName = Response($nodeId, audio = \"${source}\", isRepeatable = ${sound.repeatable})")
    }

    private fun write(command: Command) = with(command) {
        this@DialogueSourceCodeBuilder.source.appendln("\tval $nodeName = Command($nodeId, \"${this.command}\", \"\"\"${code}\"\"\")")
    }

    private fun write(function: Function) = with(function) {
        source.appendln("\tval $nodeName = Function($nodeId) {")
        source.appendln("\tval transitions = mutableListOf<Transition>()")
        val lastLine = code.toString().trim().substringAfterLast('\n')
        var lastLineTransition = (lastLine.indexOf("Transition(") >= 0)
        transitions.forEach {
            if (lastLine.indexOf(it.key) >= 0)
                lastLineTransition = true
            if (code.indexOf(it.key) >= 0)
                source.append("\t\tval ${it.key} = ")
            source.appendln("Transition(${it.value}).apply { transitions.add(this) }")
        }
        source.appendln("//--code-start;type:function;name:$nodeName")
        source.appendln(code)
        source.appendln("//--code-end;type:function;name:$nodeName")

        if (transitions.size == 1 && !lastLineTransition) {
            transitions.entries.first().let {
                source.appendln("Transition(${it.value})")
            }
        }
        source.appendln("\t}")
    }

    private fun write(subDialogue: SubDialogue) = with(subDialogue) {
        source.appendln("\tval $nodeName = SubDialogue($nodeId, \"$subDialogueId\") {")
        source.appendln("//--code-start;type:subDialogue;name:$nodeName")
        source.appendln(if (code.isNotEmpty()) code else "it.create()")
        source.appendln("//--code-end;type:subDialogue;name:$nodeName").appendln("\t}")
    }

    private fun write(action: Action) = with(action) {
        source.appendln("\tval $nodeName = Action($nodeId,\"$nodeName\", \"${this.action}\")")
    }

    private fun write(action: GlobalAction) = with(action) {
        source.appendln("\tval $nodeName = GlobalAction($nodeId,\"$nodeName\", \"${this.action}\")")
    }

    private fun writeTransitions() {
        source.appendln()
        source.appendln("\tinit {")
        transitions.forEach { source.appendln("\t\t${it.key}.next = ${it.value}") }
        source.appendln("\t}")
    }
}