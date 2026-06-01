package com.example.fintrack.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface TransactionDao {

    // IGNORE silently drops the duplicate to keep the slate clean
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertTransaction(Transaction transaction);

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    List<Transaction> getAllTransactions();

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'debit' AND timestamp >= :startTime")
    double getDebitSumSince(long startTime);
}