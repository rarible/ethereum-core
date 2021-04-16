package com.rarible.ethereum.nft.model

import com.rarible.ethereum.common.Tuples
import com.rarible.ethereum.common.keccak256
import com.rarible.ethereum.nft.misc.hash
import com.rarible.rpc.domain.Binary
import com.rarible.rpc.domain.Word
import scala.Tuple5
import scala.Tuple6
import scalether.domain.Address
import java.math.BigInteger

sealed class LazyNft {
    abstract val token: Address
    abstract val tokenId: BigInteger
    abstract val uri:String
    abstract val creators: List<Part>
    abstract val signatures: List<Binary>
    abstract val royalties: List<Part>

    abstract fun hash(): Word
}

data class LazyERC721(
    override val token: Address,
    override val tokenId: BigInteger,
    override val uri: String,
    override val creators: List<Part>,
    override val signatures: List<Binary>,
    override val royalties: List<Part>
) : LazyNft() {

    init {
        assert(creators.fold(0) { acc, it -> acc + it.value } == 10000) {
            "sum of creators parts should be 10000"
        }
    }

    override fun hash(): Word = keccak256(Tuples.lazy721HashType().encode(Tuple5(
            TYPE_HASH.bytes(),
            tokenId,
            keccak256(uri).bytes(),
            creators.map { it.hash() }.hash(),
            royalties.map { it.hash() }.hash()
    )))

    companion object {
        private val TYPE_HASH: Word = keccak256("Mint721(uint256 tokenId,string tokenURI,Part[] creators,Part[] royalties)Part(address account,uint96 value)")
    }
}

data class LazyERC1155(
    override val token: Address,
    override val tokenId: BigInteger,
    override val uri: String,
    val supply: BigInteger,
    override val creators: List<Part>,
    override val signatures: List<Binary>,
    override val royalties: List<Part>
) : LazyNft() {

    init {
        assert(creators.fold(0) { acc, it -> acc + it.value } == 10000) {
            "sum of creators parts should be 10000"
        }
    }

    override fun hash(): Word = keccak256(Tuples.lazy1155HashType().encode(Tuple6(
            TYPE_HASH.bytes(),
            tokenId,
            supply,
            keccak256(uri).bytes(),
            creators.map { it.hash() }.hash(),
            royalties.map { it.hash() }.hash()
    )))

    companion object {
        private val TYPE_HASH: Word = keccak256("Mint1155(uint256 tokenId,uint256 supply,string tokenURI,Part[] creators,Part[] royalties)Part(address account,uint96 value)")
    }
}