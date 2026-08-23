package com.inventory.service.impl;

import com.inventory.mapper.InventoryNumMapper;
import com.inventory.service.InventoryNumService;
import com.model.bean.Inventory;
import com.model.bean.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InventoryNumServiceImpl implements InventoryNumService {
    @Autowired
    InventoryNumMapper inventoryNumMapper;
    @Override
    public Inventory findNumInventory(Product product) {
        return inventoryNumMapper.findNumInventory(product);
    }

    @Override
    public void addNumInventory(Product product) {
        inventoryNumMapper.addNumInventory(product);
    }
}
