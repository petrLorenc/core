package org.promethist.client.standalone.cli

import com.beust.jcommander.Parameter
import org.promethist.client.audio.WakeWordConfig
import org.promethist.client.signal.SignalProcessor
import org.promethist.common.AppConfig
import org.promethist.common.ServiceUrlResolver

open class ClientConfig {

    @Parameter(names = ["-i", "--input"], order = 10, description = "Input file (e.g. stdin, none, /path/to/input.txt)")
    var input = "stdin"

    @Parameter(names = ["-o", "--output"], order = 11, description = "Output file (e.g. stdout, /path/to/output.txt)")
    var output = "stdout"

    @Parameter(names = ["-u", "--url"], order = 12, description = "Port URL")
    var url = ServiceUrlResolver.getEndpointUrl("core")

    @Parameter(names = ["-k", "--key"], order = 13, description = "Port contract key")
    var key = AppConfig.instance.get("port.key", "promethist")

    @Parameter(names = ["-x", "--secret"], order = 14, description = "Port contract secret (some contracts may require)")
    var secret: String? = null

    @Parameter(names = ["-l", "--language"], order = 15, description = "Preferred conversation language")
    var language = "en"

    @Parameter(names = ["-sd", "--speechDevice"], order = 16, description = "Speech device (respeaker2)")
    var speechDevice = "none"

    @Parameter(names = ["-mc", "--micChannel"], order = 17, description = "Microphone channel (count:selected)")
    var micChannel: String = "1:0"

    @Parameter(names = ["-spk", "--speakerName"], order = 18, description = "Speaker name")
    var speakerName: String? = null

    val signalProcessor: SignalProcessor? = null

    val wakeWord: WakeWordConfig? = null
}