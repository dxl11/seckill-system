package com.seckill.system.util;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;

/**
 * 限流工具类 - 基于计数器算法
 * 
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Component
public class RateLimiterUtil {
    
    /**
     * 限流器缓存，key为限流标识，value为对应的限流器
     */
    private static final ConcurrentHashMap<String, SimpleRateLimiter> RATE_LIMITER_MAP = new ConcurrentHashMap<>();
    
    /**
     * 获取限流器
     * 
     * @param key 限流标识
     * @param qps 每秒允许的请求数
     * @return 限流器实例
     */
    public static SimpleRateLimiter getRateLimiter(String key, double qps) {
        return RATE_LIMITER_MAP.computeIfAbsent(key, k -> new SimpleRateLimiter(qps));
    }
    
    /**
     * 尝试获取令牌（非阻塞）
     * 
     * @param key 限流标识
     * @param qps 每秒允许的请求数
     * @return 是否获取成功
     */
    public static boolean tryAcquire(String key, double qps) {
        SimpleRateLimiter rateLimiter = getRateLimiter(key, qps);
        return rateLimiter.tryAcquire();
    }
    
    /**
     * 尝试获取指定数量的令牌（非阻塞）
     * 
     * @param key 限流标识
     * @param qps 每秒允许的请求数
     * @param permits 令牌数量
     * @return 是否获取成功
     */
    public static boolean tryAcquire(String key, double qps, int permits) {
        SimpleRateLimiter rateLimiter = getRateLimiter(key, qps);
        return rateLimiter.tryAcquire(permits);
    }
    
    /**
     * 尝试获取令牌（带超时时间）
     * 
     * @param key 限流标识
     * @param qps 每秒允许的请求数
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 是否获取成功
     */
    public static boolean tryAcquire(String key, double qps, long timeout, TimeUnit unit) {
        SimpleRateLimiter rateLimiter = getRateLimiter(key, qps);
        return rateLimiter.tryAcquire(timeout, unit);
    }
    
    /**
     * 获取令牌（阻塞）
     * 
     * @param key 限流标识
     * @param qps 每秒允许的请求数
     * @return 等待时间
     */
    public static double acquire(String key, double qps) {
        SimpleRateLimiter rateLimiter = getRateLimiter(key, qps);
        return rateLimiter.acquire();
    }
    
    /**
     * 获取指定数量的令牌（阻塞）
     * 
     * @param key 限流标识
     * @param qps 每秒允许的请求数
     * @param permits 令牌数量
     * @return 等待时间
     */
    public static double acquire(String key, double qps, int permits) {
        SimpleRateLimiter rateLimiter = getRateLimiter(key, qps);
        return rateLimiter.acquire(permits);
    }
    
    /**
     * 移除限流器
     * 
     * @param key 限流标识
     */
    public static void removeRateLimiter(String key) {
        RATE_LIMITER_MAP.remove(key);
    }
    
    /**
     * 清空所有限流器
     */
    public static void clearAll() {
        RATE_LIMITER_MAP.clear();
    }
    
    /**
     * 简单限流器实现
     */
    public static class SimpleRateLimiter {
        private final double qps;
        private final AtomicLong counter;
        private volatile long lastResetTime;
        
        public SimpleRateLimiter(double qps) {
            this.qps = qps;
            this.counter = new AtomicLong(0);
            this.lastResetTime = System.currentTimeMillis();
        }
        
        public boolean tryAcquire() {
            return tryAcquire(1);
        }
        
        public boolean tryAcquire(int permits) {
            resetIfNeeded();
            long current = counter.get();
            if (current + permits <= qps) {
                return counter.compareAndSet(current, current + permits);
            }
            return false;
        }
        
        public boolean tryAcquire(long timeout, TimeUnit unit) {
            long startTime = System.currentTimeMillis();
            long timeoutMs = unit.toMillis(timeout);
            
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                if (tryAcquire()) {
                    return true;
                }
                try {
                    Thread.sleep(10); // 短暂休眠
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return false;
        }
        
        public double acquire() {
            return acquire(1);
        }
        
        public double acquire(int permits) {
            long startTime = System.currentTimeMillis();
            while (!tryAcquire(permits)) {
                try {
                    Thread.sleep(10); // 短暂休眠
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return 0.0;
                }
            }
            return (System.currentTimeMillis() - startTime) / 1000.0;
        }
        
        private void resetIfNeeded() {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastResetTime >= 1000) { // 1秒重置一次
                counter.set(0);
                lastResetTime = currentTime;
            }
        }
    }
}
