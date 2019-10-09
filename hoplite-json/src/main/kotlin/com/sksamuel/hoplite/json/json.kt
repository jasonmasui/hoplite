package com.sksamuel.hoplite.json

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonLocation
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.sksamuel.hoplite.BooleanValue
import com.sksamuel.hoplite.DoubleValue
import com.sksamuel.hoplite.ListValue
import com.sksamuel.hoplite.LongValue
import com.sksamuel.hoplite.MapValue
import com.sksamuel.hoplite.NullValue
import com.sksamuel.hoplite.Pos
import com.sksamuel.hoplite.StringValue
import com.sksamuel.hoplite.Value
import com.sksamuel.hoplite.parsers.Parser
import java.io.InputStream
import java.lang.UnsupportedOperationException

class JsonParser : Parser {

  private val jsonFactory = JsonFactory()

  override fun load(input: InputStream, source: String): Value {
    val parser = jsonFactory.createParser(input).configure(JsonParser.Feature.ALLOW_COMMENTS, true)
    parser.nextToken()
    return TokenProduction(parser, "<root>", source)
  }

  override fun defaultFileExtensions(): List<String> = listOf("json")
}

@Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
object TokenProduction {
  operator fun invoke(parser: JsonParser, path: String, source: String): Value {
    return when (parser.currentToken()) {
      JsonToken.NOT_AVAILABLE -> throw UnsupportedOperationException("Invalid json at ${parser.currentLocation}")
      JsonToken.START_OBJECT -> ObjectProduction(parser, path, source)
      JsonToken.START_ARRAY -> ArrayProduction(parser, path, source)
      JsonToken.VALUE_STRING -> StringValue(parser.valueAsString, parser.currentLocation.toPos(source), path)
      JsonToken.VALUE_NUMBER_INT -> LongValue(parser.valueAsLong, parser.currentLocation.toPos(source), path)
      JsonToken.VALUE_NUMBER_FLOAT -> DoubleValue(parser.valueAsDouble,
        parser.currentLocation.toPos(source),
        path)
      JsonToken.VALUE_TRUE -> BooleanValue(true, parser.currentLocation.toPos(source), path)
      JsonToken.VALUE_FALSE -> BooleanValue(false, parser.currentLocation.toPos(source), path)
      JsonToken.VALUE_NULL -> NullValue(parser.currentLocation.toPos(source), path)
      else -> throw UnsupportedOperationException("Invalid json at ${parser.currentLocation}; encountered unexpected token ${parser.currentToken}")
    }
  }
}

fun JsonLocation.toPos(source: String): Pos = Pos.LineColPos(this.lineNr, this.columnNr, source)

object ObjectProduction {
  operator fun invoke(parser: JsonParser, path: String, source: String): Value {
    require(parser.currentToken == JsonToken.START_OBJECT)
    val loc = parser.currentLocation
    val obj = mutableMapOf<String, Value>()
    while (parser.nextToken() != JsonToken.END_OBJECT) {
      require(parser.currentToken() == JsonToken.FIELD_NAME)
      val fieldName = parser.currentName()
      parser.nextToken()
      val value = TokenProduction(parser, "$path.$fieldName", source)
      obj[fieldName] = value
    }
    return MapValue(obj, loc.toPos(source), path)
  }
}

object ArrayProduction {
  operator fun invoke(parser: JsonParser, path: String, source: String): Value {
    require(parser.currentToken == JsonToken.START_ARRAY)
    val loc = parser.currentLocation
    val list = mutableListOf<Value>()
    var index = 0
    while (parser.nextToken() != JsonToken.END_ARRAY) {
      val value = TokenProduction(parser, "$path[$index]", source)
      list.add(value)
      index++
    }
    require(parser.currentToken == JsonToken.END_ARRAY)
    return ListValue(list.toList(), loc.toPos(source), path)
  }
}
