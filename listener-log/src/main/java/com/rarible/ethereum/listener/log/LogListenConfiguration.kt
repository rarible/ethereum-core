package com.rarible.ethereum.listener.log

import com.rarible.core.task.EnableRaribleTask
import com.rarible.ethereum.block.BlockListenService
import com.rarible.ethereum.block.BlockState
import com.rarible.ethereum.listener.log.block.EthereumBlockchain
import com.rarible.ethereum.listener.log.block.SimpleBlock
import com.rarible.ethereum.listener.log.persist.PersistConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@ComponentScan
@EnableScheduling
@EnableRaribleTask
class LogListenConfiguration {
    @Bean
    fun blockListenService(blockState: BlockState<SimpleBlock>, blockchain: EthereumBlockchain) =
        BlockListenService(blockState, blockchain)
}