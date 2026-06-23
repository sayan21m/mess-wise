package com.srtech.messwise.fragment_ui.cash_in;

public class Transaction {
    private String transactionId;
    private String userId;
    private String userName;
    private String amount;
    private String timestamp;
    private String status;

    public Transaction() {
        // Required for Firebase
    }

    public Transaction(String transactionId, String userId, String userName, String amount, String timestamp, String status) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.userName = userName;
        this.amount = amount;
        this.timestamp = timestamp;
        this.status = status;
    }

    public String getTransactionId() { return transactionId; }
    public String getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getAmount() { return amount; }
    public String getTimestamp() { return timestamp; }
    public String getStatus() { return status; }
}
