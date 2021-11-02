package com.rarible.ethereum.listener.log

import com.rarible.contracts.test.erc20.TestERC20
import com.rarible.contracts.test.erc20.TransferEvent
import com.rarible.core.task.Task
import com.rarible.core.task.TaskService
import com.rarible.core.task.TaskStatus
import com.rarible.core.test.wait.BlockingWait.waitAssert
import com.rarible.ethereum.listener.log.domain.BlockHead
import com.rarible.ethereum.listener.log.domain.BlockStatus
import com.rarible.ethereum.listener.log.domain.LogEvent
import com.rarible.ethereum.listener.log.domain.LogEventStatus
import com.rarible.ethereum.listener.log.mock.Transfer
import io.daonomic.rpc.domain.Word
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang3.RandomUtils
import org.apache.commons.lang3.RandomUtils.nextBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.data.mongodb.core.*
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.lt
import reactor.core.publisher.Mono
import scalether.domain.Address
import java.math.BigInteger
import java.time.Instant
import java.time.temporal.ChronoUnit

@IntegrationTest
class ListenTransfersTest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var taskService: TaskService

    @Autowired
    private lateinit var onErc20TransferEventListener1: OnLogEventListener

    @Autowired
    private lateinit var onErc20TransferEventListener2: OnLogEventListener

    @Autowired
    private lateinit var onOtherEventListener: OnLogEventListener

    @Autowired
    private lateinit var pendingLogsCheckJob: PendingLogsCheckJob

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
            assertTrue(event.data is Transfer, "class is ${event.data.javaClass}")
            val t: Transfer = event.data as Transfer
            assertThat(t.from).isEqualTo(Address.apply(ByteArray(20)))
            assertThat(t.to).isEqualTo(beneficiary)
            assertThat(t.value).isEqualTo(value)

            verify(exactly = 1) {
                onErc20TransferEventListener1.onLogEvent(withArg {
                    assertThat(it.id).isEqualTo(event.id)
                    assertThat(it.topic).isEqualTo(event.topic)
                    assertThat(it.data).isEqualTo(event.data)
                })
            }
            verify(exactly = 1) {
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
    fun confirmPending() {
        val contract = TestERC20.deployAndWait(sender, poller, "NAME", "NM").block()!!
        val value = BigInteger.valueOf(RandomUtils.nextLong(0, 1000000))
        contract.mint(sender.from(), value).execute().verifySuccess()
        assertThat(contract.balanceOf(sender.from()).call().block()!!).isEqualTo(value)

        waitAssert {
            assertThat(mongo.count(Query(), "transfer").block()!!).isEqualTo(1)
        }

        val beneficiary = Address.apply(nextBytes(20))
        val transferReceipt = contract.transfer(beneficiary, value).execute().verifySuccess()
        val tx = ethereum.ethGetTransactionByHash(transferReceipt.transactionHash()).block()!!.get()

        val saved = mongo.save(
            LogEvent(
                Transfer(sender.from(), beneficiary, value),
                contract.address(),
                TransferEvent.id(),
                tx.hash(),
                LogEventStatus.PENDING,
                index = 0,
                minorLogIndex = 0,
                visible = true,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            ), "transfer"
        ).block()!!

        waitAssert {
            assertThat(mongo.count(Query(), "transfer").block()!!).isEqualTo(3L)
            val confirmed =
                mongo.findOne(Query(Criteria.where("_id").ne(saved.id)), LogEvent::class.java, "transfer").block()!!
            assertThat(confirmed.status).isEqualTo(LogEventStatus.CONFIRMED)
            assertNotNull(confirmed.blockHash)
            assertNotNull(confirmed.blockNumber)
            assertNotNull(confirmed.logIndex)
        }
        val inactive = mongo.findById(saved.id, LogEvent::class.java, "transfer").block()
        assertThat(inactive.status).isEqualTo(LogEventStatus.INACTIVE)

        val block = mongo.findById(tx.blockNumber().toLong(), BlockHead::class.java).block()!!
        assertThat(block.status).isEqualTo(BlockStatus.SUCCESS)
    }

    @Test
    fun revertPending() {
        val contract = TestERC20.deployAndWait(sender, poller, "NAME", "NM").block()!!

        val value = BigInteger.valueOf(RandomUtils.nextLong(0, 1000000))
        contract.mint(sender.from(), value).execute().verifySuccess()
        assertThat(contract.balanceOf(sender.from()).call().block()!!).isEqualTo(value)

        waitAssert {
            assertThat(mongo.count(Query(), "transfer").block()!!).isEqualTo(1)
        }

        val beneficiary = Address.apply(nextBytes(20))
        val transferReceipt =
            contract.transfer(beneficiary, value).withGas(BigInteger.valueOf(23000)).execute().verifyError()

        val saved = mongo.save(
            LogEvent(
                Transfer(sender.from(), beneficiary, value),
                contract.address(),
                TransferEvent.id(),
                transferReceipt.transactionHash(),
                LogEventStatus.PENDING,
                index = 0,
                minorLogIndex = 0,
                visible = true,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            ), "transfer"
        ).block()!!

        waitAssert {
            val read = mongo.findById(saved.id, LogEvent::class.java, "transfer").block()!!
            assertThat(read.status).isEqualTo(LogEventStatus.INACTIVE)
            assertNull(read.blockNumber)
            assertNull(read.logIndex)
            assertThat(mongo.count(Query(), "transfer").block()!!).isEqualTo(2L)
        }
    }

    @Test
    fun revertCancelled() {
        val contract = TestERC20.deployAndWait(sender, poller, "NAME", "NM").block()!!

        val value = BigInteger.valueOf(RandomUtils.nextLong(0, 1000000))
        contract.mint(sender.from(), value).execute().verifySuccess()
        assertThat(contract.balanceOf(sender.from()).call().block()!!).isEqualTo(value)

        waitAssert {
            assertThat(mongo.count(Query(), "transfer").block()!!).isEqualTo(1)
        }

        val beneficiary = Address.apply(nextBytes(20))

        val fakeHash = Word(nextBytes(32))
        val saved = mongo.save(
            LogEvent(
                Transfer(sender.from(), beneficiary, value),
                contract.address(),
                TransferEvent.id(),
                fakeHash,
                LogEventStatus.PENDING,
                index = 0,
                minorLogIndex = 0,
                visible = true,
                createdAt = Instant.now().minus(10, ChronoUnit.MINUTES),
                updatedAt = Instant.now().minus(10, ChronoUnit.MINUTES)
            ), "transfer"
        ).block()!!

        TestERC20.deploy(sender, "NAME", "NM").verifySuccess()
        pendingLogsCheckJob.job()

        waitAssert {
            val read = mongo.findById(saved.id, LogEvent::class.java, "transfer").block()!!
            assertThat(read.status).isEqualTo(LogEventStatus.DROPPED)
            assertNull(read.blockNumber)
            assertNull(read.logIndex)
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
        val transfers = mongo.findAllAndRemove(Query(), LogEvent::class.java, "transfer").collectList().block()!!
        val newTask = Task(
            type = ReindexTopicTaskHandler.TOPIC,
            param = TransferEvent.id().toString(),
            lastStatus = TaskStatus.NONE,
            state = number + 1,
            running = false
        )
        logger.info("saving $newTask")
        mongo.save(newTask).block()

        taskService.readAndRun()

        waitAssert {
            val tasks = runBlocking { taskService.findTasks(ReindexTopicTaskHandler.TOPIC).toList() }
            assertThat(tasks)
                .hasSize(1)
            assertThat(tasks.first())
                .hasFieldOrPropertyWithValue(Task::lastStatus.name, TaskStatus.COMPLETED)
        }

        assertThat(mongo.find<LogEvent>(Query(), "transfer").collectList().block())
            .hasSize(transfers.size)
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(ListenTransfersTest::class.java)
    }
}
