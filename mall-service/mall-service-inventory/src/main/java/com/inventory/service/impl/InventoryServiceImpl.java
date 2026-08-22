package com.inventory.service.impl;

import com.inventory.mapper.InventoryMapper;
import com.inventory.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InventoryServiceImpl implements InventoryService {
    @Autowired
    InventoryMapper inventoryMapper;
}
