package com.user.fein.fall;



import com.model.bean.Product;
import com.model.bean.Result;
import com.user.fein.InventoryFeignClient;
import org.springframework.stereotype.Component;

@Component
public class InventoryFeignClientFallback implements InventoryFeignClient {
    @Override
    public Result addNumInventory(Product product) {
        return Result.error("添加库存失败");
    }
}
