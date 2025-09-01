package com.seckill.system.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 幂等性工具类
 * 
 * 防止重复请求和重复操作，确保接口的幂等性
 * 
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Component
@Slf4j
public class IdempotencyUtil {

    @Autowired
    private RedisUtil redisUtil;

    /**
     * 幂等性Token前缀
     */
    private static final String IDEMPOTENCY_PREFIX = "idempotency:token:";

    /**
     * 幂等性Token过期时间（24小时）
     */
    private static final long TOKEN_EXPIRE_TIME = 24;

    /**
     * 幂等性检查前缀
     */
    private static final String IDEMPOTENCY_CHECK_PREFIX = "idempotency:check:";

    /**
     * 幂等性检查过期时间（1小时）
     */
    private static final long CHECK_EXPIRE_TIME = 1;

    /**
     * 生成幂等性Token
     *
     * @return 幂等性Token
     */
    public String generateIdempotencyToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成幂等性Token（带业务标识）
     *
     * @param businessKey 业务标识
     * @return 幂等性Token
     */
    public String generateIdempotencyToken(String businessKey) {
        return businessKey + ":" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 验证幂等性Token
     *
     * @param token 幂等性Token
     * @return 是否有效
     */
    public boolean validateIdempotencyToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        String tokenKey = IDEMPOTENCY_PREFIX + token;
        return redisUtil.hasKey(tokenKey);
    }

    /**
     * 使用幂等性Token（一次性使用）
     *
     * @param token 幂等性Token
     * @return 是否使用成功
     */
    public boolean useIdempotencyToken(String token) {
        if (!validateIdempotencyToken(token)) {
            return false;
        }

        String tokenKey = IDEMPOTENCY_PREFIX + token;
        String checkKey = IDEMPOTENCY_CHECK_PREFIX + token;

        try {
            // 检查是否已经被使用
            if (redisUtil.hasKey(checkKey)) {
                log.warn("幂等性Token已被使用: {}", token);
                return false;
            }

            // 标记Token已被使用
            redisUtil.set(checkKey, "1", CHECK_EXPIRE_TIME, TimeUnit.HOURS);
            
            // 删除Token（一次性使用）
            redisUtil.delete(tokenKey);
            
            log.debug("幂等性Token使用成功: {}", token);
            return true;
        } catch (Exception e) {
            log.error("使用幂等性Token异常: {}", token, e);
            return false;
        }
    }

    /**
     * 检查并标记幂等性操作
     *
     * @param key 幂等性key
     * @param expireTime 过期时间
     * @param timeUnit 时间单位
     * @return 是否首次操作
     */
    public boolean checkAndMarkIdempotency(String key, long expireTime, TimeUnit timeUnit) {
        String idempotencyKey = IDEMPOTENCY_CHECK_PREFIX + key;
        
        try {
            // 检查是否已经执行过
            if (redisUtil.hasKey(idempotencyKey)) {
                log.debug("幂等性操作已执行过: {}", key);
                return false;
            }

            // 标记为已执行
            redisUtil.set(idempotencyKey, "1", expireTime, timeUnit);
            log.debug("幂等性操作标记成功: {}", key);
            return true;
        } catch (Exception e) {
            log.error("检查幂等性操作异常: {}", key, e);
            // 异常时默认允许执行，避免影响业务
            return true;
        }
    }

    /**
     * 检查并标记幂等性操作（使用默认过期时间）
     *
     * @param key 幂等性key
     * @return 是否首次操作
     */
    public boolean checkAndMarkIdempotency(String key) {
        return checkAndMarkIdempotency(key, CHECK_EXPIRE_TIME, TimeUnit.HOURS);
    }

    /**
     * 生成业务幂等性key
     *
     * @param userId 用户ID
     * @param businessType 业务类型
     * @param businessId 业务ID
     * @return 幂等性key
     */
    public String generateBusinessIdempotencyKey(Long userId, String businessType, String businessId) {
        return String.format("%s:%s:%s:%s", userId, businessType, businessId, System.currentTimeMillis());
    }

    /**
     * 生成秒杀幂等性key
     *
     * @param userId 用户ID
     * @param productId 商品ID
     * @return 幂等性key
     */
    public String generateSeckillIdempotencyKey(Long userId, Long productId) {
        return generateBusinessIdempotencyKey(userId, "seckill", productId.toString());
    }

    /**
     * 检查秒杀幂等性
     *
     * @param userId 用户ID
     * @param productId 商品ID
     * @return 是否首次秒杀
     */
    public boolean checkSeckillIdempotency(Long userId, Long productId) {
        String key = generateSeckillIdempotencyKey(userId, productId);
        return checkAndMarkIdempotency(key, 24, TimeUnit.HOURS); // 秒杀幂等性保持24小时
    }

    /**
     * 清理过期的幂等性检查记录
     *
     * @param pattern 匹配模式
     * @return 清理的记录数
     */
    public long cleanExpiredIdempotencyRecords(String pattern) {
        try {
            // 这里可以实现清理逻辑，比如定期清理过期的幂等性记录
            // 由于Redis会自动过期，这里主要是为了监控和统计
            log.info("清理幂等性记录，模式: {}", pattern);
            return 0;
        } catch (Exception e) {
            log.error("清理幂等性记录异常", e);
            return 0;
        }
    }

    /**
     * 获取幂等性Token统计信息
     *
     * @return 统计信息
     */
    public String getIdempotencyStats() {
        try {
            // 这里可以实现统计逻辑，比如统计当前有效的幂等性Token数量
            log.debug("获取幂等性统计信息");
            return "幂等性Token统计信息";
        } catch (Exception e) {
            log.error("获取幂等性统计信息异常", e);
            return "获取统计信息失败";
        }
    }
}
