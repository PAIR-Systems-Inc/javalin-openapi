@file:Suppress("unused")

package io.javalin.openapi.processor

import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiCallback
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.processor.specification.OpenApiAnnotationProcessorSpecification
import net.javacrumbs.jsonunit.assertj.JsonAssertions.json
import net.javacrumbs.jsonunit.assertj.assertThatJson
import org.junit.jupiter.api.Test

internal class OpenApiAnnotationTest : OpenApiAnnotationProcessorSpecification() {

    enum class SortOrder { ASCENDING, DESCENDING }

    class Sorter(val field: String, val order: SortOrder)

    @OpenApi(
        path = "/query-params",
        versions = ["should_keep_enum_query_params_plain_and_json_encode_structured_ones"],
        queryParams = [
            OpenApiParam(name = "sortOrder", type = SortOrder::class, defaultValue = "DESCENDING"),
            OpenApiParam(name = "orders", type = Array<SortOrder>::class),
            OpenApiParam(name = "sorter", type = Sorter::class),
        ]
    )
    @Test
    fun should_keep_enum_query_params_plain_and_json_encode_structured_ones() =
        withOpenApi("should_keep_enum_query_params_plain_and_json_encode_structured_ones") {
            val params = "$.paths['/query-params'].get.parameters"
            assertThatJson(it).inPath("$params[0]").isObject.doesNotContainKey("content")
            assertThatJson(it).inPath("$params[0].schema.\$ref").isEqualTo("#/components/schemas/SortOrder")
            assertThatJson(it).inPath("$params[0].schema.default").isEqualTo("DESCENDING")
            assertThatJson(it).inPath("$params[1]").isObject.doesNotContainKey("content")
            assertThatJson(it).inPath("$params[1].schema.type").isEqualTo("array")
            assertThatJson(it).inPath("$params[2]").isObject.doesNotContainKey("schema")
            assertThatJson(it)
                .inPath("$params[2].content.application/json.schema.\$ref")
                .isEqualTo("#/components/schemas/Sorter")
        }

    @OpenApi(
        path = "/",
        versions = ["should_generate_info"]
    )
    @Test
    fun should_generate_info() = withOpenApi("should_generate_info") {
        assertThatJson(it)
            .isObject
            .containsEntry("openapi", "3.1.0")
            .containsEntry("info", json("""{ "title":"", "version": "" }"""))
    }

    @OpenApi(
        path = "/basic",
        versions = ["should_contain_all_basic_properties_from_openapi_annotation"],
        summary = "Test summary",
        operationId = "Test operation id",
        description = "Test description",
        tags = ["Test tag"],
        deprecated = true,
    )
    @Test
    fun should_contain_all_basic_properties_from_openapi_annotation() = withOpenApi("should_contain_all_basic_properties_from_openapi_annotation") {
        assertThatJson(it)
            .inPath("$.paths['/basic'].get")
            .isObject
            .containsAllEntriesOf(linkedMapOf(
                "tags" to json("['Test tag']"),
                "summary" to "Test summary",
                "description" to "Test description",
                "operationId" to "Test operation id",
                "deprecated" to true,
            ))

        assertThatJson(it)
            .inPath("$.paths['/basic'].get")
            .isObject
            .doesNotContainKey("parameters")
            .doesNotContainKey("security")
    }

    @OpenApi(
        path = "/callback",
        versions = ["should_generate_callback"],
        callbacks = [
            OpenApiCallback(
                name = "onData",
                url = "{${'$'}request.body#/url}/callback",
                method = HttpMethod.POST,
                summary = "Test summary",
                description = "Test description",
                requestBody = OpenApiRequestBody(
                    content = [OpenApiContent(from = String::class)]
                ),
                responses = [
                    OpenApiResponse(status = "200", content = [OpenApiContent(from = String::class)])
                ]
            )
        ]
    )
    @Test
    fun should_generate_callback() = withOpenApi("should_generate_callback") {
        println(it)

        assertThatJson(it)
            .inPath("$.paths['/callback'].get.callbacks")
            .isObject
            .isEqualTo(json("""{
                "onData": {
                    "{${'$'}request.body#/url}/callback": {
                        "post": {
                            "summary": "Test summary",
                            "description": "Test description",
                            "requestBody": {
                                "content": {
                                    "text/plain": {
                                        "schema": {
                                            "type": "string"
                                        }
                                    }
                                }
                            },
                            "responses": {
                                "200": {
                                    "description": "OK",
                                    "content": {
                                        "text/plain": {
                                            "schema": {
                                                "type": "string"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }"""))
    }

}