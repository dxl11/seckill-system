package com.seckill.system.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 令牌桶限流器
 * 
 * 使用Redis实现令牌桶限流算法，支持多维度限流和动态配置
 * 
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Component
@Slf4j
public class TokenBucketRateLimiter {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 令牌桶限流Lua脚本
     * KEYS[1]: 限流key
     * ARGV[1]: 令牌桶容量
     * ARGV[2]: 令牌生成速率（个/秒）
     * ARGV[3]: 当前时间戳
     * ARGV[4]: 请求令牌数
     */
    private static final String TOKEN_BUCKET_SCRIPT = 
        "local key = KEYS[1] " +
        "local capacity = tonumber(ARGV[1]) " +
        "local rate = tonumber(ARGV[2]) " +
        "local current = tonumber(ARGV[3]) " +
        "local tokens = tonumber(ARGV[4]) " +
        " " +
        "-- 获取上次更新时间 " +
        "local lastUpdate = redis.call('HGET', key, 'lastUpdate') " +
        "local lastUpdateNum = 0 " +
        "if lastUpdate then " +
        "    lastUpdateNum = tonumber(lastUpdate) " +
        "end " +
        " " +
        "-- 计算时间间隔 " +
        "local timePassed = current - lastUpdateNum " +
        " " +
        "-- 计算新增令牌数 " +
        "local newTokens = math.floor(timePassed * rate) " +
        " " +
        "-- 获取当前令牌数 " +
        "local currentTokens = redis.call('HGET', key, 'tokens') " +
        "local currentTokensNum = 0 " +
        "if currentTokens then " +
        "    currentTokensNum = tonumber(currentTokens) " +
        "end " +
        " " +
        "-- 计算实际令牌数（不超过容量） " +
        "local actualTokens = math.min(capacity, currentTokensNum + newTokens) " +
        " " +
        "-- 检查是否有足够令牌 " +
        "if actualTokens >= tokens then " +
        "    -- 扣除令牌 " +
        "    actualTokens = actualTokens - tokens " +
        "    -- 更新令牌数和时间 " +
        "    redis.call('HSET', key, 'tokens', actualTokens) " +
        "    redis.call('HSET', key, 'lastUpdate', current) " +
        "    -- 设置过期时间 " +
        "    redis.call('EXPIRE', key, 3600) " +
        "    return 1 " +
        "else " +
        "    return 0 " +
        "end";

    /**
     * 尝试获取限流许可（令牌桶）
     *
     * @param key 限流key
     * @param capacity 令牌桶容量
     * @param rate 令牌生成速率（个/秒）
     * @param tokens 请求令牌数
     * @return 是否允许请求
     */
    public boolean tryAcquire(String key, int capacity, double rate, int tokens) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(TOKEN_BUCKET_SCRIPT);
            script.setResultType(Long.class);

            long currentTime = System.currentTimeMillis() / 1000;
            Long result = redisTemplate.execute(script, Arrays.asList(key), 
                String.valueOf(capacity), String.valueOf(rate), String.valueOf(currentTime), String.valueOf(tokens));

            boolean allowed = result != null && result == 1L;
            if (allowed) {
                log.debug("令牌桶限流通过，key: {}, capacity: {}, rate: {}, tokens: {}", key, capacity, rate, tokens);
            } else {
                log.debug("令牌桶限流拒绝，key: {}, capacity: {}, rate: {}, tokens: {}", key, capacity, rate, tokens);
            }
            return allowed;
        } catch (Exception e) {
            log.error("令牌桶限流异常，key: {}", key, e);
            // 异常时默认放行，避免影响业务
            return true;
        }
    }

    /**
     * 尝试获取限流许可（带用户维度）
     *
     * @param key 限流key
     * @param userId 用户ID
     * @param capacity 令牌桶容量
     * @param rate 令牌生成速率（个/秒）
     * @param tokens 请求令牌数
     * @return 是否允许请求
     */
    public boolean tryAcquire(String key, Long userId, int capacity, double rate, int tokens) {
        String userKey = key + ":user:" + userId;
        return tryAcquire(userKey, capacity, rate, tokens);
    }

    /**
     * 尝试获取限流许可（带IP维度）
     *
     * @param key 限流key
     * @param ip IP地址
     * @param capacity 令牌桶容量
     * @param rate 令牌生成速率（个/秒）
     * @param tokens 请求令牌数
     * @return 是否允许请求
     */
    public boolean tryAcquire(String key, String ip, int capacity, double rate, int tokens) {
        String ipKey = key + ":ip:" + ip;
        return tryAcquire(ipKey, capacity, rate, tokens);
    }

    /**
     * 获取当前令牌桶状态
     *
     * @param key 限流key
     * @return 当前令牌数
     */
    public long getCurrentTokens(String key) {
        try {
            Object tokens = redisTemplate.opsForHash().get(key, "tokens");
            return tokens != null ? Long.parseLong(tokens.toString()) : 0;
        } catch (Exception e) {
            log.error("获取令牌桶状态异常，key: {}", key, e);
            return 0;
        }
    }

    /**
     * 获取上次更新时间
     *
     * @param key 限流key
     * @return 上次更新时间戳
     */
    public long getLastUpdate(String key) {
        try {
            Object lastUpdate = redisTemplate.opsForHash().get(key, "lastUpdate");
            return lastUpdate != null ? Long.parseLong(lastUpdate.toString()) : 0;
        } catch (Exception e) {
            log.error("获取上次更新时间异常，key: {}", key, e);
            return 0;
        }
    }

    /**
     * 重置令牌桶
     *
     * @param key 限流key
     */
    public void reset(String key) {
        try {
            redisTemplate.delete(key);
            log.info("重置令牌桶，key: {}", key);
        } catch (Exception e) {
            log.error("重置令牌桶异常，key: {}", key, e);
        }
    }

    /**
     * 预填充令牌桶
     *
     * @param key 限流key
     * @param capacity 令牌桶容量
     */
    public void preFill(String key, int capacity) {
        try {
            long currentTime = System.currentTimeMillis() / 1000;
            redisTemplate.opsForHash().put(key, "tokens", capacity);
            redisTemplate.opsForHash().put(key, "lastUpdate", currentTime);
            redisTemplate.expire(key, java.time.Duration.ofHours(1));
            log.info("预填充令牌桶，key: {}, tokens: {}", key, capacity);
        } catch (Exception e) {
            log.error("预填充令牌桶异常，key: {}", key, e);
        }
    }
}
