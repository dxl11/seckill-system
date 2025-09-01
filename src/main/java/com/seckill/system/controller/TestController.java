package com.seckill.system.controller;

import com.seckill.system.entity.Result;
import com.seckill.system.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试控制器
 * 
 * 用于验证短期优化的各项功能
 * 
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@RestController
@RequestMapping("/test")
@Slf4j
public class TestController {

    @Autowired
    private RedissonDistributedLockUtil redissonLockUtil;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private IdempotencyUtil idempotencyUtil;

    @Autowired
    private StockConsistencyUtil stockConsistencyUtil;

    @Autowired
    private SlidingWindowRateLimiter slidingWindowRateLimiter;

    @Autowired
    private TokenBucketRateLimiter tokenBucketRateLimiter;

    /**
     * 测试Redisson分布式锁
     */
    @GetMapping("/redisson-lock")
    public Result<Map<String, Object>> testRedissonLock(@RequestParam String lockKey) {
        try {
            Map<String, Object> result = new HashMap<>();
            
            // 测试获取锁
            boolean locked = redissonLockUtil.tryLockWithWatchdog(lockKey, 5);
            result.put("lockAcquired", locked);
            
            if (locked) {
                // 测试锁状态
                result.put("isLocked", redissonLockUtil.isLocked(lockKey));
                result.put("isHeldByCurrentThread", redissonLockUtil.isHeldByCurrentThread(lockKey));
                result.put("remainTime", redissonLockUtil.getRemainTime(lockKey));
                
                // 释放锁
                redissonLockUtil.unlock(lockKey);
                result.put("lockReleased", true);
            }
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("测试Redisson分布式锁异常", e);
            return Result.error("测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试JWT功能
     */
    @GetMapping("/jwt")
    public Result<Map<String, Object>> testJwt() {
        try {
            Map<String, Object> result = new HashMap<>();
            
            // 生成Token
            String token = jwtUtil.generateToken(1001L, "testuser");
            result.put("generatedToken", token);
            
            // 验证Token
            boolean valid = jwtUtil.validateToken(token);
            result.put("tokenValid", valid);
            
            // 获取用户信息
            Long userId = jwtUtil.getUserIdFromToken(token);
            String username = jwtUtil.getUsernameFromToken(token);
            result.put("userId", userId);
            result.put("username", username);
            
            // 检查过期时间
            long remainingTime = jwtUtil.getRemainingTime(token);
            result.put("remainingTime", remainingTime);
            
            // 刷新Token
            String newToken = jwtUtil.refreshToken(token);
            result.put("refreshedToken", newToken);
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("测试JWT功能异常", e);
            return Result.error("测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试幂等性功能
     */
    @GetMapping("/idempotency")
    public Result<Map<String, Object>> testIdempotency() {
        try {
            Map<String, Object> result = new HashMap<>();
            
            // 生成幂等性Token
            String token = idempotencyUtil.generateIdempotencyToken("test");
            result.put("generatedToken", token);
            
            // 验证Token
            boolean valid = idempotencyUtil.validateIdempotencyToken(token);
            result.put("tokenValid", valid);
            
            // 使用Token
            boolean used = idempotencyUtil.useIdempotencyToken(token);
            result.put("tokenUsed", used);
            
            // 再次使用Token（应该失败）
            boolean usedAgain = idempotencyUtil.useIdempotencyToken(token);
            result.put("tokenUsedAgain", usedAgain);
            
            // 生成业务幂等性key
            String businessKey = idempotencyUtil.generateSeckillIdempotencyKey(1001L, 1L);
            result.put("businessKey", businessKey);
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("测试幂等性功能异常", e);
            return Result.error("测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试库存一致性功能
     */
    @GetMapping("/stock-consistency")
    public Result<Map<String, Object>> testStockConsistency(@RequestParam Long productId) {
        try {
            Map<String, Object> result = new HashMap<>();
            
            // 获取库存一致性状态
            String status = stockConsistencyUtil.getStockConsistencyStatus(productId);
            result.put("status", status);
            
            // 同步库存到Redis
            boolean synced = stockConsistencyUtil.syncStockToRedis(productId, 100);
            result.put("synced", synced);
            
            // 预扣减库存
            Long newStock = stockConsistencyUtil.preDeductStock(productId, 10);
            result.put("preDeductStock", newStock);
            
            if (newStock >= 0) {
                // 确认扣减
                boolean confirmed = stockConsistencyUtil.confirmDeductStock(productId, 10, newStock);
                result.put("confirmed", confirmed);
            }
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("测试库存一致性功能异常", e);
            return Result.error("测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试滑动窗口限流
     */
    @GetMapping("/sliding-window")
    public Result<Map<String, Object>> testSlidingWindow(@RequestParam String key) {
        try {
            Map<String, Object> result = new HashMap<>();
            
            // 测试限流
            boolean allowed = slidingWindowRateLimiter.tryAcquire(key, 60, 10);
            result.put("allowed", allowed);
            
            // 获取当前计数
            long currentCount = slidingWindowRateLimiter.getCurrentCount(key, 60);
            result.put("currentCount", currentCount);
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("测试滑动窗口限流异常", e);
            return Result.error("测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试令牌桶限流
     */
    @GetMapping("/token-bucket")
    public Result<Map<String, Object>> testTokenBucket(@RequestParam String key) {
        try {
            Map<String, Object> result = new HashMap<>();
            
            // 预填充令牌桶
            tokenBucketRateLimiter.preFill(key, 100);
            result.put("preFilled", true);
            
            // 测试限流
            boolean allowed = tokenBucketRateLimiter.tryAcquire(key, 100, 10.0, 1);
            result.put("allowed", allowed);
            
            // 获取当前令牌数
            long currentTokens = tokenBucketRateLimiter.getCurrentTokens(key);
            result.put("currentTokens", currentTokens);
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("测试令牌桶限流异常", e);
            return Result.error("测试失败: " + e.getMessage());
        }
    }

    /**
     * 综合测试
     */
    @GetMapping("/comprehensive")
    public Result<Map<String, Object>> comprehensiveTest() {
        try {
            Map<String, Object> result = new HashMap<>();
            
            // 1. 测试JWT
            String token = jwtUtil.generateToken(1001L, "testuser");
            result.put("jwtToken", token);
            
            // 2. 测试幂等性
            String idempotencyToken = idempotencyUtil.generateIdempotencyToken("comprehensive");
            result.put("idempotencyToken", idempotencyToken);
            
            // 3. 测试分布式锁
            String lockKey = "test:lock:" + System.currentTimeMillis();
            boolean locked = redissonLockUtil.tryLockWithWatchdog(lockKey, 5);
            result.put("distributedLock", locked);
            if (locked) {
                redissonLockUtil.unlock(lockKey);
            }
            
            // 4. 测试限流
            boolean rateLimited = slidingWindowRateLimiter.tryAcquire("test:rate:limit", 60, 100);
            result.put("rateLimit", rateLimited);
            
            result.put("testTime", System.currentTimeMillis());
            result.put("status", "success");
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("综合测试异常", e);
            return Result.error("测试失败: " + e.getMessage());
        }
    }
}
