package com.inventory.service;

import com.model.bean.Inventory;
import com.model.bean.Product;

public interface InventoryNumService {

    Inventory findNumInventory(Product product);

    void addNumInventory(Product product);
}
