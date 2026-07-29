package com.srtech.messwise.data_models;

/**
 * Cash-in ledger entry.
 * Amount is stored as Object so Firebase can deserialize historic String values
 * as well as numeric doubles. Always write numbers via {@link #setAmountValue(double)}.
 */
public class CashInModel {
    private String transactionId;
    private String userId;
    private String userName;
    private Object amount;
    private String timestamp;
    private long timestampMillis;
    private String status;
    private String type;
    private Object updatedBalance;
    private String messId;
    /** Month key whose monthly_balance this tx affects (settlement may differ from timestamp month). */
    private String balanceMonthKey;

    public CashInModel() {
    }

    public CashInModel(String transactionId, String userId, String userName,
                       double amount, String timestamp, long timestampMillis, String status) {
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

    public Object getAmount() {
        return amount;
    }

    public void setAmount(Object amount) {
        this.amount = amount;
    }

    public void setAmountValue(double value) {
        this.amount = value;
    }

    public double getAmountValue() {
        return parseNumber(amount);
    }

    public String getAmountText() {
        double value = getAmountValue();
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
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

    public Object getUpdatedBalance() {
        return updatedBalance;
    }

    public void setUpdatedBalance(Object updatedBalance) {
        this.updatedBalance = updatedBalance;
    }

    public double getUpdatedBalanceValue() {
        return parseNumber(updatedBalance);
    }

    public String getMessId() {
        return messId;
    }

    public void setMessId(String messId) {
        this.messId = messId;
    }

    public String getBalanceMonthKey() {
        return balanceMonthKey;
    }

    public void setBalanceMonthKey(String balanceMonthKey) {
        this.balanceMonthKey = balanceMonthKey;
    }

    /** Month used for monthly_balance adjustments (settlement month or timestamp month). */
    public String resolveBalanceMonthKey() {
        if (balanceMonthKey != null && !balanceMonthKey.trim().isEmpty()) {
            return balanceMonthKey.trim();
        }
        return com.srtech.messwise.utils.DateUtils.formatMonthKey(timestampMillis);
    }

    private static double parseNumber(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return 0;
        }
    }
}
