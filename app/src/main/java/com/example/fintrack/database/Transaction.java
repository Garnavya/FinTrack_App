package com.example.fintrack.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "transactions")
public class Transaction {

    // CRUCIAL FIX to stop duplicates ---
    @PrimaryKey
    public long timestamp;

    public double amount;
    public String merchant;
    public String type;

    public Transaction(double amount, String merchant, long timestamp, String type) {
        this.amount = amount;
        this.merchant = merchant;
        this.timestamp = timestamp;
        this.type = type;
    }
}