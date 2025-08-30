package com.seckill.system.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 滑动窗口限流器
 * 
 * 使用Redis实现滑动窗口限流算法，支持多维度限流和动态配置
 * 
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Component
@Slf4j
public class SlidingWindowRateLimiter {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 滑动窗口限流Lua脚本
     * KEYS[1]: 限流key
     * ARGV[1]: 窗口大小（秒）
     * ARGV[2]: 最大请求数
     * ARGV[3]: 当前时间戳
     */
    private static final String SLIDING_WINDOW_SCRIPT = 
        "local key = KEYS[1] " +
        "local window = tonumber(ARGV[1]) " +
        "local limit = tonumber(ARGV[2]) " +
        "local current = tonumber(ARGV[3]) " +
        "local start = current - window " +
        " " +
        "-- 移除过期的请求记录 " +
        "redis.call('ZREMRANGEBYSCORE', key, 0, start) " +
        " " +
        "-- 获取当前窗口内的请求数 " +
        "local count = redis.call('ZCARD', key) " +
        " " +
        "if count < limit then " +
        "    -- 添加当前请求 " +
        "    redis.call('ZADD', key, current, current .. ':' .. math.random()) " +
        "    -- 设置过期时间 " +
        "    redis.call('EXPIRE', key, window) " +
        "    return 1 " +
        "else " +
        "    return 0 " +
        "end";

    /**
     * 尝试获取限流许可（滑动窗口）
     *
     * @param key 限流key
     * @param windowSize 窗口大小（秒）
     * @param limit 最大请求数
     * @return 是否允许请求
     */
    public boolean tryAcquire(String key, int windowSize, int limit) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(SLIDING_WINDOW_SCRIPT);
            script.setResultType(Long.class);

            long currentTime = System.currentTimeMillis() / 1000;
            Long result = redisTemplate.execute(script, Arrays.asList(key), 
                String.valueOf(windowSize), String.valueOf(limit), String.valueOf(currentTime));

            boolean allowed = result != null && result == 1L;
            if (allowed) {
                log.debug("滑动窗口限流通过，key: {}, window: {}s, limit: {}", key, windowSize, limit);
            } else {
                log.debug("滑动窗口限流拒绝，key: {}, window: {}s, limit: {}", key, windowSize, limit);
            }
            return allowed;
        } catch (Exception e) {
            log.error("滑动窗口限流异常，key: {}", key, e);
            // 异常时默认放行，避免影响业务
            return true;
        }
    }

    /**
     * 尝试获取限流许可（带用户维度）
     *
     * @param key 限流key
     * @param userId 用户ID
     * @param windowSize 窗口大小（秒）
     * @param limit 最大请求数
     * @return 是否允许请求
     */
    public boolean tryAcquire(String key, Long userId, int windowSize, int limit) {
        String userKey = key + ":user:" + userId;
        return tryAcquire(userKey, windowSize, limit);
    }

    /**
     * 尝试获取限流许可（带IP维度）
     *
     * @param key 限流key
     * @param ip IP地址
     * @param windowSize 窗口大小（秒）
     * @param limit 最大请求数
     * @return 是否允许请求
     */
    public boolean tryAcquire(String key, String ip, int windowSize, int limit) {
        String ipKey = key + ":ip:" + ip;
        return tryAcquire(ipKey, windowSize, limit);
    }

    /**
     * 获取当前限流状态
     *
     * @param key 限流key
     * @param windowSize 窗口大小（秒）
     * @return 当前窗口内的请求数
     */
    public long getCurrentCount(String key, int windowSize) {
        try {
            long currentTime = System.currentTimeMillis() / 1000;
            long start = currentTime - windowSize;
            
            Long count = redisTemplate.opsForZSet().count(key, start, currentTime);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("获取限流状态异常，key: {}", key, e);
            return 0;
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
            log.info("重置限流器，key: {}", key);
        } catch (Exception e) {
            log.error("重置限流器异常，key: {}", key, e);
        }
    }
}
