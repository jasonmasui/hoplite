package com.sksamuel.hoplite.decoder

import arrow.data.Valid
import arrow.data.Validated
import arrow.data.invalid
import arrow.data.valid
import com.sksamuel.hoplite.ConfigFailure
import com.sksamuel.hoplite.ConfigResult
import com.sksamuel.hoplite.Value
import com.sksamuel.hoplite.NullValue
import com.sksamuel.hoplite.UndefinedValue
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType

inline fun <reified T : Any> DecoderRegistry.decoder(): ConfigResult<Decoder<T>> = decoder(T::class)

/**
 * An immutable registry for providing instances of a [Decoder] for a given type.
 */
interface DecoderRegistry {

  fun <T : Any> decoder(t: KClass<T>): ConfigResult<Decoder<T>>
  fun decoder(type: KType): ConfigResult<Decoder<*>>
  fun register(decoder: Decoder<*>): DecoderRegistry

  companion object {
    val zero: DecoderRegistry = DefaultDecoderRegistry(emptyList())
  }
}

@Suppress("UNCHECKED_CAST")
class DefaultDecoderRegistry(private val decoders: List<Decoder<*>>) : DecoderRegistry {

  override fun <T : Any> decoder(t: KClass<T>): ConfigResult<Decoder<T>> {
    return decoder(t.createType()).map { it as Decoder<T> }
  }

  override fun decoder(type: KType): ConfigResult<Decoder<*>> =
    decoders.find { it.supports(type) }?.valid() ?: ConfigFailure.NoSuchDecoder(type).invalid()

  override fun register(decoder: Decoder<*>): DecoderRegistry = DefaultDecoderRegistry(decoders + decoder)
}

fun defaultDecoderRegistry(): DecoderRegistry {
  return ServiceLoader.load(Decoder::class.java).toList()
    .fold(DecoderRegistry.zero) { registry, decoder -> registry.register(decoder) }
}

/**
 * A typeclass for decoding a [Value] into a specified type.
 */
interface Decoder<T> {

  fun supports(type: KType): Boolean

  /**
   * Attempts to decode the given node into an instance of the given [KType].
   *
   * @param value contains the value for the current dot path
   * @param type the concrete type required by the caller
   * @param registry used to lookup decoders for types that have nested types
   */
  fun decode(value: Value,
             type: KType,
             registry: DecoderRegistry): ConfigResult<T>
}

inline fun <T, reified U> Decoder<T>.map(crossinline f: (T) -> U): Decoder<U> = object : Decoder<U> {
  override fun supports(type: KType): Boolean = U::class.createType() == type
  override fun decode(value: Value, type: KType, registry: DecoderRegistry): ConfigResult<U> {
    return this@map.decode(value, type, registry).map { f(it) }
  }
}

/**
 * Extends [Decoder] for types which will not handle nulls.
 */
@Suppress("UNCHECKED_CAST")
interface NonNullableDecoder<T> : Decoder<T> {

  private fun decode(node: NullValue, type: KType): Validated<ConfigFailure, *> {
    return if (type.isMarkedNullable) Valid(null) else
      ConfigFailure.NullValueForNonNullField(node).invalid()
  }

  private fun decode(type: KType): Validated<ConfigFailure, *> {
    return if (type.isMarkedNullable) Valid(null) else
      ConfigFailure.MissingValue.invalid()
  }

  override fun decode(value: Value,
                      type: KType,
                      registry: DecoderRegistry): Validated<ConfigFailure, T> =
    when (value) {
      is UndefinedValue -> decode(type).map { it as T }
      is NullValue -> decode(value, type).map { it as T }
      else -> safeDecode(value, type, registry)
    }

  /**
   * Attempts to decode the given node into an instance of the given [KType].
   *
   * @param value contains the value for the current dot path
   * @param type the concrete type required by the caller
   * @param registry used to lookup decoders for types that have nested types
   */
  fun safeDecode(value: Value,
                 type: KType,
                 registry: DecoderRegistry): ConfigResult<T>
}
