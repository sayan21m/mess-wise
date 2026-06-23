package com.srtech.messwise.data_models;

public class CashInModel {
    private String transactionId;
    private String userId;
    private String userName;
    private Object amount; // Using Object to handle both String and Long from Firebase
    private String timestamp;
    private long timestampMillis;
    private String status;
    private String type;
    private long updatedBalance;
    private String messId;

    public CashInModel() {
    }

    public CashInModel(String transactionId, String userId, String userName,
                       Object amount, String timestamp, long timestampMillis, String status) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.userName = userName;
        this.amount = amount;
        this.timestamp = timestamp;
        this.timestampMillis = timestampMillis;
        this.status = status;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getAmount() {
        return amount != null ? String.valueOf(amount) : "0";
    }

    public void setAmount(Object amount) {
        this.amount = amount;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    public void setTimestampMillis(long timestampMillis) {
        this.timestampMillis = timestampMillis;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getUpdatedBalance() {
        return updatedBalance;
    }

    public void setUpdatedBalance(long updatedBalance) {
        this.updatedBalance = updatedBalance;
    }

    public String getMessId() {
        return messId;
    }

    public void setMessId(String messId) {
        this.messId = messId;
    }
}
