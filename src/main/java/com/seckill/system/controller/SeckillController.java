package com.seckill.system.controller;

import com.seckill.system.aspect.RateLimit;
import com.seckill.system.entity.Result;
import com.seckill.system.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 秒杀控制器
 * 
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@RestController
@RequestMapping("/api/seckill")
@Slf4j
public class SeckillController {
    
    @Autowired
    private SeckillService seckillService;
    
    /**
     * 执行秒杀
     * 
     * @param userId 用户ID
     * @param productId 商品ID
     * @param quantity 购买数量
     * @return 秒杀结果
     */
    @PostMapping("/do")
    @RateLimit(key = "'api:seckill:' + #productId", qps = 50.0, block = false, timeout = 2000)
    public Result<String> doSeckill(@RequestParam Long userId,
                                   @RequestParam Long productId,
                                   @RequestParam Integer quantity) {
        log.info("收到秒杀请求，userId: {}, productId: {}, quantity: {}", userId, productId, quantity);
        return seckillService.doSeckill(userId, productId, quantity);
    }
    
    /**
     * 查询秒杀结果
     * 
     * @param userId 用户ID
     * @param productId 商品ID
     * @return 秒杀结果
     */
    @GetMapping("/result")
    public Result<String> getSeckillResult(@RequestParam Long userId,
                                          @RequestParam Long productId) {
        log.info("查询秒杀结果，userId: {}, productId: {}", userId, productId);
        return seckillService.getSeckillResult(userId, productId);
    }
    
    /**
     * 预热商品库存
     * 
     * @param productId 商品ID
     * @return 预热结果
     */
    @PostMapping("/preload")
    public Result<String> preloadStock(@RequestParam Long productId) {
        log.info("预热商品库存，productId: {}", productId);
        return seckillService.preloadStock(productId);
    }
    
    /**
     * 查询商品库存
     * 
     * @param productId 商品ID
     * @return 库存信息
     */
    @GetMapping("/stock")
    public Result<Integer> getStock(@RequestParam Long productId) {
        log.info("查询商品库存，productId: {}", productId);
        return seckillService.getStock(productId);
    }
    
    /**
     * 健康检查接口
     * 
     * @return 健康状态
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("秒杀系统运行正常");
    }
}
