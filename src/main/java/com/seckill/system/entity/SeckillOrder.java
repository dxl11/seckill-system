package com.seckill.system.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀订单实体类
 * 
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Data
public class SeckillOrder {
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 商品ID
     */
    private Long productId;
    
    /**
     * 商品名称
     */
    private String productName;
    
    /**
     * 秒杀价格
     */
    private BigDecimal seckillPrice;
    
    /**
     * 购买数量
     */
    private Integer quantity;
    
    /**
     * 订单总金额
     */
    private BigDecimal totalAmount;
    
    /**
     * 订单状态：0-待支付，1-已支付，2-已取消，3-已退款
     */
    private Integer status;
    
    /**
     * 支付时间
     */
    private LocalDateTime payTime;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
