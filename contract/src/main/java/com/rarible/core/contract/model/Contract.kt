package com.rarible.core.contract.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import scalether.domain.Address

enum class ContractType {
    ERC20_TOKEN,
    ERC721_TOKEN,
    ERC1155_TOKEN
}

@JsonTypeInfo(property = "type", use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes(
    JsonSubTypes.Type(name = "ERC20_TOKEN", value = Erc20Token::class),
    JsonSubTypes.Type(name = "ERC721_TOKEN", value = Erc721Token::class),
    JsonSubTypes.Type(name = "ERC1155_TOKEN", value = Erc1155Token::class)
)
@Document(collection = "contract")
sealed class Contract(var type: ContractType) {
    @get:Id
    abstract val id: Address
    abstract val name: String?
    abstract val symbol: String?
}

data class Erc20Token(
    override val id: Address,
    override val name: String?,
    override val symbol: String?,
    val decimals: Int?
) : Contract(type = ContractType.ERC20_TOKEN)

data class Erc721Token(
    override val id: Address,
    override val name: String?,
    override val symbol: String?
) : Contract(type = ContractType.ERC721_TOKEN)

data class Erc1155Token(
    override val id: Address,
    override val name: String?,
    override val symbol: String?
) : Contract(type = ContractType.ERC1155_TOKEN)
