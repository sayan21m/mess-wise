package com.srtech.messwise.data_models;

public class ExpenseModel {
    private String expenseId;
    private String category;
    private double amount;
    private String description;
    private String date;
    private long timestampMillis;
    private String addedBy;
    private String messId;

    public ExpenseModel() {
    }

    public ExpenseModel(String expenseId, String category, double amount, String description, String date, long timestampMillis, String addedBy, String messId) {
        this.expenseId = expenseId;
        this.category = category;
        this.amount = amount;
        this.description = description;
        this.date = date;
        this.timestampMillis = timestampMillis;
        this.addedBy = addedBy;
        this.messId = messId;
    }

    public String getExpenseId() {
        return expenseId;
    }

    public void setExpenseId(String expenseId) {
        this.expenseId = expenseId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    public void setTimestampMillis(long timestampMillis) {
        this.timestampMillis = timestampMillis;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public void setAddedBy(String addedBy) {
        this.addedBy = addedBy;
    }

    public String getMessId() {
        return messId;
    }

    public void setMessId(String messId) {
        this.messId = messId;
    }
}
