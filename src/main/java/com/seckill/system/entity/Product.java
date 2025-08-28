package com.seckill.system.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体类
 * 
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Data
public class Product {
    
    /**
     * 商品ID
     */
    private Long productId;
    
    /**
     * 商品名称
     */
    private String productName;
    
    /**
     * 商品描述
     */
    private String description;
    
    /**
     * 商品价格
     */
    private BigDecimal price;
    
    /**
     * 秒杀价格
     */
    private BigDecimal seckillPrice;
    
    /**
     * 商品图片
     */
    private String imageUrl;
    
    /**
     * 商品状态：0-下架，1-上架，2-秒杀中
     */
    private Integer status;
    
    /**
     * 秒杀开始时间
     */
    private LocalDateTime seckillStartTime;
    
    /**
     * 秒杀结束时间
     */
    private LocalDateTime seckillEndTime;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
