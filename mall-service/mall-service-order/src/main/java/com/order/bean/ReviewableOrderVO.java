package com.order.bean;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 「我的哪张订单还能评这个商品」——商品详情页写评价弹框的订单选择器数据源。
 *
 * <p>刻意做成**列表**而不是单个订单：本功能的规则是「每个订单每件商品一条」，
 * 买家可能分两次买过同一个商品，那就该有两次评价机会。
 * 弹框若静默取第一条，第二次评价实际上就做不到了。
 *
 * <p>只包含**已完成且尚未评价**的订单，所以列表为空即「当前没有可评价的订单」。
 */
@Data
public class ReviewableOrderVO {

    private Long orderId;

    private String orderNo;

    /** 下单时的商品名快照，让买家分辨是哪一次购买 */
    private String productName;

    private String productImage;

    /** 该订单的完成时间，作为选择器的辅助信息 */
    private LocalDateTime completeTime;
}
