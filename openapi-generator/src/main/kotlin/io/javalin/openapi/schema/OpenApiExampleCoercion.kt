package io.javalin.openapi.schema

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import io.javalin.openapi.experimental.processor.shared.createArrayNode
import java.math.BigDecimal
import java.math.BigInteger

private val COMPOSITION_KEYS = listOf("allOf", "anyOf", "oneOf")
private val jsonMapper = ObjectMapper()

internal fun ObjectNode.coerceTypedExamplesInPlace(): ObjectNode = apply {
    val components = path("components").path("schemas") as? ObjectNode
    components?.properties()?.forEach { (_, schema) ->
        if (schema is ObjectNode) {
            coerceSchemaExamples(schema)
        }
    }

    val paths = path("paths") as? ObjectNode ?: return@apply
    paths.properties().forEach { (_, pathItem) ->
        val pathObject = pathItem as? ObjectNode ?: return@forEach
        pathObject.properties().forEach { (_, operationNode) ->
            val operation = operationNode as? ObjectNode ?: return@forEach

            (operation.get("parameters") as? ArrayNode)?.forEach { parameterNode ->
                val parameter = parameterNode as? ObjectNode ?: return@forEach
                coerceHolderExample(parameter, parameter.get("schema"))
            }

            (operation.get("requestBody") as? ObjectNode)
                ?.let { processContentExamples(it.get("content") as? ObjectNode) }

            (operation.get("responses") as? ObjectNode)?.properties()?.forEach { (_, responseNode) ->
                val response = responseNode as? ObjectNode ?: return@forEach

                (response.get("headers") as? ObjectNode)?.properties()?.forEach { (_, headerNode) ->
                    val header = headerNode as? ObjectNode ?: return@forEach
                    coerceHolderExample(header, header.get("schema"))
                }

                processContentExamples(response.get("content") as? ObjectNode)
            }
        }
    }
}

private fun ObjectNode.processContentExamples(content: ObjectNode?) {
    content?.properties()?.forEach { (_, mediaTypeNode) ->
        val mediaType = mediaTypeNode as? ObjectNode ?: return@forEach
        coerceHolderExample(mediaType, mediaType.get("schema"))
    }
}

private fun ObjectNode.coerceSchemaExamples(schema: ObjectNode) {
    schema.get("example")?.let { schema.set<JsonNode>("example", coerceExampleValue(it, schema)) }
    schema.get("default")?.let { schema.set<JsonNode>("default", coerceExampleValue(it, schema)) }

    (schema.get("properties") as? ObjectNode)?.properties()?.forEach { (_, propertyNode) ->
        val property = propertyNode as? ObjectNode ?: return@forEach
        coerceSchemaExamples(property)
    }

    (schema.get("items") as? ObjectNode)?.let { coerceSchemaExamples(it) }
    (schema.get("additionalProperties") as? ObjectNode)?.let { coerceSchemaExamples(it) }

    COMPOSITION_KEYS.forEach { key ->
        (schema.get(key) as? ArrayNode)?.forEach { branchNode ->
            val branch = branchNode as? ObjectNode ?: return@forEach
            coerceSchemaExamples(branch)
        }
    }
}

private fun ObjectNode.coerceHolderExample(holder: ObjectNode, schema: JsonNode?) {
    (schema as? ObjectNode)?.let { coerceSchemaExamples(it) }
    val example = holder.get("example") ?: return
    holder.set<JsonNode>("example", coerceExampleValue(example, schema))
}

private fun ObjectNode.coerceExampleValue(example: JsonNode, schemaNode: JsonNode?): JsonNode {
    if (schemaNode == null || schemaNode.isMissingNode || schemaNode.isNull) {
        return example
    }

    return when {
        example.isTextual -> coerceTextExample(example.asText(), schemaNode) ?: example
        example.isArray -> {
            val itemsSchema = findItemsSchema(schemaNode) ?: return example
            val array = createArrayNode()
            example.forEach { element -> array.add(coerceExampleValue(element, itemsSchema)) }
            array
        }
        example.isObject -> {
            val objectExample = (example as ObjectNode).deepCopy()
            objectExample.properties().forEach { (name, value) ->
                val propertySchema = findPropertySchema(schemaNode, name) ?: return@forEach
                objectExample.set<JsonNode>(name, coerceExampleValue(value, propertySchema))
            }
            objectExample
        }
        else -> example
    }
}

