package com.findev.fintrack.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.findev.fintrack.data.local.FinTrackDatabase
import com.findev.fintrack.data.local.entity.LoanPrepaymentEntity
import com.findev.fintrack.data.local.entity.LoanRateEntity
import com.findev.fintrack.data.local.entity.LoanType
import com.findev.fintrack.data.local.entity.PrepaymentMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LoanRepositoryTest {

    private lateinit var db: FinTrackDatabase
    private lateinit var repository: LoanRepository

    /** 15.01.2027 */
    private val start = LocalDate.of(2027, 1, 15).toEpochDay()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            FinTrackDatabase::class.java,
        ).build()
        repository = LoanRepository(db.loanDao(), db.loanRateDao(), db.loanPrepaymentDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun createAnnuity(): String = repository.create(
        name = "Ипотека",
        type = LoanType.ANNUITY,
        principalMinor = 1_000_000_00,
        rateBp = 1200,
        startDateEpochDay = start,
        termMonths = 12,
        paymentDay = 15,
        upfrontFeeMinor = 0,
        monthlyFeeMinor = 0,
    )

    @Test
    fun storedLoanProducesTheSameScheduleAsTheEngine() = runBlocking {
        val id = createAnnuity()

        val withSchedule = repository.observeWithSchedule(id).first()!!

        assertEquals(12, withSchedule.schedule.size)
        // The textbook figure: 1 000 000 руб. под 12% на 12 мес = 88 848,79 руб.
        assertEquals(88_848_79L, withSchedule.schedule.first().paymentMinor)
        assertEquals(0L, withSchedule.schedule.last().balanceAfterMinor)
        assertEquals(1_000_000_00L, withSchedule.summary.principalMinor)
    }

    @Test
    fun rateHistoryFromTheDatabaseReachesTheEngine() = runBlocking {
        val id = createAnnuity()
        val plain = repository.observeWithSchedule(id).first()!!.summary.overpaymentMinor

        db.loanRateDao().insert(
            LoanRateEntity(
                id = UUID.randomUUID().toString(),
                loanId = id,
                rateBp = 2000,
                effectiveFromEpochDay = LocalDate.of(2027, 7, 15).toEpochDay(),
                updatedAt = System.currentTimeMillis(),
            ),
        )

        val afterRise = repository.observeWithSchedule(id).first()!!.summary.overpaymentMinor
        assertTrue("a rate rise must cost more: $afterRise vs $plain", afterRise > plain)
    }

    @Test
    fun prepaymentsFromTheDatabaseReachTheEngine() = runBlocking {
        val id = createAnnuity()
        val plain = repository.observeWithSchedule(id).first()!!

        db.loanPrepaymentDao().insert(
            LoanPrepaymentEntity(
                id = UUID.randomUUID().toString(),
                loanId = id,
                amountMinor = 300_000_00,
                dateEpochDay = LocalDate.of(2027, 4, 15).toEpochDay(),
                mode = PrepaymentMode.REDUCE_TERM,
                updatedAt = System.currentTimeMillis(),
            ),
        )

        val prepaid = repository.observeWithSchedule(id).first()!!
        assertTrue(prepaid.schedule.size < plain.schedule.size)
        assertTrue(prepaid.summary.closingDate < plain.summary.closingDate)
        assertTrue(prepaid.summary.overpaymentMinor < plain.summary.overpaymentMinor)
        // Still the same debt - only cheaper and shorter.
        assertEquals(1_000_000_00L, prepaid.summary.principalMinor)
    }

    @Test
    fun instalmentFeesSurviveTheRoundTrip() = runBlocking {
        val id = repository.create(
            name = "iPhone",
            type = LoanType.INSTALLMENT,
            principalMinor = 60_000_00,
            rateBp = 0,
            startDateEpochDay = start,
            termMonths = 6,
            paymentDay = 15,
            upfrontFeeMinor = 2_000_00,
            monthlyFeeMinor = 500_00,
        )

        val withSchedule = repository.observeWithSchedule(id).first()!!

        assertEquals(10_500_00L, withSchedule.schedule.first().paymentMinor)
        // 6 x 500 + 2 000 upfront: the up-front fee is only visible in the summary.
        assertEquals(5_000_00L, withSchedule.summary.totalFeesMinor)
        assertEquals(0L, withSchedule.summary.totalInterestMinor)
        assertTrue(withSchedule.schedule.all { it.feeMinor == 500_00L })
    }

    @Test
    fun editingALoanReshapesItsSchedule() = runBlocking {
        val id = createAnnuity()

        repository.update(
            id = id,
            name = "Ипотека",
            type = LoanType.DIFFERENTIATED,
            principalMinor = 1_000_000_00,
            rateBp = 1200,
            startDateEpochDay = start,
            termMonths = 12,
            paymentDay = 15,
            upfrontFeeMinor = 0,
            monthlyFeeMinor = 0,
        )

        val schedule = repository.observeWithSchedule(id).first()!!.schedule
        // Differentiated means equal principal slices, unlike the annuity it replaced -
        // bar the last one, which absorbs the rounding remainder of 100 000 000 / 12.
        assertEquals(1, schedule.dropLast(1).map { it.principalMinor }.distinct().size)
        assertEquals(1_000_000_00L, schedule.sumOf { it.principalMinor })
    }

    @Test
    fun deletedLoanDisappears() = runBlocking {
        val id = createAnnuity()
        assertEquals(1, repository.observeAll().first().size)

        repository.delete(id)

        assertTrue(repository.observeAll().first().isEmpty())
        assertEquals(null, repository.observeWithSchedule(id).first())
    }

    @Test
    fun loanTypesMapAcrossTheModuleBoundary() = runBlocking {
        // The engine cannot see the entities, so both sides have their own LoanType.
        // This checks the hand-written mapping really lines them up.
        LoanType.entries.forEach { type ->
            val id = repository.create(
                name = "$type",
                type = type,
                principalMinor = 120_000_00,
                rateBp = if (type == LoanType.INSTALLMENT) 0 else 1200,
                startDateEpochDay = start,
                termMonths = 12,
                paymentDay = 15,
                upfrontFeeMinor = 0,
                monthlyFeeMinor = 0,
            )

            val schedule = repository.observeWithSchedule(id).first()!!.schedule
            assertEquals("$type", 12, schedule.size)
            assertEquals("$type", 120_000_00L, schedule.sumOf { it.principalMinor })
            repository.delete(id)
        }
    }
}
