package com.user.fein;




import com.model.bean.Product;
import com.model.bean.Result;
import com.user.fein.fall.InventoryFeignClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(value = "mall-service-inventory",fallback = InventoryFeignClientFallback.class)
public interface InventoryFeignClient {

    @PostMapping("/addNumInventory")
    Result addNumInventory(Product product);
}
