import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

open class VersionNameUpdate : DefaultTask() {
    @Input
    var versionName = "0.0.0"

    @Input
    var filesToUpdate = listOf<String>()

    @TaskAction
    fun updateVersions() {
        filesToUpdate.forEach { file ->
            val versionStr = versionName.replace("SNAPSHOT", SimpleDateFormat("yyMMdd.HHmm").format(Date()))
            val text = mutableListOf<String>()
            var updated = false

            if (System.currentTimeMillis() - File(file).lastModified() > TimeUnit.DAYS.toMillis(1)) {
                FileReader(file).use {
                    text += it.readLines()
                    for (i in text.indices) {
                        val startI = text[i].indexOf("const val KOOL_VERSION = ")
                        if (startI >= 0) {
                            text[i] = text[i].substring(0 until startI) + "const val KOOL_VERSION = \"$versionStr\""
                            updated = true
                            break
                        }
                    }
                }
                if (updated) {
                    FileWriter(file).use {
                        text.forEach { line ->
                            it.append(line).append(System.lineSeparator())
                        }
                    }
                }
            }
        }
    }
}