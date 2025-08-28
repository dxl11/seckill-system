package com.seckill.system.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 商品库存实体类
 * 
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Data
public class ProductStock {
    
    /**
     * 库存ID
     */
    private Long stockId;
    
    /**
     * 商品ID
     */
    private Long productId;
    
    /**
     * 总库存数量
     */
    private Integer totalStock;
    
    /**
     * 可用库存数量
     */
    private Integer availableStock;
    
    /**
     * 已锁定库存数量
     */
    private Integer lockedStock;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
