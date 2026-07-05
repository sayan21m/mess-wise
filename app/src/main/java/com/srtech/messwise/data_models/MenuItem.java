package com.srtech.messwise.data_models;

public class MenuItem {

    private final String id;
    private final String name;
    private final String description;
    private final double cost;

    public MenuItem(String id, String name, String description, double cost) {
        this.id = id;
        this.name = name;
        this.description = description != null ? description : "";
        this.cost = cost;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public double getCost() {
        return cost;
    }
}
