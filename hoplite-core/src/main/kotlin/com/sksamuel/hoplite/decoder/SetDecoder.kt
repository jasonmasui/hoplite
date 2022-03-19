package com.sksamuel.hoplite.decoder

import com.sksamuel.hoplite.ArrayNode
import com.sksamuel.hoplite.ConfigFailure
import com.sksamuel.hoplite.ConfigResult
import com.sksamuel.hoplite.DecoderContext
import com.sksamuel.hoplite.Node
import com.sksamuel.hoplite.StringNode
import com.sksamuel.hoplite.fp.flatMap
import com.sksamuel.hoplite.fp.invalid
import com.sksamuel.hoplite.fp.sequence
import kotlin.reflect.KType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.full.withNullability

class SetDecoder : NullHandlingDecoder<Set<*>> {

  override fun supports(type: KType): Boolean =
    type.isSubtypeOf(Set::class.starProjectedType) || type.isSubtypeOf(Set::class.starProjectedType.withNullability(true))

  override fun safeDecode(node: Node,
                          type: KType,
                          context: DecoderContext): ConfigResult<Set<*>> {
    require(type.arguments.size == 1)

    val t = type.arguments[0].type!!

    fun <T> decode(node: ArrayNode, decoder: Decoder<T>): ConfigResult<Set<T>> {
      return node.elements.map { decoder.decode(it, t, context) }.sequence()
        .mapInvalid { ConfigFailure.CollectionElementErrors(node, it) }
        .map { it.toSet() }
    }

    fun <T> decode(node: StringNode, decoder: Decoder<T>): ConfigResult<Set<T>> {
      val tokens = node.value.split(",").map {
        StringNode(it.trim(), node.pos, node.path)
      }
      return tokens.map { decoder.decode(it, t, context) }.sequence()
        .mapInvalid { ConfigFailure.CollectionElementErrors(node, it) }
        .map { it.toSet() }
    }

    return context.decoder(t).flatMap { decoder ->
      when (node) {
        is ArrayNode -> decode(node, decoder)
        is StringNode -> decode(node, decoder)
        else -> ConfigFailure.UnsupportedCollectionType(node, "Set").invalid()
      }
    }
  }
}
