package com.sksamuel.hoplite.converter

import arrow.data.invalidNel
import arrow.data.validNel
import com.sksamuel.hoplite.ConfigResult
import com.sksamuel.hoplite.ConversionFailure
import com.sksamuel.hoplite.Cursor
import kotlin.reflect.KClass
import kotlin.reflect.KType

class EnumConverterProvider : ConverterProvider {
  override fun <T : Any> provide(type: KType): Converter<T>? = when (val c = type.classifier) {
    is KClass<*> -> if (c.java.isEnum) EnumConverter<T>(c as KClass<T>) else null
    else -> null
  }
}

class EnumConverter<T : Any>(private val klass: KClass<T>) : Converter<T> {
  override fun apply(cursor: Cursor): ConfigResult<T> {
    val t = klass.java.enumConstants.find { it.toString().validNel() == cursor.string() }
    return t?.validNel() ?: ConversionFailure(klass, cursor.value()).invalidNel()
  }
}