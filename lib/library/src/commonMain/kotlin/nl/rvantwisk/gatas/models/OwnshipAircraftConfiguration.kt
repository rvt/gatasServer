package io.github.kotlin.fibonacci.nl.rvantwisk.gatas.models

import kotlinx.serialization.Serializable

@Serializable
data class OwnshipAircraftConfiguration (
  val gatasId: UInt,
  val options: UInt,
  val icaoAddress: UInt,
  val newIcaoAddress: UInt?,
  val icaoAddressList: List<UInt>,
  val gatasIp: UInt,
)



