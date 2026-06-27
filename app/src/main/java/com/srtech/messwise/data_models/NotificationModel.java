package com.srtech.messwise.data_models;

public class NotificationModel {
    private String id;
    private String title;
    private String message;
    private String type; // "MEAL_SUMMARY", "DUE_REMINDER", "EXPENSE", etc.
    private String targetUid; // UID of the user who should see this (null for global)
    private long timestamp;
    private boolean isRead;

    public NotificationModel() {}

    public NotificationModel(String id, String title, String message, String type, long timestamp) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.type = type;
        this.timestamp = timestamp;
        this.isRead = false;
    }

    public NotificationModel(String id, String title, String message, String type, String targetUid, long timestamp) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.type = type;
        this.targetUid = targetUid;
        this.timestamp = timestamp;
        this.isRead = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTargetUid() { return targetUid; }
    public void setTargetUid(String targetUid) { this.targetUid = targetUid; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
}
