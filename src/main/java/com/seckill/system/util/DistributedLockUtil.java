package com.seckill.system.util;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁工具类
 *
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Component
@Slf4j
public class DistributedLockUtil {

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 尝试获取分布式锁
     *
     * @param lockKey 锁的key
     * @param waitTime 等待时间
     * @param leaseTime 持有锁的时间
     * @param timeUnit 时间单位
     * @return 是否获取成功
     */
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean success = lock.tryLock(waitTime, leaseTime, timeUnit);
            if (success) {
                log.debug("获取分布式锁成功，lockKey: {}", lockKey);
            } else {
                log.debug("获取分布式锁失败，lockKey: {}", lockKey);
            }
            return success;
        } catch (InterruptedException e) {
            log.error("获取分布式锁被中断，lockKey: {}", lockKey, e);
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.error("获取分布式锁异常，lockKey: {}", lockKey, e);
            return false;
        }
    }

    /**
     * 释放分布式锁
     *
     * @param lockKey 锁的key
     */
    public void unlock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("释放分布式锁成功，lockKey: {}", lockKey);
            }
        } catch (Exception e) {
            log.error("释放分布式锁异常，lockKey: {}", lockKey, e);
        }
    }

    /**
     * 执行带锁的业务逻辑
     *
     * @param lockKey 锁的key
     * @param waitTime 等待时间
     * @param leaseTime 持有锁的时间
     * @param timeUnit 时间单位
     * @param businessLogic 业务逻辑
     * @param <T> 返回值类型
     * @return 业务逻辑执行结果
     */
    public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit, LockCallback<T> businessLogic) {
        if (tryLock(lockKey, waitTime, leaseTime, timeUnit)) {
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
     * 执行带锁的业务逻辑（无返回值）
     *
     * @param lockKey 锁的key
     * @param waitTime 等待时间
     * @param leaseTime 持有锁的时间
     * @param timeUnit 时间单位
     * @param businessLogic 业务逻辑
     */
    public void executeWithLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit, LockCallbackVoid businessLogic) {
        if (tryLock(lockKey, waitTime, leaseTime, timeUnit)) {
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
