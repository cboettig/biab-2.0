package org.geobon.pipeline

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.geobon.pipeline.Pipeline.Companion.createRootPipeline
import org.geobon.utils.productionPipelinesRoot
import org.geobon.utils.withProductionPaths
import org.json.JSONObject
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.fail

@ExperimentalCoroutinesApi
internal class PipelineValidation {

    @BeforeTest
    fun setupOutputFolder() {

    }

    @AfterTest
    fun removeOutputFolder() {
    }

    private fun validateAllPipelines(directory: File): String {
        var errorMessages = ""
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                validateAllPipelines(file)
            } else if (file.extension == "json") {
                try {
                    // Generate fake inputs
                    val fakeInputs = JSONObject()
                    val pipelineJSON = JSONObject(file.readText())
                    pipelineJSON.optJSONObject(INPUTS)?.let { inputsSpec ->
                        inputsSpec.keySet().forEach { key ->
                            inputsSpec.optJSONObject(key)?.let { inputSpec ->
                                fakeInputs.put(
                                    key,
                                    inputSpec.opt(INPUTS__EXAMPLE) ?: JSONObject.NULL
                                )
                            }
                        }
                    }
                    println(fakeInputs.toString(2))

                    // Run validation
                    createRootPipeline(file, fakeInputs.toString(2))
                } catch (e: Exception) {
                    errorMessages += "${file.relativeTo(productionPipelinesRoot)}:\n\t${e.message}\n"
                }
            }
        }

        return errorMessages
    }

    @Test
    fun runValidationOnAllPipelines() = runTest {
        withProductionPaths {
            val errorMessage = validateAllPipelines(productionPipelinesRoot)
            if (errorMessage.isNotEmpty()) {
                fail(errorMessage)
            }
        }
    }
}