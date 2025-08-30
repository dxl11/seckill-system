package com.seckill.system.controller;

import com.seckill.system.config.RateLimitConfig;
import com.seckill.system.entity.Result;
import com.seckill.system.util.SlidingWindowRateLimiter;
import com.seckill.system.util.TokenBucketRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 限流管理控制器
 * 
 * 支持动态调整限流策略和监控限流状态
 * 
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@RestController
@RequestMapping("/rate-limit")
@Slf4j
public class RateLimitController {

    @Autowired
    private RateLimitConfig rateLimitConfig;

    @Autowired
    private SlidingWindowRateLimiter slidingWindowRateLimiter;

    @Autowired
    private TokenBucketRateLimiter tokenBucketRateLimiter;

    /**
     * 获取当前限流配置
     */
    @GetMapping("/config")
    public Result<Map<String, Object>> getRateLimitConfig() {
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("defaultStrategy", rateLimitConfig.getDefaultStrategy());
            config.put("products", rateLimitConfig.getProducts());
            config.put("users", rateLimitConfig.getUsers());
            config.put("ips", rateLimitConfig.getIps());
            config.put("apis", rateLimitConfig.getApis());
            
            return Result.success(config);
        } catch (Exception e) {
            log.error("获取限流配置异常", e);
            return Result.error("获取限流配置失败");
        }
    }

    /**
     * 更新商品限流策略
     */
    @PostMapping("/config/product/{productId}")
    public Result<String> updateProductStrategy(@PathVariable String productId, 
                                             @RequestBody RateLimitConfig.ProductStrategy strategy) {
        try {
            rateLimitConfig.updateProductStrategy(productId, strategy);
            log.info("更新商品限流策略成功，productId: {}, strategy: {}", productId, strategy);
            return Result.success("更新商品限流策略成功");
        } catch (Exception e) {
            log.error("更新商品限流策略异常，productId: {}", productId, e);
            return Result.error("更新商品限流策略失败");
        }
    }

    /**
     * 更新用户限流策略
     */
    @PostMapping("/config/user/{userId}")
    public Result<String> updateUserStrategy(@PathVariable String userId, 
                                           @RequestBody RateLimitConfig.UserStrategy strategy) {
        try {
            rateLimitConfig.updateUserStrategy(userId, strategy);
            log.info("更新用户限流策略成功，userId: {}, strategy: {}", userId, strategy);
            return Result.success("更新用户限流策略成功");
        } catch (Exception e) {
            log.error("更新用户限流策略异常，userId: {}", userId, e);
            return Result.error("更新用户限流策略失败");
        }
    }

    /**
     * 更新IP限流策略
     */
    @PostMapping("/config/ip/{ip}")
    public Result<String> updateIpStrategy(@PathVariable String ip, 
                                         @RequestBody RateLimitConfig.IpStrategy strategy) {
        try {
            rateLimitConfig.updateIpStrategy(ip, strategy);
            log.info("更新IP限流策略成功，ip: {}, strategy: {}", ip, strategy);
            return Result.success("更新IP限流策略成功");
        } catch (Exception e) {
            log.error("更新IP限流策略异常，ip: {}", ip, e);
            return Result.error("更新IP限流策略失败");
        }
    }

    /**
     * 更新接口限流策略
     */
    @PostMapping("/config/api/{apiPath}")
    public Result<String> updateApiStrategy(@PathVariable String apiPath, 
                                          @RequestBody RateLimitConfig.ApiStrategy strategy) {
        try {
            rateLimitConfig.updateApiStrategy(apiPath, strategy);
            log.info("更新接口限流策略成功，apiPath: {}, strategy: {}", apiPath, strategy);
            return Result.success("更新接口限流策略成功");
        } catch (Exception e) {
            log.error("更新接口限流策略异常，apiPath: {}", apiPath, e);
            return Result.error("更新接口限流策略失败");
        }
    }

    /**
     * 重置所有限流策略
     */
    @PostMapping("/config/reset")
    public Result<String> resetAllStrategies() {
        try {
            rateLimitConfig.resetAllStrategies();
            log.info("重置所有限流策略成功");
            return Result.success("重置所有限流策略成功");
        } catch (Exception e) {
            log.error("重置所有限流策略异常", e);
            return Result.error("重置所有限流策略失败");
        }
    }

    /**
     * 获取滑动窗口限流状态
     */
    @GetMapping("/status/sliding-window/{key}")
    public Result<Map<String, Object>> getSlidingWindowStatus(@PathVariable String key, 
                                                            @RequestParam(defaultValue = "60") int windowSize) {
        try {
            long currentCount = slidingWindowRateLimiter.getCurrentCount(key, windowSize);
            Map<String, Object> status = new HashMap<>();
            status.put("key", key);
            status.put("windowSize", windowSize);
            status.put("currentCount", currentCount);
            status.put("algorithm", "sliding-window");
            
            return Result.success(status);
        } catch (Exception e) {
            log.error("获取滑动窗口限流状态异常，key: {}", key, e);
            return Result.error("获取滑动窗口限流状态失败");
        }
    }

    /**
     * 获取令牌桶限流状态
     */
    @GetMapping("/status/token-bucket/{key}")
    public Result<Map<String, Object>> getTokenBucketStatus(@PathVariable String key) {
        try {
            long currentTokens = tokenBucketRateLimiter.getCurrentTokens(key);
            long lastUpdate = tokenBucketRateLimiter.getLastUpdate(key);
            Map<String, Object> status = new HashMap<>();
            status.put("key", key);
            status.put("currentTokens", currentTokens);
            status.put("lastUpdate", lastUpdate);
            status.put("algorithm", "token-bucket");
            
            return Result.success(status);
        } catch (Exception e) {
            log.error("获取令牌桶限流状态异常，key: {}", key, e);
            return Result.error("获取令牌桶限流状态失败");
        }
    }

    /**
     * 重置限流器
     */
    @PostMapping("/reset/{algorithm}/{key}")
    public Result<String> resetRateLimiter(@PathVariable String algorithm, @PathVariable String key) {
        try {
            switch (algorithm.toLowerCase()) {
                case "sliding-window":
                    slidingWindowRateLimiter.reset(key);
                    break;
                case "token-bucket":
                    tokenBucketRateLimiter.reset(key);
                    break;
                default:
                    return Result.error("不支持的限流算法: " + algorithm);
            }
            
            log.info("重置限流器成功，algorithm: {}, key: {}", algorithm, key);
            return Result.success("重置限流器成功");
        } catch (Exception e) {
            log.error("重置限流器异常，algorithm: {}, key: {}", algorithm, key, e);
            return Result.error("重置限流器失败");
        }
    }

    /**
     * 预填充令牌桶
     */
    @PostMapping("/prefill/token-bucket/{key}")
    public Result<String> prefillTokenBucket(@PathVariable String key, 
                                           @RequestParam(defaultValue = "100") int capacity) {
        try {
            tokenBucketRateLimiter.preFill(key, capacity);
            log.info("预填充令牌桶成功，key: {}, capacity: {}", key, capacity);
            return Result.success("预填充令牌桶成功");
        } catch (Exception e) {
            log.error("预填充令牌桶异常，key: {}, capacity: {}", key, capacity, e);
            return Result.error("预填充令牌桶失败");
        }
    }

    /**
     * 测试限流功能
     */
    @PostMapping("/test/{algorithm}")
    public Result<Map<String, Object>> testRateLimit(@PathVariable String algorithm,
                                                   @RequestParam String key,
                                                   @RequestParam(defaultValue = "60") int windowSize,
                                                   @RequestParam(defaultValue = "100") int limit,
                                                   @RequestParam(defaultValue = "100") int capacity,
                                                   @RequestParam(defaultValue = "10.0") double rate,
                                                   @RequestParam(defaultValue = "1") int tokens) {
        try {
            boolean result = false;
            Map<String, Object> response = new HashMap<>();
            response.put("key", key);
            response.put("algorithm", algorithm);
            
            switch (algorithm.toLowerCase()) {
                case "sliding-window":
                    result = slidingWindowRateLimiter.tryAcquire(key, windowSize, limit);
                    response.put("windowSize", windowSize);
                    response.put("limit", limit);
                    response.put("currentCount", slidingWindowRateLimiter.getCurrentCount(key, windowSize));
                    break;
                    
                case "token-bucket":
                    result = tokenBucketRateLimiter.tryAcquire(key, capacity, rate, tokens);
                    response.put("capacity", capacity);
                    response.put("rate", rate);
                    response.put("tokens", tokens);
                    response.put("currentTokens", tokenBucketRateLimiter.getCurrentTokens(key));
                    break;
                    
                default:
                    return Result.error("不支持的限流算法: " + algorithm);
            }
            
            response.put("allowed", result);
            response.put("timestamp", System.currentTimeMillis());
            
            return Result.success(response);
        } catch (Exception e) {
            log.error("测试限流功能异常，algorithm: {}, key: {}", algorithm, key, e);
            return Result.error("测试限流功能失败");
        }
    }
}
