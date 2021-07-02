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

@ExperimentalCoroutinesApi
@EnableAutoConfiguration
class ListenTransfersTest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var taskService: TaskService

    @Autowired
    private lateinit var onErc20TransferEventListener1: OnLogEventListener

    @Autowired
    private lateinit var onErc20TransferEventListener2 : OnLogEventListener

    @Autowired
    private lateinit var onOtherEventListener : OnLogEventListener

    @Test
    fun mintAndListen() {
        val contract = TestERC20.deployAndWait(sender, poller, "NAME", "NM").block()!!

        val beneficiary = Address.apply(nextBytes(20))
        val value = BigInteger.valueOf(RandomUtils.nextLong(0, 1000000))
        val receipt = contract.mint(beneficiary, value).execute().verifySuccess()
        assertEquals(contract.balanceOf(beneficiary).call().block()!!, value)

        waitAssert {
            assertThat(mongo.count(Query(), "transfer").block()!!)
                .isEqualTo(1L)

            val event = mongo.findOne<LogEvent>(Query(), "transfer").block()!!
            assertEquals(event.status, LogEventStatus.CONFIRMED)
            assertTrue(event.data is Transfer, "class is ${event.data.javaClass}")
            val t: Transfer = event.data as Transfer
            assertEquals(t.from, Address.apply(ByteArray(20)))
            assertEquals(t.to, beneficiary)
            assertEquals(t.value, value)

            verify(exactly = 1) { onErc20TransferEventListener1.onLogEvent(withArg {
                assertEquals(it.id, event.id)
                assertEquals(it.topic, event.topic)
                assertEquals(it.data, event.data)
            })}
            verify(exactly = 1) { onErc20TransferEventListener2.onLogEvent(withArg {
                assertEquals(it.id, event.id)
                assertEquals(it.topic, event.topic)
                assertEquals(it.data, event.data)
            })}
            verify(exactly = 0) { onOtherEventListener.onLogEvent(any()) }
        }

        mongo.updateFirst<BlockHead>(
            Query(Criteria.where("id").`is`(receipt.blockNumber().toLong())),
            Update().set("status", "PENDING").set("timestamp", (System.currentTimeMillis() / 1000) - 100)
        ).block()

        waitAssert {
            val block = mongo.findById<BlockHead>(receipt.blockNumber().toLong()).block()!!
            assertEquals(block.status, BlockStatus.SUCCESS)
        }
    }

    @Test
    fun confirmPending() {
        clearMocks(onErc20TransferEventListener1, onErc20TransferEventListener2)
        prepareOnTransferLogEventListenerMocks(onErc20TransferEventListener1, onErc20TransferEventListener2)

        val contract = TestERC20.deployAndWait(sender, poller, "NAME", "NM").block()!!
        val value = BigInteger.valueOf(RandomUtils.nextLong(0, 1000000))
        contract.mint(sender.from(), value).execute().verifySuccess()
        assertEquals(contract.balanceOf(sender.from()).call().block()!!, value)

        waitAssert {
            assertEquals(mongo.count(Query(), "transfer").block()!!, 1)
        }

        val beneficiary = Address.apply(nextBytes(20))
        val transferReceipt = contract.transfer(beneficiary, value).execute().verifySuccess()
        val tx = ethereum.ethGetTransactionByHash(transferReceipt.transactionHash()).block()!!.get()

        val saved = mongo.save(LogEvent(Transfer(sender.from(), beneficiary, value), contract.address(), TransferEvent.id(), tx.hash(), sender.from(), tx.nonce().toLong(), LogEventStatus.PENDING, index = 0, minorLogIndex = 0, visible = true), "transfer").block()!!

        waitAssert {
            assertEquals(mongo.count(Query(), "transfer").block()!!, 3L)
            val confirmed = mongo.findOne(Query(Criteria.where("_id").ne(saved.id)), LogEvent::class.java, "transfer").block()!!
            assertEquals(confirmed.status, LogEventStatus.CONFIRMED)
            assertNotNull(confirmed.blockHash)
            assertNotNull(confirmed.blockNumber)
            assertNotNull(confirmed.logIndex)
        }
        val inactive = mongo.findById(saved.id, LogEvent::class.java, "transfer").block()
        assertEquals(inactive.status, LogEventStatus.INACTIVE)

        val block = mongo.findById(tx.blockNumber().toLong(), BlockHead::class.java).block()!!
        assertEquals(block.status, BlockStatus.SUCCESS)
    }

    @Test
    fun revertPending() {
        clearMocks(onErc20TransferEventListener1, onErc20TransferEventListener2)
        prepareOnTransferLogEventListenerMocks(onErc20TransferEventListener1, onErc20TransferEventListener2)

        val contract = TestERC20.deployAndWait(sender, poller, "NAME", "NM").block()!!

        val value = BigInteger.valueOf(RandomUtils.nextLong(0, 1000000))
        contract.mint(sender.from(), value).execute().verifySuccess()
        assertEquals(contract.balanceOf(sender.from()).call().block()!!, value)

        waitAssert {
            assertEquals(mongo.count(Query(), "transfer").block()!!, 1)
        }

        val beneficiary = Address.apply(nextBytes(20))
        val transferReceipt = contract.transfer(beneficiary, value).withGas(BigInteger.valueOf(23000)).execute().verifyError()

        val saved = mongo.save(LogEvent(Transfer(sender.from(), beneficiary, value), contract.address(), TransferEvent.id(), transferReceipt.transactionHash(), sender.from(), 0, LogEventStatus.PENDING, index = 0, minorLogIndex = 0, visible = true), "transfer").block()!!

        waitAssert {
            val read = mongo.findById(saved.id, LogEvent::class.java, "transfer").block()!!
            assertEquals(read.status, LogEventStatus.INACTIVE)
            assertNull(read.blockNumber)
            assertNull(read.logIndex)
            assertEquals(mongo.count(Query(), "transfer").block()!!, 2L)
        }
    }

    @Test
    fun revertCancelled() {
        clearMocks(onErc20TransferEventListener1, onErc20TransferEventListener2)
        prepareOnTransferLogEventListenerMocks(onErc20TransferEventListener1, onErc20TransferEventListener2)

        val contract = TestERC20.deployAndWait(sender, poller, "NAME", "NM").block()!!

        val value = BigInteger.valueOf(RandomUtils.nextLong(0, 1000000))
        contract.mint(sender.from(), value).execute().verifySuccess()
        assertEquals(contract.balanceOf(sender.from()).call().block()!!, value)

        waitAssert {
            assertEquals(mongo.count(Query(), "transfer").block()!!, 1)
        }

        val beneficiary = Address.apply(nextBytes(20))

        val nonce = ethereum.ethGetTransactionCount(sender.from(), "latest").block()!!.toLong()
        val fakeHash = Word(nextBytes(32))
        val saved = mongo.save(LogEvent(Transfer(sender.from(), beneficiary, value), contract.address(), TransferEvent.id(), fakeHash, sender.from(), nonce, LogEventStatus.PENDING, index = 0, minorLogIndex = 0, visible = true), "transfer").block()!!

        TestERC20.deploy(sender, "NAME", "NM").verifySuccess()

        waitAssert {
            val read = mongo.findById(saved.id, LogEvent::class.java, "transfer").block()!!
            assertEquals(read.status, LogEventStatus.DROPPED)
            assertNull(read.blockNumber)
            assertNull(read.logIndex)
        }
    }

    @Test
    fun reindex() {
        clearMocks(onErc20TransferEventListener1, onErc20TransferEventListener2)
        prepareOnTransferLogEventListenerMocks(onErc20TransferEventListener1, onErc20TransferEventListener2)

        val number = ethereum.ethBlockNumber().block()!!.toLong()

        val contract = TestERC20.deployAndWait(sender, poller, "NAME", "NM").block()!!

        val beneficiary = Address.apply(nextBytes(20))
        val value = BigInteger.valueOf(RandomUtils.nextLong(0, 1000000))
        val receipt = contract.mint(beneficiary, value).execute().verifySuccess()
        assertEquals(contract.balanceOf(beneficiary).call().block()!!, value)

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
            assertEquals(block.status, BlockStatus.SUCCESS)
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

    private fun prepareOnTransferLogEventListenerMocks(vararg listeners: OnLogEventListener) {
        listeners.forEach {
            every { it.onLogEvent(any()) } returns Mono.empty()
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(ListenTransfersTest::class.java)
    }
}