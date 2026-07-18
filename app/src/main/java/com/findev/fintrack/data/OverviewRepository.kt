package com.findev.fintrack.data

import com.findev.fintrack.data.local.AccountBalance
import com.findev.fintrack.data.local.MonthTotals
import com.findev.fintrack.data.local.dao.OverviewDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class OverviewRepository @Inject constructor(
    private val overviewDao: OverviewDao,
) {
    fun observeTotalBalance(): Flow<Long> = overviewDao.observeTotalBalance()

    fun observeAccountBalances(): Flow<List<AccountBalance>> = overviewDao.observeAccountBalances()

    fun observeTotals(fromEpochDay: Long, toEpochDay: Long): Flow<MonthTotals> =
        overviewDao.observeTotals(fromEpochDay, toEpochDay)
}
