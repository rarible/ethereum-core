package com.rarible.ethereum.listener.log

import com.rarible.contracts.test.erc20.TestERC20
import com.rarible.contracts.test.erc20.TransferEvent
import com.rarible.core.task.Task
import com.rarible.core.task.TaskService
import com.rarible.core.task.TaskStatus
import com.rarible.core.test.data.randomAddress
import com.rarible.core.test.data.randomLong
import com.rarible.core.test.wait.BlockingWait.waitAssert
import com.rarible.core.test.wait.BlockingWait.waitFor
import com.rarible.ethereum.listener.log.domain.BlockHead
import com.rarible.ethereum.listener.log.domain.BlockStatus
import com.rarible.ethereum.listener.log.domain.LogEvent
import com.rarible.ethereum.listener.log.domain.LogEventStatus
import com.rarible.ethereum.listener.log.domain.NewBlockEvent
import com.rarible.ethereum.listener.log.mock.Transfer
import com.rarible.ethereum.listener.log.mock.randomWordd
import io.daonomic.rpc.domain.Binary
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang3.RandomUtils
import org.apache.commons.lang3.RandomUtils.nextBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.findAll
import org.springframework.data.mongodb.core.findAllAndRemove
import org.springframework.data.mongodb.core.findById
import org.springframework.data.mongodb.core.findOne
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.lt
import org.springframework.data.mongodb.core.updateFirst
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import scalether.domain.Address
import scalether.domain.response.Block
import scalether.domain.response.Transaction
import scalether.java.Lists
import java.math.BigInteger
import java.time.Instant

@FlowPreview
@ExperimentalCoroutinesApi
@IntegrationTest
class ListenTransfersTest : AbstractIntegrationTest() {
    @Autowired
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    private lateinit var taskService: TaskService

    @Autowired
    private lateinit var onErc20TransferEventListener1: OnLogEventListener

    @Autowired
    private lateinit var onErc20TransferEventListener2: OnLogEventListener

    @Autowired
    private lateinit var onOtherEventListener: OnLogEventListener

    @Autowired
    private lateinit var logListenService: LogListenService

    @BeforeEach
    fun prepareMocks() {
        clearMocks(onErc20TransferEventListener1, onErc20TransferEventListener2)
        listOf(onErc20TransferEventListener1, onErc20TransferEventListener2).forEach {
            every { it.onLogEvent(any()) } returns Mono.empty()
        }
    }

    @Test
    fun mintAndListen() {
        val contract = TestERC20.deployAndWait(sender, poller, "NAME", "NM").block()!!

        val beneficiary = Address.apply(nextBytes(20))
        val value = BigInteger.valueOf(RandomUtils.nextLong(0, 1000000))
        val receipt = contract.mint(beneficiary, value).execute().verifySuccess()
        assertThat(contract.balanceOf(beneficiary).call().block()!!).isEqualTo(value)

        waitAssert {
            assertThat(mongo.count(Query(), "transfer").block()!!)
                .isEqualTo(1L)

            val event = mongo.findOne<LogEvent>(Query(), "transfer").block()!!
            assertThat(event.status).isEqualTo(LogEventStatus.CONFIRMED)
            assertThat(event.blockTimestamp).isGreaterThanOrEqualTo(receipt.getTimestamp().epochSecond)
            assertTrue(event.data is Transfer, "class is ${event.data.javaClass}")
            val t: Transfer = event.data as Transfer
            assertThat(t.from).isEqualTo(Address.apply(ByteArray(20)))
            assertThat(t.to).isEqualTo(beneficiary)
            assertThat(t.value).isEqualTo(value)

            // Flaky test
            verify(atLeast = 1) {
                onErc20TransferEventListener1.onLogEvent(withArg {
                    assertThat(it.id).isEqualTo(event.id)
                    assertThat(it.topic).isEqualTo(event.topic)
                    assertThat(it.data).isEqualTo(event.data)
                })
            }
            verify(atLeast = 1) {
                onErc20TransferEventListener2.onLogEvent(withArg {
                    assertThat(it.id).isEqualTo(event.id)
                    assertThat(it.topic).isEqualTo(event.topic)
                    assertThat(it.data).isEqualTo(event.data)
                })
            }
            verify(exactly = 0) { onOtherEventListener.onLogEvent(any()) }
        }

        mongo.updateFirst<BlockHead>(
            Query(Criteria.where("id").`is`(receipt.blockNumber().toLong())),
            Update().set("status", "PENDING").set("timestamp", (System.currentTimeMillis() / 1000) - 100)
        ).block()

        waitAssert {
            val block = mongo.findById<BlockHead>(receipt.blockNumber().toLong()).block()!!
            assertThat(block.status).isEqualTo(BlockStatus.SUCCESS)
        }
    }

