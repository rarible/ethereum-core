package com.rarible.ethereum.common

import scalether.abi.tuple.{Tuple3Type, Tuple5Type, Tuple6Type}
import scalether.abi.{AddressType, Bytes32Type, Uint256Type}

//noinspection TypeAnnotation
object Tuples {
  val eip712DomainHashType =
    Tuple5Type(Bytes32Type, Bytes32Type, Bytes32Type, Uint256Type, AddressType)

  val lazy721HashType =
    Tuple5Type(Bytes32Type, Uint256Type, Bytes32Type, Bytes32Type, Bytes32Type)

  val lazy1155HashType =
    Tuple6Type(Bytes32Type, Uint256Type, Uint256Type, Bytes32Type, Bytes32Type, Bytes32Type)

  val partHashType =
    Tuple3Type(Bytes32Type, AddressType, Uint256Type)
}
