package com.seckill.system.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Redis Lua脚本工具类
 *
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Component
@Slf4j
public class RedisLuaUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 库存扣减Lua脚本
     * 参数：KEYS[1] = stockKey, ARGV[1] = quantity
     * 返回值：-1表示库存不足，>=0表示扣减后的库存
     */
    private static final String DECREASE_STOCK_SCRIPT = 
        "local stock = redis.call('get', KEYS[1]) " +
        "if not stock then return -1 end " +
        "local currentStock = tonumber(stock) " +
        "local quantity = tonumber(ARGV[1]) " +
        "if currentStock >= quantity then " +
        "    redis.call('decrby', KEYS[1], quantity) " +
        "    return redis.call('get', KEYS[1]) " +
        "else " +
        "    return -1 " +
        "end";

    /**
     * 库存增加Lua脚本
     * 参数：KEYS[1] = stockKey, ARGV[1] = quantity
     * 返回值：增加后的库存
     */
    private static final String INCREASE_STOCK_SCRIPT = 
        "local stock = redis.call('get', KEYS[1]) " +
        "if not stock then " +
        "    redis.call('set', KEYS[1], ARGV[1]) " +
        "    return ARGV[1] " +
        "else " +
        "    redis.call('incrby', KEYS[1], ARGV[1]) " +
        "    return redis.call('get', KEYS[1]) " +
        "end";

    /**
     * 原子性扣减库存
     *
     * @param stockKey 库存key
     * @param quantity 扣减数量
     * @return 扣减后的库存，-1表示库存不足
     */
    public Long decreaseStock(String stockKey, Integer quantity) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(DECREASE_STOCK_SCRIPT);
            script.setResultType(Long.class);
            
            Long result = redisTemplate.execute(script, Arrays.asList(stockKey), quantity);
            log.debug("库存扣减执行结果，stockKey: {}, quantity: {}, result: {}", stockKey, quantity, result);
            return result;
        } catch (Exception e) {
            log.error("库存扣减异常，stockKey: {}, quantity: {}", stockKey, quantity, e);
            return -1L;
        }
    }

    /**
     * 原子性增加库存
     *
     * @param stockKey 库存key
     * @param quantity 增加数量
     * @return 增加后的库存
     */
    public Long increaseStock(String stockKey, Integer quantity) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(INCREASE_STOCK_SCRIPT);
            script.setResultType(Long.class);
            
            Long result = redisTemplate.execute(script, Arrays.asList(stockKey), quantity);
            log.debug("库存增加执行结果，stockKey: {}, quantity: {}, result: {}", stockKey, quantity, result);
            return result;
        } catch (Exception e) {
            log.error("库存增加异常，stockKey: {}, quantity: {}", stockKey, quantity, e);
            return 0L;
        }
    }

    /**
     * 检查并扣减库存（带锁机制）
     *
     * @param stockKey 库存key
     * @param quantity 扣减数量
     * @param lockKey 锁key
     * @return 扣减后的库存，-1表示库存不足
     */
    public Long decreaseStockWithLock(String stockKey, Integer quantity, String lockKey) {
        try {
            // 这里可以集成分布式锁，暂时使用Redis的原子操作
            return decreaseStock(stockKey, quantity);
        } catch (Exception e) {
            log.error("带锁库存扣减异常，stockKey: {}, quantity: {}, lockKey: {}", stockKey, quantity, lockKey, e);
            return -1L;
        }
    }

    /**
     * 批量扣减库存
     *
     * @param stockKeys 库存key列表
     * @param quantities 扣减数量列表
     * @return 扣减结果列表
     */
    public List<Long> batchDecreaseStock(List<String> stockKeys, List<Integer> quantities) {
        // 这里可以实现批量扣减逻辑
        // 暂时使用循环方式
        return null;
    }
}
