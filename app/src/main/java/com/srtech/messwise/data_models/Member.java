package com.srtech.messwise.data_models;

public class Member {
    private String uid, mail, name;
    private Boolean is_admin;
    private Integer meal_count;
    private String role; // "Admin", "Meal Manager", "Member", or custom

    public Member() {}

    public Member(String uid, String mail, String name, Boolean is_admin, Integer meal_count, String role) {
        this.uid = uid;
        this.mail = mail;
        this.name = name;
        this.is_admin = is_admin;
        this.meal_count = meal_count;
        this.role = role;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getMail() {
        return mail;
    }

    public void setMail(String mail) {
        this.mail = mail;
    }

    public Boolean getIs_admin() {
        return is_admin;
    }

    public void setIs_admin(Boolean is_admin) {
        this.is_admin = is_admin;
    }

    public Integer getMeal_count() {
        return meal_count;
    }

    public void setMeal_count(Integer meal_count) {
        this.meal_count = meal_count;
    }

    public String getRole() {
        return role != null ? role : (is_admin != null && is_admin ? "Admin" : "Member");
    }

    public void setRole(String role) {
        this.role = role;
    }

    @Override
    public String toString() {
        return name;
    }
}
