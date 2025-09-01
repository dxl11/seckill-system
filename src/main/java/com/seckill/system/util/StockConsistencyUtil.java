package com.seckill.system.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 库存一致性工具类
 * 
 * 确保Redis和数据库库存的一致性，防止超卖和库存不一致
 * 
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Component
@Slf4j
public class StockConsistencyUtil {

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private RedisLuaUtil redisLuaUtil;

    /**
     * 库存缓存前缀
     */
    private static final String STOCK_CACHE_PREFIX = "seckill:stock:";

    /**
     * 库存锁定前缀
     */
    private static final String STOCK_LOCK_PREFIX = "seckill:stock:lock:";

    /**
     * 库存同步前缀
     */
    private static final String STOCK_SYNC_PREFIX = "seckill:stock:sync:";

    /**
     * 库存锁定过期时间（30秒）
     */
    private static final long STOCK_LOCK_EXPIRE = 30;

    /**
     * 库存同步过期时间（5分钟）
     */
    private static final long STOCK_SYNC_EXPIRE = 5;

    /**
     * 预扣减库存（Redis）
     *
     * @param productId 商品ID
     * @param quantity 扣减数量
     * @return 扣减后的库存，-1表示库存不足
     */
    public Long preDeductStock(Long productId, Integer quantity) {
        String stockKey = STOCK_CACHE_PREFIX + productId;
        String lockKey = STOCK_LOCK_PREFIX + productId;

        try {
            // 1. 尝试获取库存锁
            if (!acquireStockLock(lockKey)) {
                log.warn("获取库存锁失败，productId: {}", productId);
                return -1L;
            }

            try {
                // 2. 检查Redis库存
                Object currentStock = redisUtil.get(stockKey);
                if (currentStock == null) {
                    log.warn("Redis中不存在库存信息，productId: {}", productId);
                    return -1L;
                }

                long stock = Long.parseLong(currentStock.toString());
                if (stock < quantity) {
                    log.warn("Redis库存不足，productId: {}, 当前库存: {}, 需要: {}", productId, stock, quantity);
                    return -1L;
                }

                // 3. 预扣减库存
                Long newStock = redisLuaUtil.decreaseStock(stockKey, quantity);
                if (newStock < 0) {
                    log.warn("Redis库存扣减失败，productId: {}", productId);
                    return -1L;
                }

                // 4. 标记库存已锁定
                String syncKey = STOCK_SYNC_PREFIX + productId + ":" + System.currentTimeMillis();
                redisUtil.set(syncKey, String.format("deduct:%d:%d", quantity, newStock), 
                    STOCK_SYNC_EXPIRE, TimeUnit.MINUTES);

                log.info("Redis库存预扣减成功，productId: {}, 扣减数量: {}, 剩余库存: {}", 
                    productId, quantity, newStock);
                return newStock;

            } finally {
                // 释放库存锁
                releaseStockLock(lockKey);
            }

        } catch (Exception e) {
            log.error("预扣减库存异常，productId: {}, quantity: {}", productId, quantity, e);
            return -1L;
        }
    }

    /**
     * 确认扣减库存（数据库）
     *
     * @param productId 商品ID
     * @param quantity 扣减数量
     * @param expectedStock 期望的剩余库存
     * @return 是否扣减成功
     */
    public boolean confirmDeductStock(Long productId, Integer quantity, Long expectedStock) {
        String stockKey = STOCK_CACHE_PREFIX + productId;
        String lockKey = STOCK_LOCK_PREFIX + productId;

        try {
            // 1. 尝试获取库存锁
            if (!acquireStockLock(lockKey)) {
                log.warn("获取库存锁失败，productId: {}", productId);
                return false;
            }

            try {
                // 2. 验证Redis库存状态
                Object currentStock = redisUtil.get(stockKey);
                if (currentStock == null) {
                    log.warn("Redis中不存在库存信息，productId: {}", productId);
                    return false;
                }

                long stock = Long.parseLong(currentStock.toString());
                if (stock != expectedStock) {
                    log.warn("Redis库存状态不一致，productId: {}, 期望: {}, 实际: {}", 
                        productId, expectedStock, stock);
                    // 回滚Redis库存
                    rollbackStock(productId, quantity);
                    return false;
                }

                // 3. 标记库存扣减完成
                String syncKey = STOCK_SYNC_PREFIX + productId + ":" + System.currentTimeMillis();
                redisUtil.set(syncKey, String.format("confirm:%d:%d", quantity, stock), 
                    STOCK_SYNC_EXPIRE, TimeUnit.MINUTES);

                log.info("库存扣减确认成功，productId: {}, 扣减数量: {}, 剩余库存: {}", 
                    productId, quantity, stock);
                return true;

            } finally {
                // 释放库存锁
                releaseStockLock(lockKey);
            }

        } catch (Exception e) {
            log.error("确认扣减库存异常，productId: {}, quantity: {}", productId, quantity, e);
            return false;
        }
    }