    @Test
    fun revertConfirmed() {
        val contract = TestERC20.deployAndWait(sender, poller, "NAME", "NM").block()!!
        val value = randomLong(1, 100000).toBigInteger()
        val transactionReceipt = contract.mint(sender.from(), value).execute().verifySuccess()
        val mintLogEvent = waitFor {
            mongo.findAll<LogEvent>("transfer").collectList().block()!!.single()
        }!!
        val blockNumber = transactionReceipt.blockNumber()
        val blockHash = transactionReceipt.blockHash()

        val revertedBlockHash = randomWordd()
        @Suppress("ReactiveStreamsUnusedPublisher")
        every { ethereum.ethGetFullBlockByHash(revertedBlockHash) } returns Block(
            blockNumber,
            revertedBlockHash,
            randomWordd(),
            "",
            "",
            "",
            "",
            "",
            randomAddress(),
            BigInteger.ZERO,
            BigInteger.ZERO,
            Binary.empty(),
            BigInteger.ZERO,
            BigInteger.ZERO,
            BigInteger.ZERO,
            Lists.toScala(emptyList<Transaction>()),
            Instant.EPOCH.epochSecond.toBigInteger()
        ).toMono()

        val replacingBlock = NewBlockEvent(
            number = blockNumber.toLong(),
            hash = revertedBlockHash,
            timestamp = Instant.EPOCH.epochSecond,
            reverted = blockHash
        )
        logListenService.onBlock(replacingBlock).block()
        waitAssert {
            val updatedLogEvent = mongo.findById(mintLogEvent.id, LogEvent::class.java, "transfer").block()!!
            assertThat(updatedLogEvent.status).isEqualTo(LogEventStatus.REVERTED)
        }
    }

    @Test
    fun reindex() {
        val number = ethereum.ethBlockNumber().block()!!.toLong()

        val contract = TestERC20.deployAndWait(sender, poller, "NAME", "NM").block()!!

        val beneficiary = Address.apply(nextBytes(20))
        val value = BigInteger.valueOf(RandomUtils.nextLong(0, 1000000))
        val receipt = contract.mint(beneficiary, value).execute().verifySuccess()
        assertThat(contract.balanceOf(beneficiary).call().block()!!).isEqualTo(value)

        waitAssert {
            assertThat(mongo.count(Query(), "transfer").block()!!)
                .isEqualTo(1L)
        }

        mongo.updateFirst<BlockHead>(
            Query(Criteria.where("id").`is`(receipt.blockNumber().toLong())),
            Update().set("status", "PENDING").set("timestamp", (System.currentTimeMillis() / 1000) - 100)
        ).block()

        waitAssert {
            val block = mongo.findById<BlockHead>(receipt.blockNumber().toLong()).block()!!
            assertThat(block.status).isEqualTo(BlockStatus.SUCCESS)
        }

        val numberEnd = ethereum.ethBlockNumber().block()!!.toLong()
        mongo.findAllAndRemove<Task>(Query()).then().block()
        mongo.findAllAndRemove<BlockHead>(Query(BlockHead::id lt numberEnd)).then().block()
        val transfers = mongo.findAllAndRemove(Query(), LogEvent::class.java, "transfer")
            .filter {
                it.transactionHash == receipt.transactionHash()
            }
            .collectList().block()!!
        val newTask = Task(
            type = ReindexTopicTaskHandler.TOPIC,
            param = TransferEvent.id().toString(),
            lastStatus = TaskStatus.NONE,
            state = number + 1,
            running = false
        )
        logger.info("saving $newTask")
        mongo.save(newTask).block()

        taskService.runTasks()

        waitAssert {
            val tasks = runBlocking { taskService.findTasks(ReindexTopicTaskHandler.TOPIC).toList() }
            assertThat(tasks)
                .hasSize(1)
            assertThat(tasks.first())
                .hasFieldOrPropertyWithValue(Task::lastStatus.name, TaskStatus.COMPLETED)
        }

        assertThat(mongo.find<LogEvent>(Query(), "transfer")
            .filter {
                it.transactionHash == receipt.transactionHash()
            }
            .collectList().block())
            .hasSize(transfers.size)
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(ListenTransfersTest::class.java)
    }
}
