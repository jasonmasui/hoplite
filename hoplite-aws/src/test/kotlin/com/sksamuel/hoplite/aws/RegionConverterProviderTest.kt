package com.sksamuel.hoplite.aws

import arrow.data.invalidNel
import arrow.data.valid
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.sksamuel.hoplite.ConfigFailure
import com.sksamuel.hoplite.LongValue
import com.sksamuel.hoplite.Pos
import com.sksamuel.hoplite.StringValue
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class RegionConverterProviderTest : StringSpec() {
  init {
    "region converter" {
      RegionConverterProvider().converter().convert(StringValue("us-east-1", Pos.NoPos)) shouldBe Region.getRegion(Regions.US_EAST_1).valid()
      RegionConverterProvider().converter().convert(StringValue("qwewqe-1", Pos.NoPos)) shouldBe ConfigFailure("Cannot create region from qwewqe-1").invalidNel()
    }
  }
}