private fun ObjectNode.coerceTextExample(value: String, schemaNode: JsonNode): JsonNode? {
    val schemaType = findSchemaType(schemaNode) ?: return null
    val normalized = value.trim()

    return when (schemaType) {
        "boolean" ->
            when {
                normalized.equals("true", ignoreCase = true) -> JsonNodeFactory.instance.booleanNode(true)
                normalized.equals("false", ignoreCase = true) -> JsonNodeFactory.instance.booleanNode(false)
                else -> null
            }
        "integer" -> normalized.toBigIntegerOrNull()?.let { JsonNodeFactory.instance.numberNode(it) }
        "number" -> normalized.toBigDecimalOrNull()?.let { JsonNodeFactory.instance.numberNode(it) }
        "object", "array" ->
            tryParseJsonNode(normalized)?.let { parsed -> coerceExampleValue(parsed, schemaNode) }
        else -> null
    }
}

private fun ObjectNode.findSchemaType(schemaNode: JsonNode, seenRefs: Set<String> = emptySet()): String? {
    val resolved = resolveSchema(schemaNode, seenRefs)
    val directType = extractSchemaType(resolved.get("type"))
    if (directType != null) {
        return directType
    }

    COMPOSITION_KEYS.forEach { key ->
        val branchTypes =
            (resolved.get(key) as? ArrayNode)
                ?.mapNotNull { branch -> findSchemaType(branch, seenRefs) }
                ?.filterNot { it == "null" }
                ?.distinct()
                .orEmpty()

        if (branchTypes.size == 1) {
            return branchTypes.first()
        }
    }

    return null
}

private fun extractSchemaType(typeNode: JsonNode?): String? =
    when {
        typeNode == null || typeNode.isMissingNode || typeNode.isNull -> null
        typeNode.isTextual ->
            typeNode.asText().takeIf { it in setOf("boolean", "integer", "number", "object", "array") }
        typeNode.isArray -> {
            val nonNullTypes = typeNode.filter { it.isTextual && it.asText() != "null" }.map { it.asText() }.distinct()
            nonNullTypes.singleOrNull()?.takeIf { it in setOf("boolean", "integer", "number", "object", "array") }
        }
        else -> null
    }

private fun ObjectNode.findPropertySchema(
    schemaNode: JsonNode,
    propertyName: String,
    seenRefs: Set<String> = emptySet(),
): JsonNode? {
    val resolved = resolveSchema(schemaNode, seenRefs)

    (resolved.get("properties") as? ObjectNode)?.get(propertyName)?.let { return it }

    COMPOSITION_KEYS.forEach { key ->
        (resolved.get(key) as? ArrayNode)?.forEach { branch ->
            val propertySchema = findPropertySchema(branch, propertyName, seenRefs)
            if (propertySchema != null) {
                return propertySchema
            }
        }
    }

    return (resolved.get("additionalProperties") as? ObjectNode)
}

private fun ObjectNode.findItemsSchema(schemaNode: JsonNode, seenRefs: Set<String> = emptySet()): JsonNode? {
    val resolved = resolveSchema(schemaNode, seenRefs)
    (resolved.get("items") as? ObjectNode)?.let { return it }

    COMPOSITION_KEYS.forEach { key ->
        (resolved.get(key) as? ArrayNode)?.forEach { branch ->
            val itemsSchema = findItemsSchema(branch, seenRefs)
            if (itemsSchema != null) {
                return itemsSchema
            }
        }
    }

    return null
}

private fun ObjectNode.resolveSchema(schemaNode: JsonNode, seenRefs: Set<String>): JsonNode {
    val schemaObject = schemaNode as? ObjectNode ?: return schemaNode
    val ref = schemaObject.get($$"$ref")?.asText() ?: return schemaNode
    if (!ref.startsWith("#/") || ref in seenRefs) {
        return schemaNode
    }

    val resolved = at(ref.removePrefix("#"))
    if (resolved.isMissingNode || resolved.isNull) {
        return schemaNode
    }

    return resolveSchema(resolved, seenRefs + ref)
}

private fun String.toBigIntegerOrNull(): BigInteger? =
    try {
        BigInteger(this)
    } catch (_: NumberFormatException) {
        null
    }

private fun String.toBigDecimalOrNull(): BigDecimal? =
    try {
        BigDecimal(this)
    } catch (_: NumberFormatException) {
        null
    }

private fun tryParseJsonNode(value: String): JsonNode? =
    try {
        jsonMapper.readTree(value)
    } catch (_: Exception) {
        null
    }
