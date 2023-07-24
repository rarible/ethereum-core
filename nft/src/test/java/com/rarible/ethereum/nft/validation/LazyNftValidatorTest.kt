package com.rarible.ethereum.nft.validation

import com.rarible.ethereum.nft.domain.EIP712DomainNftFactory
import com.rarible.ethereum.nft.model.LazyERC1155
import com.rarible.ethereum.nft.model.LazyERC721
import com.rarible.ethereum.nft.model.LazyNft
import com.rarible.ethereum.nft.model.Part
import com.rarible.ethereum.sign.domain.EIP712Domain
import com.rarible.ethereum.sign.service.ERC1271SignService
import io.daonomic.rpc.domain.Binary
import io.daonomic.rpc.domain.Word
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang3.RandomUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import scalether.domain.Address
import scalether.domain.AddressFactory
import java.math.BigInteger
import java.util.Arrays
import java.util.UUID
import java.util.stream.Stream

internal class LazyNftValidatorTest {
    private val signService = mockk<ERC1271SignService>()
    private val nftEip712Domain = mockk<EIP712Domain>()
    private val domainNftFactory = mockk<EIP712DomainNftFactory>()

    private val lazyAssetValidator = LazyNftValidator(signService, domainNftFactory)

    @BeforeEach
    fun setup() {
        clearMocks(signService, nftEip712Domain, domainNftFactory)

        every { domainNftFactory.createErc1155Domain(any()) } returns nftEip712Domain
        every { domainNftFactory.createErc721Domain(any()) } returns nftEip712Domain
    }

    companion object {
        private fun creators(vararg creator: Address): List<Part> = creators(creator.toList())

        private fun creators(creators: List<Address>): List<Part> {
            val every = 10000 / creators.size
            return creators.map { Part(it, every) }
        }

        @JvmStatic
        fun validLazyNft(): Stream<LazyNft> {
            val assetType1 = createLazyErc721()
                .copy(
                    creators = creators(AddressFactory.create(), AddressFactory.create()),
                    signatures = listOfNotNull(createSignature(), createSignature())
                )

            val assetType2 = createLazyErc1155()
                .copy(
                    creators = creators(AddressFactory.create(), AddressFactory.create()),
                    signatures = listOfNotNull(createSignature(), createSignature())
                )

            return Arrays.stream(arrayOf(assetType1, assetType2))
        }

        private fun createSignature(): Binary = Binary.apply(RandomUtils.nextBytes(65))
        private fun createWord(): Word = Word.apply(RandomUtils.nextBytes(32))

        fun createLazyErc721(): LazyERC721 {
            return LazyERC721(
                token = AddressFactory.create(),
                tokenId = BigInteger.valueOf((1L..100L).random()),
                uri = UUID.randomUUID().toString(),
                creators = creators((1L..100L).map { AddressFactory.create() }),
                signatures = (1L..100L).map { Binary.apply(ByteArray(65)) },
                royalties = emptyList()
            )
        }

        fun createLazyErc1155(): LazyERC1155 {
            return LazyERC1155(
                token = AddressFactory.create(),
                tokenId = BigInteger.valueOf((1L..100L).random()),
                supply = BigInteger.valueOf((1L..100L).random()),
                uri = UUID.randomUUID().toString(),
                creators = creators((1L..100L).map { AddressFactory.create() }),
                signatures = (1L..100L).map { Binary.apply(ByteArray(65)) },
                royalties = emptyList()
            )
        }
    }

    @ParameterizedTest
    @MethodSource("validLazyNft")
    fun `should validate lazy asset`(lazyNft: LazyNft) = runBlocking {
        val checkedHash = createWord()

        val token = lazyNft.token
        val creators = lazyNft.creators
        val signatures = lazyNft.signatures

        every { nftEip712Domain.hashToSign(eq(lazyNft.hash())) } returns checkedHash

        coEvery { signService.isSigner(eq(creators[0].account), eq(checkedHash), eq(signatures[0])) } returns true
        coEvery { signService.isSigner(eq(creators[1].account), eq(checkedHash), eq(signatures[1])) } returns true

        val result = lazyAssetValidator.validate(lazyNft)
        Assertions.assertEquals(ValidationResult.Valid, result)

        if (lazyNft is LazyERC1155) {
            verify(exactly = 1) { domainNftFactory.createErc1155Domain(eq(token)) }
            verify(exactly = 0) { domainNftFactory.createErc721Domain(any()) }
        }
        if (lazyNft is LazyERC721) {
            verify(exactly = 1) { domainNftFactory.createErc721Domain(eq(token)) }
            verify(exactly = 0) { domainNftFactory.createErc1155Domain(any()) }
        }
        coVerify { signService.isSigner(eq(creators[0].account), any<Word>(), eq(signatures[0])) }
        coVerify { signService.isSigner(eq(creators[1].account), any<Word>(), eq(signatures[1])) }
    }

    @Test
    fun `should validate matching creator and signature size`() = runBlocking {
        val assetType = createLazyErc721()
            .copy(
                creators = creators(AddressFactory.create()),
                signatures = listOfNotNull(createSignature(), createSignature())
            )

        val result = lazyAssetValidator.validate(assetType)
        Assertions.assertEquals(ValidationResult.InvalidCreatorAndSignatureSize, result)

        verify(exactly = 0) { domainNftFactory.createErc1155Domain(any()) }
        verify(exactly = 0) { domainNftFactory.createErc721Domain(any()) }
        coVerify(exactly = 0) { signService.isSigner(any(), any<Word>(), any()) }
        coVerify(exactly = 0) { signService.isSigner(any(), any<Word>(), any()) }
    }

    @Test
    fun `creators must be unique`() = runBlocking<Unit> {
        val creator = AddressFactory.create()

        val assetType = createLazyErc1155()
            .copy(
                creators = creators(creator, creator),
                signatures = listOfNotNull(createSignature(), createSignature())
            )

        val result = lazyAssetValidator.validate(assetType)
        assertThat(result).isEqualTo(ValidationResult.NotUniqCreators)

        verify(exactly = 0) { domainNftFactory.createErc1155Domain(any()) }
        verify(exactly = 0) { domainNftFactory.createErc721Domain(any()) }
        coVerify(exactly = 0) { signService.isSigner(any(), any<Word>(), any()) }
        coVerify(exactly = 0) { signService.isSigner(any(), any<Word>(), any()) }
    }

    @Test
    fun `should return all creators with invalid signature`() = runBlocking<Unit> {
        val creator1 = AddressFactory.create()
        val creator2 = AddressFactory.create()
        val creator3 = AddressFactory.create()
        val creator4 = AddressFactory.create()

        val assetType = createLazyErc1155()
            .copy(
                creators = creators(creator1, creator2, creator3, creator4),
                signatures = listOfNotNull(createSignature(), createSignature(), createSignature(), createSignature())
            )

        val checkedHash = createWord()

        every { nftEip712Domain.hashToSign(any()) } returns checkedHash

        coEvery { signService.isSigner(eq(creator1), any<Word>(), any()) } returns true
        coEvery { signService.isSigner(eq(creator2), any<Word>(), any()) } returns false
        coEvery { signService.isSigner(eq(creator3), any<Word>(), any()) } returns true
        coEvery { signService.isSigner(eq(creator4), any<Word>(), any()) } returns false

        val result = lazyAssetValidator.validate(assetType)
        assertThat(result).isInstanceOf(ValidationResult.InvalidCreatorSignature::class.java)
        assertThat((result as ValidationResult.InvalidCreatorSignature).creators).containsExactlyInAnyOrder(
            creator2,
            creator4
        )
    }
}
