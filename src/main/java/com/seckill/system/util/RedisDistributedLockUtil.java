package com.seckill.system.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 基于Redis的分布式锁工具类
 *
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Component
@Slf4j
public class RedisDistributedLockUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 锁的默认过期时间（秒）
     */
    private static final long DEFAULT_EXPIRE_TIME = 30;

    /**
     * 锁的默认等待时间（秒）
     */
    private static final long DEFAULT_WAIT_TIME = 5;

    /**
     * 锁的默认重试间隔（毫秒）
     */
    private static final long DEFAULT_RETRY_INTERVAL = 100;

    /**
     * 尝试获取分布式锁
     *
     * @param lockKey 锁的key
     * @param expireTime 锁的过期时间（秒）
     * @return 是否获取成功
     */
    public boolean tryLock(String lockKey, long expireTime) {
        try {
            Boolean result = redisTemplate.opsForValue().setIfAbsent(lockKey, 
                Thread.currentThread().getId(), expireTime, TimeUnit.SECONDS);
            boolean success = Boolean.TRUE.equals(result);
            
            if (success) {
                log.debug("获取分布式锁成功，lockKey: {}, threadId: {}", lockKey, Thread.currentThread().getId());
            } else {
                log.debug("获取分布式锁失败，lockKey: {}", lockKey);
            }
            
            return success;
        } catch (Exception e) {
            log.error("获取分布式锁异常，lockKey: {}", lockKey, e);
            return false;
        }
    }

    /**
     * 尝试获取分布式锁（使用默认过期时间）
     *
     * @param lockKey 锁的key
     * @return 是否获取成功
     */
    public boolean tryLock(String lockKey) {
        return tryLock(lockKey, DEFAULT_EXPIRE_TIME);
    }

    /**
     * 尝试获取分布式锁（带等待时间）
     *
     * @param lockKey 锁的key
     * @param waitTime 等待时间（秒）
     * @param expireTime 锁的过期时间（秒）
     * @return 是否获取成功
     */
    public boolean tryLock(String lockKey, long waitTime, long expireTime) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + waitTime * 1000;
        
        while (System.currentTimeMillis() < endTime) {
            if (tryLock(lockKey, expireTime)) {
                return true;
            }
            
            try {
                Thread.sleep(DEFAULT_RETRY_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        log.warn("获取分布式锁超时，lockKey: {}, waitTime: {}", lockKey, waitTime);
        return false;
    }

    /**
     * 释放分布式锁
     *
     * @param lockKey 锁的key
     * @return 是否释放成功
     */
    public boolean unlock(String lockKey) {
        try {
            // 使用Lua脚本确保原子性释放锁
            String script = 
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "    return redis.call('del', KEYS[1]) " +
                "else " +
                "    return 0 " +
                "end";
            
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
            redisScript.setScriptText(script);
            redisScript.setResultType(Long.class);
            
            Long result = redisTemplate.execute(redisScript, Arrays.asList(lockKey), 
                String.valueOf(Thread.currentThread().getId()));
            
            boolean success = result != null && result == 1L;
            if (success) {
                log.debug("释放分布式锁成功，lockKey: {}", lockKey);
            } else {
                log.debug("释放分布式锁失败，lockKey: {}, 可能不是锁的持有者", lockKey);
            }
            
            return success;
        } catch (Exception e) {
            log.error("释放分布式锁异常，lockKey: {}", lockKey, e);
            return false;
        }
    }

    /**
     * 执行带锁的业务逻辑
     *
     * @param lockKey 锁的key
     * @param waitTime 等待时间（秒）
     * @param expireTime 锁的过期时间（秒）
     * @param businessLogic 业务逻辑
     * @param <T> 返回值类型
     * @return 业务逻辑执行结果
     */
    public <T> T executeWithLock(String lockKey, long waitTime, long expireTime, LockCallback<T> businessLogic) {
        if (tryLock(lockKey, waitTime, expireTime)) {
            try {
                return businessLogic.execute();
            } finally {
                unlock(lockKey);
            }
        } else {
            throw new RuntimeException("获取分布式锁失败，lockKey: " + lockKey);
        }
    }

    /**
     * 执行带锁的业务逻辑（使用默认参数）
     *
     * @param lockKey 锁的key
     * @param businessLogic 业务逻辑
     * @param <T> 返回值类型
     * @return 业务逻辑执行结果
     */
    public <T> T executeWithLock(String lockKey, LockCallback<T> businessLogic) {
        return executeWithLock(lockKey, DEFAULT_WAIT_TIME, DEFAULT_EXPIRE_TIME, businessLogic);
    }

    /**
     * 执行带锁的业务逻辑（无返回值）
     *
     * @param lockKey 锁的key
     * @param waitTime 等待时间（秒）
     * @param expireTime 锁的过期时间（秒）
     * @param businessLogic 业务逻辑
     */
    public void executeWithLock(String lockKey, long waitTime, long expireTime, LockCallbackVoid businessLogic) {
        if (tryLock(lockKey, waitTime, expireTime)) {
            try {
                businessLogic.execute();
            } finally {
                unlock(lockKey);
            }
        } else {
            throw new RuntimeException("获取分布式锁失败，lockKey: " + lockKey);
        }
    }

    /**
     * 执行带锁的业务逻辑（无返回值，使用默认参数）
     *
     * @param lockKey 锁的key
     * @param businessLogic 业务逻辑
     */
    public void executeWithLock(String lockKey, LockCallbackVoid businessLogic) {
        executeWithLock(lockKey, DEFAULT_WAIT_TIME, DEFAULT_EXPIRE_TIME, businessLogic);
    }

    /**
     * 检查锁是否存在
     *
     * @param lockKey 锁的key
     * @return 是否存在
     */
    public boolean isLocked(String lockKey) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
        } catch (Exception e) {
            log.error("检查锁状态异常，lockKey: {}", lockKey, e);
            return false;
        }
    }

    /**
     * 获取锁的剩余过期时间
     *
     * @param lockKey 锁的key
     * @return 剩余过期时间（秒），-1表示永不过期，-2表示key不存在
     */
    public Long getLockExpireTime(String lockKey) {
        try {
            return redisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("获取锁过期时间异常，lockKey: {}", lockKey, e);
            return -2L;
        }
    }

    /**
     * 锁回调接口（有返回值）
     */
    @FunctionalInterface
    public interface LockCallback<T> {
        T execute();
    }

    /**
     * 锁回调接口（无返回值）
     */
    @FunctionalInterface
    public interface LockCallbackVoid {
        void execute();
    }
}
