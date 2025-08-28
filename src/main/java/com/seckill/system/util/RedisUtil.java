package com.seckill.system.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis缓存工具类
 * 
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Component
public class RedisUtil {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 设置缓存
     * 
     * @param key 缓存键
     * @param value 缓存值
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }
    
    /**
     * 设置缓存并设置过期时间
     * 
     * @param key 缓存键
     * @param value 缓存值
     * @param timeout 过期时间
     * @param unit 时间单位
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }
    
    /**
     * 获取缓存
     * 
     * @param key 缓存键
     * @return 缓存值
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }
    
    /**
     * 删除缓存
     * 
     * @param key 缓存键
     * @return 是否删除成功
     */
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }
    
    /**
     * 批量删除缓存
     * 
     * @param keys 缓存键集合
     * @return 删除成功的数量
     */
    public Long delete(List<String> keys) {
        return redisTemplate.delete(keys);
    }
    
    /**
     * 判断缓存是否存在
     * 
     * @param key 缓存键
     * @return 是否存在
     */
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }
    
    /**
     * 设置过期时间
     * 
     * @param key 缓存键
     * @param timeout 过期时间
     * @param unit 时间单位
     * @return 是否设置成功
     */
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }
    
    /**
     * 获取过期时间
     * 
     * @param key 缓存键
     * @return 过期时间（秒）
     */
    public Long getExpire(String key) {
        return redisTemplate.getExpire(key);
    }
    
    /**
     * 原子递增
     * 
     * @param key 缓存键
     * @param delta 增量
     * @return 递增后的值
     */
    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }
    
    /**
     * 原子递减
     * 
     * @param key 缓存键
     * @param delta 减量
     * @return 递减后的值
     */
    public Long decrement(String key, long delta) {
        return redisTemplate.opsForValue().decrement(key, delta);
    }
    
    /**
     * 设置Hash缓存
     * 
     * @param key 缓存键
     * @param hashKey Hash键
     * @param value Hash值
     */
    public void hSet(String key, String hashKey, Object value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }
    
    /**
     * 获取Hash缓存
     * 
     * @param key 缓存键
     * @param hashKey Hash键
     * @return Hash值
     */
    public Object hGet(String key, String hashKey) {
        return redisTemplate.opsForHash().get(key, hashKey);
    }
    
    /**
     * 获取Hash所有键值对
     * 
     * @param key 缓存键
     * @return Hash所有键值对
     */
    public Map<Object, Object> hGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }
    
    /**
     * 删除Hash缓存
     * 
     * @param key 缓存键
     * @param hashKeys Hash键集合
     * @return 删除成功的数量
     */
    public Long hDelete(String key, Object... hashKeys) {
        return redisTemplate.opsForHash().delete(key, hashKeys);
    }
    
    /**
     * 判断Hash缓存是否存在
     * 
     * @param key 缓存键
     * @param hashKey Hash键
     * @return 是否存在
     */
    public Boolean hHasKey(String key, String hashKey) {
        return redisTemplate.opsForHash().hasKey(key, hashKey);
    }
    
    /**
     * 原子递增Hash值
     * 
     * @param key 缓存键
     * @param hashKey Hash键
     * @param delta 增量
     * @return 递增后的值
     */
    public Long hIncrement(String key, String hashKey, long delta) {
        return redisTemplate.opsForHash().increment(key, hashKey, delta);
    }
    
    /**
     * 原子递减Hash值
     * 
     * @param key 缓存键
     * @param hashKey Hash键
     * @param delta 减量
     * @return 递减后的值
     */
    public Long hDecrement(String key, String hashKey, long delta) {
        return redisTemplate.opsForHash().increment(key, hashKey, -delta);
    }
    
    /**
     * 设置List缓存
     * 
     * @param key 缓存键
     * @param value 缓存值
     * @return List长度
     */
    public Long lPush(String key, Object value) {
        return redisTemplate.opsForList().leftPush(key, value);
    }
    
    /**
     * 获取List缓存
     * 
     * @param key 缓存键
     * @param start 开始索引
     * @param end 结束索引
     * @return List值
     */
    public List<Object> lRange(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }
    
    /**
     * 设置Set缓存
     * 
     * @param key 缓存键
     * @param values 缓存值集合
     * @return 添加成功的数量
     */
    public Long sAdd(String key, Object... values) {
        return redisTemplate.opsForSet().add(key, values);
    }
    
    /**
     * 获取Set缓存
     * 
     * @param key 缓存键
     * @return Set值
     */
    public Set<Object> sMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }
    
    /**
     * 判断Set缓存是否包含指定值
     * 
     * @param key 缓存键
     * @param value 指定值
     * @return 是否包含
     */
    public Boolean sIsMember(String key, Object value) {
        return redisTemplate.opsForSet().isMember(key, value);
    }
}
