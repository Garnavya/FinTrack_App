package com.example.fintrack.database;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

// The composite index guarantees we never save the exact same transaction twice
@Entity(tableName = "transactions",
        indices = {@Index(value = {"timestamp", "amount", "merchant"}, unique = true)})
public class Transaction {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public double amount;
    public String merchant;
    public long timestamp;
    public String type; // "debit" or "credit"

    public Transaction(double amount, String merchant, long timestamp, String type) {
        this.amount = amount;
        this.merchant = merchant;
        this.timestamp = timestamp;
        this.type = type;
    }
}
