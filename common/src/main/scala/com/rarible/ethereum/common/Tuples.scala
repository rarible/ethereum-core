package com.rarible.ethereum.common

import scalether.abi.tuple.Tuple5Type
import scalether.abi.{AddressType, Bytes32Type, Uint256Type}

//noinspection TypeAnnotation
object Tuples {
  val eip712DomainHashType =
    Tuple5Type(Bytes32Type, Bytes32Type, Bytes32Type, Uint256Type, AddressType)
}
