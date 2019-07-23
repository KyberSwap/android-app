package com.kyberswap.android.data.db

import androidx.room.*
import com.kyberswap.android.domain.model.Transaction
import io.reactivex.Flowable

/**
 * Data Access Object for the transactions table.
 */
@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE hash = :hash")
    fun getTransactionByHash(hash: String): Transaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTransaction(transaction: Transaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTransactionBatch(transactions: List<Transaction>)

    @Update
    fun updateTransaction(transaction: Transaction)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateTransactionList(transactions: List<Transaction>)

    @Update
    fun updateTransactionBatch(transactions: List<Transaction>)

    @Query("DELETE FROM transactions")
    fun deleteAllTransactions()

    @Query("SELECT * FROM transactions ORDER BY timeStamp DESC LIMIT 1")
    fun getLatestTransaction(): Transaction?

    @Delete
    fun delete(model: Transaction)

    @get:Query("SELECT * FROM transactions")
    val all: Flowable<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE walletAddress = :walletAddress AND transactionStatus != :pending")
    fun getCompletedTransactions(
        walletAddress: String,
        pending: String = Transaction.PENDING_TRANSACTION_STATUS
    ): Flowable<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE hash = :hash AND transactionStatus = :status")
    fun findTransaction(hash: String, status: String): Transaction?

    @Query("SELECT * FROM transactions WHERE walletAddress = :walletAddress AND transactionStatus = :status")
    fun getTransactionByStatus(walletAddress: String, status: String): Flowable<List<Transaction>>

}

