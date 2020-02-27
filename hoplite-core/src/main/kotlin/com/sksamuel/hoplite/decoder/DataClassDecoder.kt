package com.sksamuel.hoplite.decoder

import arrow.core.ValidatedNel
import arrow.core.invalid
import arrow.core.valid
import com.sksamuel.hoplite.ConfigFailure
import com.sksamuel.hoplite.ConfigResult
import com.sksamuel.hoplite.DecoderContext
import com.sksamuel.hoplite.Node
import com.sksamuel.hoplite.NullNode
import com.sksamuel.hoplite.ParameterMapper
import com.sksamuel.hoplite.Undefined
import com.sksamuel.hoplite.arrow.flatMap
import com.sksamuel.hoplite.arrow.sequence
import com.sksamuel.hoplite.isDefined
import java.lang.IllegalArgumentException
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType

class DataClassDecoder : Decoder<Any> {

  override fun supports(type: KType): Boolean = type.classifier is KClass<*> && (type.classifier as KClass<*>).isData

  override fun priority(): Int = Int.MIN_VALUE

  override fun decode(node: Node,
                      type: KType,
                      context: DecoderContext): ConfigResult<Any> {

    val klass = type.classifier as KClass<*>
    val constructor = klass.constructors.first()

    // create a map of parameter to value. in the case of defaults, we skip the parameter completely.
    val args: ValidatedNel<ConfigFailure, List<Pair<KParameter, Any?>>> = constructor.parameters.mapNotNull { param ->

      val n = context.paramMappers.fold<ParameterMapper, Node>(Undefined) { n, mapper ->
        if (n.isDefined) n else node.atKey(mapper.map(param))
      }

      val processed = context.preprocessors.fold(n) { acc, pp -> pp.process(acc) }

      when {
        // if we have no value for this parameter at all, and it is optional we can skip it, and
        // kotlin will use the default
        param.isOptional && processed is Undefined -> null
        param.type.isMarkedNullable && processed is Undefined -> Pair(param, null).valid()
        param.type.isMarkedNullable && processed is NullNode -> Pair(param, null).valid()
        processed is Undefined -> ConfigFailure.MissingValue.invalid()
        processed is NullNode -> ConfigFailure.NullValueForNonNullField(node).invalid()
        else -> context.decoder(param)
          .flatMap { it.decode(processed, param.type, context) }
          .map { param to it }
          .leftMap { ConfigFailure.ParamFailure(param, it) }
      }
    }.sequence()

    return args
      .leftMap { ConfigFailure.DataClassFieldErrors(it, type, node.pos) }
      .flatMap { construct(type, constructor, it.toMap()) }
  }

  private fun <A> construct(type: KType,
                            constructor: KFunction<A>,
                            args: Map<KParameter, Any?>): ConfigResult<A> {
    return try {
      constructor.callBy(args).valid()
    } catch (e: IllegalArgumentException) {
      ConfigFailure.InvalidConstructorParameters(type, constructor, args).invalid()
    }
  }
}
