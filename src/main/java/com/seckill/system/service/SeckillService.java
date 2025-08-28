package com.seckill.system.service;

import com.seckill.system.entity.Result;

/**
 * 秒杀服务接口
 * 
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
public interface SeckillService {
    
    /**
     * 执行秒杀
     * 
     * @param userId 用户ID
     * @param productId 商品ID
     * @param quantity 购买数量
     * @return 秒杀结果
     */
    Result<String> doSeckill(Long userId, Long productId, Integer quantity);
    
    /**
     * 查询秒杀结果
     * 
     * @param userId 用户ID
     * @param productId 商品ID
     * @return 秒杀结果
     */
    Result<String> getSeckillResult(Long userId, Long productId);
    
    /**
     * 预热商品库存到Redis
     * 
     * @param productId 商品ID
     * @return 预热结果
     */
    Result<String> preloadStock(Long productId);
    
    /**
     * 查询商品库存
     * 
     * @param productId 商品ID
     * @return 库存信息
     */
    Result<Integer> getStock(Long productId);
}
