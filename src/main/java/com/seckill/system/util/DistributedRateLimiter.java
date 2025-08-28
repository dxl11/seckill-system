package com.seckill.system.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 分布式限流器
 * 基于Redis的滑动窗口算法实现
 *
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Component
@Slf4j
public class DistributedRateLimiter {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 限流Lua脚本
     * 参数：KEYS[1] = key, ARGV[1] = limit, ARGV[2] = window
     * 返回值：1表示成功，0表示失败
     */
    private static final String RATE_LIMIT_SCRIPT = 
        "local current = redis.call('get', KEYS[1]) " +
        "if current == false then " +
        "    redis.call('incr', KEYS[1]) " +
        "    redis.call('expire', KEYS[1], ARGV[2]) " +
        "    return 1 " +
        "end " +
        "if tonumber(current) < tonumber(ARGV[1]) then " +
        "    redis.call('incr', KEYS[1]) " +
        "    return 1 " +
        "else " +
        "    return 0 " +
        "end";

    /**
     * 尝试获取令牌（非阻塞）
     *
     * @param key 限流key
     * @param limit 限制次数
     * @param window 时间窗口（秒）
     * @return 是否获取成功
     */
    public boolean tryAcquire(String key, int limit, int window) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(RATE_LIMIT_SCRIPT);
            script.setResultType(Long.class);
            
            Long result = redisTemplate.execute(script, Arrays.asList(key), String.valueOf(limit), String.valueOf(window));
            
            boolean success = result != null && result == 1L;
            log.debug("限流检查结果，key: {}, limit: {}, window: {}, result: {}", key, limit, window, success);
            return success;
            
        } catch (Exception e) {
            log.error("限流检查异常，key: {}, limit: {}, window: {}", key, limit, window, e);
            // 异常情况下默认放行，避免影响业务
            return true;
        }
    }

    /**
     * 获取令牌（阻塞）
     *
     * @param key 限流key
     * @param limit 限制次数
     * @param window 时间窗口（秒）
     * @param timeout 超时时间（毫秒）
     * @return 是否获取成功
     */
    public boolean acquire(String key, int limit, int window, long timeout) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeout) {
            if (tryAcquire(key, limit, window)) {
                return true;
            }
            try {
                Thread.sleep(10); // 短暂休眠，避免过度消耗CPU
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        log.warn("限流获取令牌超时，key: {}, limit: {}, window: {}, timeout: {}", key, limit, window, timeout);
        return false;
    }

    /**
     * 获取当前计数
     *
     * @param key 限流key
     * @return 当前计数
     */
    public Long getCurrentCount(String key) {
        try {
            Object result = redisTemplate.opsForValue().get(key);
            return result != null ? Long.valueOf(result.toString()) : 0L;
        } catch (Exception e) {
            log.error("获取限流计数异常，key: {}", key, e);
            return 0L;
        }
    }

    /**
     * 重置限流器
     *
     * @param key 限流key
     */
    public void reset(String key) {
        try {
            redisTemplate.delete(key);
            log.debug("限流器重置成功，key: {}", key);
        } catch (Exception e) {
            log.error("限流器重置异常，key: {}", key, e);
        }
    }

    /**
     * 获取剩余可用次数
     *
     * @param key 限流key
     * @param limit 限制次数
     * @return 剩余可用次数
     */
    public Long getRemainingCount(String key, int limit) {
        try {
            Long current = getCurrentCount(key);
            return Math.max(0, limit - current);
        } catch (Exception e) {
            log.error("获取剩余次数异常，key: {}, limit: {}", key, limit, e);
            return 0L;
        }
    }

    /**
     * 预热限流器
     *
     * @param key 限流key
     * @param initialCount 初始计数
     * @param window 时间窗口（秒）
     */
    public void warmUp(String key, int initialCount, int window) {
        try {
            redisTemplate.opsForValue().set(key, initialCount, window, TimeUnit.SECONDS);
            log.debug("限流器预热成功，key: {}, initialCount: {}, window: {}", key, initialCount, window);
        } catch (Exception e) {
            log.error("限流器预热异常，key: {}, initialCount: {}, window: {}", key, initialCount, window, e);
        }
    }
}
