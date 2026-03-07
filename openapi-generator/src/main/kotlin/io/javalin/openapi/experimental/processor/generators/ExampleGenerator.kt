package io.javalin.openapi.experimental.processor.generators

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import io.javalin.openapi.ExampleValueType
import io.javalin.openapi.NULL_STRING
import io.javalin.openapi.OpenApiExampleProperty
import io.javalin.openapi.experimental.processor.shared.createArrayNode
import io.javalin.openapi.experimental.processor.shared.createObjectNode
import io.javalin.openapi.experimental.processor.shared.jsonMapper
import java.math.BigDecimal

data class ExampleProperty(
    val name: String?,
    val value: String?,
    val type: ExampleValueType,
    val raw: String?,
    val objects: List<ExampleProperty>?
)

fun OpenApiExampleProperty.toExampleProperty(): ExampleProperty =
    ExampleProperty(
        name = this.name.takeIf { it != NULL_STRING },
        value = this.value.takeIf { it != NULL_STRING },
        type = this.type,
        raw = this.raw.takeIf { it != NULL_STRING },
        objects = this.objects.map { it.toExampleProperty() }.takeIf { it.isNotEmpty() },
    )

object ExampleGenerator {

    data class GeneratorResult(
        val simpleValue: String?,
        val jsonElement: JsonNode?,
    ) {
        init {
            when {
                simpleValue != null && jsonElement != null -> throw IllegalArgumentException("simpleValue and jsonElement cannot be both non-null")
                simpleValue == null && jsonElement == null -> throw IllegalArgumentException("simpleValue and jsonElement cannot be both null")
            }
        }
    }

    fun generateFromExamples(examples: List<ExampleProperty>): GeneratorResult {
        if (examples.isRawList()) {
            val jsonArray = createArrayNode()
            examples.forEach { jsonArray.add(it.toSimpleExampleValue().toJsonNode()) }
            return GeneratorResult(null, jsonArray)
        }

        if (examples.isObjectList()) {
            val jsonArray = createArrayNode()
            examples.forEach { jsonArray.add(it.toSimpleExampleValue().jsonElement!!) }
            return GeneratorResult(null, jsonArray)
        }

        return GeneratorResult(null, examples.toJsonObject())
    }

    private fun ExampleProperty.toSimpleExampleValue(): GeneratorResult =
        when {
            this.raw != null -> GeneratorResult(null, jsonMapper.readTree(this.raw))
            this.type == ExampleValueType.NULL -> GeneratorResult(null, JsonNodeFactory.instance.nullNode())
            this.value != null -> when (this.type) {
                ExampleValueType.STRING -> GeneratorResult(this.value, null)
                ExampleValueType.NUMBER -> GeneratorResult(null, JsonNodeFactory.instance.numberNode(BigDecimal(this.value)))
                ExampleValueType.BOOLEAN -> GeneratorResult(null, JsonNodeFactory.instance.booleanNode(this.value.toBoolean()))
                ExampleValueType.NULL -> GeneratorResult(null, JsonNodeFactory.instance.nullNode())
            }
            this.objects?.isNotEmpty() == true -> generateFromExamples(this.objects)
            else -> throw IllegalArgumentException("Example object must have value, raw value or objects ($this)")
        }

    private fun List<ExampleProperty>.toJsonObject(): ObjectNode {
        val jsonObject = createObjectNode()
        this.forEach {
            val result = it.toSimpleExampleValue()
            if (it.name == null) {
                throw IllegalArgumentException("Example object must have a name ($it)")
            }
            when {
                result.simpleValue != null -> jsonObject.put(it.name, result.simpleValue)
                result.jsonElement != null -> jsonObject.set<JsonNode>(it.name, result.jsonElement)
            }
        }
        return jsonObject
    }

    private fun List<ExampleProperty>.isObjectList(): Boolean =
        this.isNotEmpty() && this.all { it.name == null && it.value == null && (it.objects?.isNotEmpty() ?: false) }

    private fun List<ExampleProperty>.isRawList(): Boolean =
        this.isNotEmpty() && this.all {
            it.name == null
                && (it.objects?.isEmpty() ?: true)
                && (it.value != null || it.raw != null || it.type == ExampleValueType.NULL)
        }

    private fun GeneratorResult.toJsonNode(): JsonNode =
        when {
            this.jsonElement != null -> this.jsonElement
            this.simpleValue != null -> JsonNodeFactory.instance.textNode(this.simpleValue)
            else -> throw IllegalStateException("GeneratorResult must contain either simpleValue or jsonElement")
        }

}
