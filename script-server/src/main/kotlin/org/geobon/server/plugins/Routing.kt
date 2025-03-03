package org.geobon.server.plugins

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.geobon.pipeline.*
import org.geobon.pipeline.Pipeline.Companion.createMiniPipelineFromScript
import org.geobon.pipeline.Pipeline.Companion.createRootPipeline
import org.geobon.pipeline.RunContext.Companion.scriptRoot
import org.geobon.utils.runCommand
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Used to transport paths through path param.
 * Folder tree not supported, see https://github.com/OAI/OpenAPI-Specification/issues/892
 */
const val FILE_SEPARATOR = '>'
private val gson = Gson()
private val pipelinesRoot = File(System.getenv("PIPELINES_LOCATION"))

private val runningPipelines = mutableMapOf<String, Pipeline>()
private val logger: Logger = LoggerFactory.getLogger("Server")

fun Application.configureRouting() {

    routing {

        get("/{type}/list") {
            val type = call.parameters["type"]
            val root:File
            val extension:String
            when(type) {
                "pipeline" -> {
                    root = pipelinesRoot
                    extension = "json"
                }
                "script" -> {
                    root = scriptRoot
                    extension = "yml"
                }
                else -> {
                    call.respondText(
                        text = "Invalid type $type. Must be either \"script\" or \"pipeline\".",
                        status = HttpStatusCode.BadRequest)
                    return@get
                }
            }

            val possible = mutableMapOf<String, String>()
            root.walkTopDown().forEach { file ->
                if (file.extension == extension) {
                    val relativePath = file.relativeTo(root).path.replace('/', FILE_SEPARATOR)

                    val name = if(file.extension == "yml") { // Scripts
                        val lineStart = "name: "
                        file.useLines {sequence ->
                            sequence.find { l -> l.startsWith(lineStart) }?.substring(lineStart.length)
                        }
                    } else { // Pipelines
                        null // TODO
                    }

                    possible[relativePath] = name ?: file.name // Fallback on file name
                }
            }

            call.respond(possible.toSortedMap(String.CASE_INSENSITIVE_ORDER))
        }

        get("/script/{scriptPath}/info") {
            try {
                // Put back the slashes and replace extension by .yml
                val ymlPath = call.parameters["scriptPath"]!!.run{replace(FILE_SEPARATOR, '/').replace(Regex("""\.\w+$"""), ".yml")}
                val scriptFile = File(scriptRoot, ymlPath)
                if (scriptFile.exists()) {
                    call.respond(Yaml().load(scriptFile.readText()) as Map<String, Any>)
                } else {
                    call.respondText(text = "$scriptFile does not exist", status = HttpStatusCode.NotFound)
                    logger.debug("404: getInfo ${call.parameters["scriptPath"]}")
                }
            } catch (ex: Exception) {
                call.respondText(text = ex.message!!, status = HttpStatusCode.InternalServerError)
                ex.printStackTrace()
            }
        }

        get("/pipeline/{descriptionPath}/info") {
            try {
                // Put back the slashes before reading
                val descriptionFile = File(pipelinesRoot, call.parameters["descriptionPath"]!!.replace(FILE_SEPARATOR, '/'))
                if (descriptionFile.exists()) {
                    val descriptionJSON = JSONObject(descriptionFile.readText())
                    val metadataJSON = JSONObject()
                    metadataJSON.putOpt(INPUTS, descriptionJSON.get(INPUTS))
                    metadataJSON.putOpt(OUTPUTS, descriptionJSON.get(OUTPUTS))
                    descriptionJSON.optJSONObject(METADATA)?.let { metadata ->
                        metadata.keys().forEach { key ->
                            metadataJSON.putOpt(key, metadata.get(key))
                        }
                    }

                    call.respondText(metadataJSON.toString(), ContentType.parse("application/json"))
                } else {
                    call.respondText(text = "$descriptionFile does not exist", status = HttpStatusCode.NotFound)
                    logger.debug("404: getListOf ${call.parameters["descriptionPath"]}")
                }
            } catch (ex: Exception) {
                call.respondText(text = ex.message!!, status = HttpStatusCode.InternalServerError)
                ex.printStackTrace()
            }
        }

        get("/pipeline/{descriptionPath}/get") {
            val descriptionFile = File(pipelinesRoot, call.parameters["descriptionPath"]!!.replace(FILE_SEPARATOR, '/'))
            if (descriptionFile.exists()) {
                call.respondText(descriptionFile.readText(), ContentType.parse("application/json"))
            } else {
                call.respondText(text = "$descriptionFile does not exist", status = HttpStatusCode.NotFound)
                logger.debug("404: pipeline/${call.parameters["descriptionPath"]}/get")
            }   
        }

        post("/{type}/{descriptionPath}/run") {
            val singleScript = call.parameters["type"] == "script"

            val inputFileContent = call.receive<String>()
            val descriptionPath = call.parameters["descriptionPath"]!!

            val withoutExtension = descriptionPath.removeSuffix(".json").removeSuffix(".yml")

            // Unique   to this pipeline                    and to these params
            val runId = withoutExtension + FILE_SEPARATOR + RunContext.inputsToMd5(inputFileContent)
            val pipelineOutputFolder = File(outputRoot, runId.replace(FILE_SEPARATOR, '/'))
            logger.info("Pipeline: $descriptionPath\nFolder: $pipelineOutputFolder\nBody: $inputFileContent")

            // Validate the existence of the file
            val descriptionFile = File(
                if (singleScript) scriptRoot else pipelinesRoot,
                descriptionPath.replace(FILE_SEPARATOR, '/')
            )
            if(!descriptionFile.exists()) {
                call.respondText(
                    text = "Script $descriptionPath not found on this server.".also { logger.warn(it) },
                    status = HttpStatusCode.NotFound
                )
                return@post
            }

            runCatching {
                if(singleScript) {
                    createMiniPipelineFromScript(descriptionFile, descriptionPath, inputFileContent)
                } else {
                    createRootPipeline(descriptionFile, inputFileContent)
                }
            }.onSuccess { pipeline ->
                runningPipelines[runId] = pipeline
                try {
                    call.respondText(runId)

                    pipelineOutputFolder.mkdirs()
                    val resultFile = File(pipelineOutputFolder, "pipelineOutput.json")
                    logger.trace("Pipeline outputting to {}", resultFile)

                    File(pipelineOutputFolder,"input.json").writeText(inputFileContent)
                    val scriptOutputFolders = pipeline.pullFinalOutputs().mapKeys { it.key.replace('/', FILE_SEPARATOR) }
                    resultFile.writeText(gson.toJson(scriptOutputFolders))
                } catch (ex:Exception) {
                    ex.printStackTrace()
                } finally {
                    runningPipelines.remove(runId)
                }

            }.onFailure {
                call.respondText(text = it.message ?: "", status = HttpStatusCode.InternalServerError)
                logger.debug("run: ${it.message}")
            }
        }
        
        get("/{type}/{id}/outputs") {
            // type: The value pipeline of script is for api consistency, it makes no real difference for this API call.
            val id = call.parameters["id"]!!
            val pipeline = runningPipelines[id]
            if (pipeline == null) {
                val outputFolder = File(outputRoot, id.replace(FILE_SEPARATOR, '/'))
                val outputFile = File(outputFolder, "pipelineOutput.json")
                if(outputFile.exists()) {
                    val typeToken = object : TypeToken<Map<String, Any>>() {}.type
                    call.respond(gson.fromJson<Map<String, String>>(outputFile.readText(), typeToken))
                } else {
                    call.respondText(text = "Run \"$id\" was not found on this server.", status = HttpStatusCode.NotFound)
                }
            } else {
                call.respond(pipeline.getLiveOutput().mapKeys { it.key.replace('/', FILE_SEPARATOR) })
            }
        }
        
        get("/{type}/{id}/stop") {
            val id = call.parameters["id"]!!
            runningPipelines[id]?.let { pipeline ->
                // the pipeline is running, we need to stop it
                pipeline.stop()
                logger.debug("Cancelled $id")
                call.respond(HttpStatusCode.OK)
            } ?: call.respond(/*412*/HttpStatusCode.PreconditionFailed, "The pipeline wasn't running")
        }

        get("/api/versions") {
            call.respond("""
                UI: ${"docker exec -i biab-ui cat /version.txt".runCommand()}
                Script server: ${"cat /version.txt".runCommand()}
                   ${"python3 --version".runCommand()}
                R runner: ${"docker exec -i biab-runner-r cat /version.txt".runCommand()}
                   ${"docker exec -i biab-runner-r Rscript --version".runCommand()}
                Julia runner: ${"docker exec -i biab-runner-julia cat /version.txt".runCommand()}
                   ${"docker exec -i biab-runner-julia julia --version".runCommand()}
                TiTiler: ${
                    "docker inspect --type=image -f '{{ .Created }}' ghcr.io/developmentseed/titiler".runCommand()
                    ?.let { it.substring(0, it.lastIndexOf(':')).replace('T', ' ') }}
                """.trimIndent())
        }
    }
}
