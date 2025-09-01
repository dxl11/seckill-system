package com.seckill.system.util;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 基于Redisson的分布式锁工具类（支持看门狗自动续期）
 * 
 * 解决传统分布式锁超时释放的问题，确保业务执行完成前锁不会释放
 * 
 * @author seckill-system
 * @version 2.0.0
 * @since 2025-08-28
 */
@Component
@Slf4j
public class RedissonDistributedLockUtil {

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 默认锁超时时间（秒）
     */
    private static final long DEFAULT_LEASE_TIME = 30;

    /**
     * 默认等待时间（秒）
     */
    private static final long DEFAULT_WAIT_TIME = 5;

    /**
     * 尝试获取分布式锁（带看门狗自动续期）
     *
     * @param lockKey 锁的key
     * @param waitTime 等待时间（秒）
     * @param leaseTime 锁的持有时间（秒），-1表示永久持有，看门狗自动续期
     * @param timeUnit 时间单位
     * @return 是否获取成功
     */
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean success = lock.tryLock(waitTime, leaseTime, timeUnit);
            if (success) {
                log.debug("获取Redisson分布式锁成功，lockKey: {}, leaseTime: {}s", lockKey, leaseTime);
            } else {
                log.debug("获取Redisson分布式锁失败，lockKey: {}", lockKey);
            }
            return success;
        } catch (InterruptedException e) {
            log.error("获取Redisson分布式锁被中断，lockKey: {}", lockKey, e);
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.error("获取Redisson分布式锁异常，lockKey: {}", lockKey, e);
            return false;
        }
    }

    /**
     * 尝试获取分布式锁（使用默认参数）
     *
     * @param lockKey 锁的key
     * @return 是否获取成功
     */
    public boolean tryLock(String lockKey) {
        return tryLock(lockKey, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, TimeUnit.SECONDS);
    }

    /**
     * 尝试获取分布式锁（带看门狗，永久持有）
     *
     * @param lockKey 锁的key
     * @param waitTime 等待时间（秒）
     * @return 是否获取成功
     */
    public boolean tryLockWithWatchdog(String lockKey, long waitTime) {
        // leaseTime = -1 表示永久持有，看门狗自动续期
        return tryLock(lockKey, waitTime, -1, TimeUnit.SECONDS);
    }

    /**
     * 释放分布式锁
     *
     * @param lockKey 锁的key
     * @return 是否释放成功
     */
    public boolean unlock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("释放Redisson分布式锁成功，lockKey: {}", lockKey);
                return true;
            } else {
                log.warn("尝试释放不属于当前线程的锁，lockKey: {}", lockKey);
                return false;
            }
        } catch (Exception e) {
            log.error("释放Redisson分布式锁异常，lockKey: {}", lockKey, e);
            return false;
        }
    }

    /**
     * 执行带锁的业务逻辑（带看门狗自动续期）
     *
     * @param lockKey 锁的key
     * @param waitTime 等待时间（秒）
     * @param businessLogic 业务逻辑
     * @param <T> 返回值类型
     * @return 业务逻辑执行结果
     */
    public <T> T executeWithLock(String lockKey, long waitTime, LockCallback<T> businessLogic) {
        if (tryLockWithWatchdog(lockKey, waitTime)) {
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
     * 执行带锁的业务逻辑（无返回值，带看门狗自动续期）
     *
     * @param lockKey 锁的key
     * @param waitTime 等待时间（秒）
     * @param businessLogic 业务逻辑
     */
    public void executeWithLock(String lockKey, long waitTime, LockCallbackVoid businessLogic) {
        if (tryLockWithWatchdog(lockKey, waitTime)) {
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
     * 检查锁是否存在
     *
     * @param lockKey 锁的key
     * @return 是否存在
     */
    public boolean isLocked(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            return lock.isLocked();
        } catch (Exception e) {
            log.error("检查锁状态异常，lockKey: {}", lockKey, e);
            return false;
        }
    }

    /**
     * 检查锁是否被当前线程持有
     *
     * @param lockKey 锁的key
     * @return 是否被当前线程持有
     */
    public boolean isHeldByCurrentThread(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            return lock.isHeldByCurrentThread();
        } catch (Exception e) {
            log.error("检查锁持有状态异常，lockKey: {}", lockKey, e);
            return false;
        }
    }

    /**
     * 获取锁的剩余持有时间
     *
     * @param lockKey 锁的key
     * @return 剩余持有时间（毫秒），-1表示永久持有
     */
    public long getRemainTime(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            return lock.remainTimeToLive();
        } catch (Exception e) {
            log.error("获取锁剩余时间异常，lockKey: {}", lockKey, e);
            return -1;
        }
    }

    /**
     * 强制释放锁（危险操作，谨慎使用）
     *
     * @param lockKey 锁的key
     * @return 是否释放成功
     */
    public boolean forceUnlock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean result = lock.forceUnlock();
            if (result) {
                log.warn("强制释放分布式锁成功，lockKey: {}", lockKey);
            }
            return result;
        } catch (Exception e) {
            log.error("强制释放分布式锁异常，lockKey: {}", lockKey, e);
            return false;
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
