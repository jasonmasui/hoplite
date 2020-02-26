package com.sksamuel.hoplite.decoder

import arrow.core.invalid
import arrow.core.valid
import com.sksamuel.hoplite.ConfigFailure
import com.sksamuel.hoplite.ConfigResult
import java.lang.RuntimeException
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType

/**
 * An immutable registry for providing instances of a [Decoder] for a given type.
 */
interface DecoderRegistry {

  /**
   * Returns a [Decoder] for the given kclass otherwise returns an invalid ConfigFailure.
   */
  fun <T : Any> decoder(t: KClass<T>): ConfigResult<Decoder<T>>

  fun decoder(type: KType): ConfigResult<Decoder<*>>

  fun register(decoder: Decoder<*>): DecoderRegistry

  val size: Int

  companion object {
    val zero: DecoderRegistry = DefaultDecoderRegistry(
      emptyList())
  }
}

inline fun <reified T : Any> DecoderRegistry.decoder(): ConfigResult<Decoder<T>> = decoder(T::class)

@Suppress("UNCHECKED_CAST")
class DefaultDecoderRegistry(private val decoders: List<Decoder<*>>) : DecoderRegistry {

  override fun <T : Any> decoder(t: KClass<T>): ConfigResult<Decoder<T>> {
    return decoder(t.createType()).map { it as Decoder<T> }
  }

  override fun decoder(type: KType): ConfigResult<Decoder<*>> {
    return when (type.classifier) {
      is KClass<*> -> {
        val decoders = decoders.filter { it.supports(type) }
        when {
          decoders.isEmpty() && (type.classifier as KClass<*>).isData -> ConfigFailure.NoDataClassDecoder.invalid()
          decoders.isEmpty() -> ConfigFailure.NoSuchDecoder(type).invalid()
          else -> decoders.maxBy { it.priority() }!!.valid()
        }
      }
      else -> throw RuntimeException("Asked to decode $type")
    }
  }

  override fun register(decoder: Decoder<*>): DecoderRegistry = DefaultDecoderRegistry(decoders + decoder)

  override val size: Int = decoders.size
}

fun defaultDecoderRegistry(): DecoderRegistry {
  return ServiceLoader.load(Decoder::class.java).toList()
    .fold(DecoderRegistry.zero) { registry, decoder -> registry.register(decoder) }
}