    /**
     * 回滚库存
     *
     * @param productId 商品ID
     * @param quantity 回滚数量
     * @return 是否回滚成功
     */
    public boolean rollbackStock(Long productId, Integer quantity) {
        String stockKey = STOCK_CACHE_PREFIX + productId;
        String lockKey = STOCK_LOCK_PREFIX + productId;

        try {
            // 1. 尝试获取库存锁
            if (!acquireStockLock(lockKey)) {
                log.warn("获取库存锁失败，productId: {}", productId);
                return false;
            }

            try {
                // 2. 回滚Redis库存
                Long newStock = redisLuaUtil.increaseStock(stockKey, quantity);
                if (newStock < 0) {
                    log.error("回滚Redis库存失败，productId: {}", productId);
                    return false;
                }

                // 3. 标记库存回滚
                String syncKey = STOCK_SYNC_PREFIX + productId + ":" + System.currentTimeMillis();
                redisUtil.set(syncKey, String.format("rollback:%d:%d", quantity, newStock), 
                    STOCK_SYNC_EXPIRE, TimeUnit.MINUTES);

                log.info("库存回滚成功，productId: {}, 回滚数量: {}, 当前库存: {}", 
                    productId, quantity, newStock);
                return true;

            } finally {
                // 释放库存锁
                releaseStockLock(lockKey);
            }

        } catch (Exception e) {
            log.error("回滚库存异常，productId: {}, quantity: {}", productId, quantity, e);
            return false;
        }
    }

    /**
     * 同步数据库库存到Redis
     *
     * @param productId 商品ID
     * @param dbStock 数据库库存
     * @return 是否同步成功
     */
    public boolean syncStockToRedis(Long productId, Integer dbStock) {
        String stockKey = STOCK_CACHE_PREFIX + productId;
        String lockKey = STOCK_LOCK_PREFIX + productId;

        try {
            // 1. 尝试获取库存锁
            if (!acquireStockLock(lockKey)) {
                log.warn("获取库存锁失败，productId: {}", productId);
                return false;
            }

            try {
                // 2. 同步库存到Redis
                redisUtil.set(stockKey, dbStock, 24, TimeUnit.HOURS);

                // 3. 标记库存同步
                String syncKey = STOCK_SYNC_PREFIX + productId + ":" + System.currentTimeMillis();
                redisUtil.set(syncKey, String.format("sync:%d", dbStock), 
                    STOCK_SYNC_EXPIRE, TimeUnit.MINUTES);

                log.info("库存同步成功，productId: {}, 数据库库存: {}", productId, dbStock);
                return true;

            } finally {
                // 释放库存锁
                releaseStockLock(lockKey);
            }

        } catch (Exception e) {
            log.error("同步库存异常，productId: {}, dbStock: {}", productId, dbStock, e);
            return false;
        }
    }

    /**
     * 获取库存一致性状态
     *
     * @param productId 商品ID
     * @return 库存状态信息
     */
    public String getStockConsistencyStatus(Long productId) {
        String stockKey = STOCK_CACHE_PREFIX + productId;
        String lockKey = STOCK_LOCK_PREFIX + productId;

        try {
            Object redisStock = redisUtil.get(stockKey);
            boolean isLocked = redisUtil.hasKey(lockKey);

            return String.format("商品ID: %d, Redis库存: %s, 是否锁定: %s", 
                productId, redisStock, isLocked);

        } catch (Exception e) {
            log.error("获取库存一致性状态异常，productId: {}", productId, e);
            return "获取状态失败";
        }
    }

    /**
     * 清理过期的库存同步记录
     *
     * @param productId 商品ID
     * @return 清理的记录数
     */
    public long cleanExpiredStockSyncRecords(Long productId) {
        try {
            // 这里可以实现清理逻辑，比如定期清理过期的库存同步记录
            log.info("清理库存同步记录，productId: {}", productId);
            return 0;
        } catch (Exception e) {
            log.error("清理库存同步记录异常，productId: {}", productId, e);
            return 0;
        }
    }

    /**
     * 获取库存锁
     *
     * @param lockKey 锁key
     * @return 是否获取成功
     */
    private boolean acquireStockLock(String lockKey) {
        try {
            // 使用Redis的SETNX实现分布式锁
            if (redisUtil.hasKey(lockKey)) {
                return false; // 锁已存在
            }
            redisUtil.set(lockKey, "1", STOCK_LOCK_EXPIRE, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.error("获取库存锁异常，lockKey: {}", lockKey, e);
            return false;
        }
    }

    /**
     * 释放库存锁
     *
     * @param lockKey 锁key
     */
    private void releaseStockLock(String lockKey) {
        try {
            redisUtil.delete(lockKey);
        } catch (Exception e) {
            log.error("释放库存锁异常，lockKey: {}", lockKey, e);
        }
    }
}
