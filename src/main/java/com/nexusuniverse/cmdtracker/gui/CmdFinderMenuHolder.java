package com.nexusuniverse.cmdtracker.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class CmdFinderMenuHolder implements InventoryHolder {

    public enum Type { FILTER_LIST, ENTRY_PAGE }

    private final Type type;
    private final String filterKey;
    private final int page;
    private Inventory inventory;

    public CmdFinderMenuHolder(Type type, String filterKey, int page) {
        this.type = type;
        this.filterKey = filterKey;
        this.page = page;
    }

    public Type type() {
        return type;
    }

    public String filterKey() {
        return filterKey;
    }

    public int page() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
