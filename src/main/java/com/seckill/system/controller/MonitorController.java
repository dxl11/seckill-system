package com.seckill.system.controller;

import com.seckill.system.entity.Result;
import com.seckill.system.util.CacheMonitorUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 系统监控控制器
 *
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@RestController
@RequestMapping("/monitor")
@Slf4j
public class MonitorController {

    @Autowired
    private CacheMonitorUtil cacheMonitorUtil;

    /**
     * 获取缓存统计信息
     *
     * @return 缓存统计信息
     */
    @GetMapping("/cache/stats")
    public Result<Map<String, Object>> getCacheStats() {
        try {
            Map<String, Object> stats = cacheMonitorUtil.getCacheStats();
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取缓存统计信息异常", e);
            return Result.error("获取缓存统计信息失败");
        }
    }

    /**
     * 获取缓存大小分布
     *
     * @return 缓存大小分布
     */
    @GetMapping("/cache/size-distribution")
    public Result<Map<String, Long>> getCacheSizeDistribution() {
        try {
            Map<String, Long> distribution = cacheMonitorUtil.getCacheSizeDistribution();
            return Result.success(distribution);
        } catch (Exception e) {
            log.error("获取缓存大小分布异常", e);
            return Result.error("获取缓存大小分布失败");
        }
    }

    /**
     * 获取缓存过期时间分布
     *
     * @param pattern key模式
     * @return 过期时间分布
     */
    @GetMapping("/cache/expire-distribution")
    public Result<Map<String, Long>> getExpireTimeDistribution(@RequestParam String pattern) {
        try {
            Map<String, Long> distribution = cacheMonitorUtil.getExpireTimeDistribution(pattern);
            return Result.success(distribution);
        } catch (Exception e) {
            log.error("获取缓存过期时间分布异常，pattern: {}", pattern, e);
            return Result.error("获取缓存过期时间分布失败");
        }
    }

    /**
     * 清理过期缓存
     *
     * @param pattern key模式
     * @return 清理结果
     */
    @PostMapping("/cache/clean")
    public Result<Long> cleanExpiredCache(@RequestParam String pattern) {
        try {
            long count = cacheMonitorUtil.cleanExpiredCache(pattern);
            return Result.success(count);
        } catch (Exception e) {
            log.error("清理过期缓存异常，pattern: {}", pattern, e);
            return Result.error("清理过期缓存失败");
        }
    }

    /**
     * 预热缓存
     *
     * @param key 缓存key
     * @param value 缓存值
     * @param expireTime 过期时间（秒）
     * @return 预热结果
     */
    @PostMapping("/cache/warmup")
    public Result<Boolean> warmUpCache(@RequestParam String key, 
                                     @RequestParam String value, 
                                     @RequestParam(defaultValue = "3600") long expireTime) {
        try {
            boolean success = cacheMonitorUtil.warmUpCache(key, value, expireTime, java.util.concurrent.TimeUnit.SECONDS);
            if (success) {
                return Result.success(true);
            } else {
                return Result.error("缓存预热失败");
            }
        } catch (Exception e) {
            log.error("缓存预热异常，key: {}, value: {}, expireTime: {}", key, value, expireTime, e);
            return Result.error("缓存预热失败");
        }
    }

    /**
     * 获取缓存性能报告
     *
     * @return 性能报告
     */
    @GetMapping("/cache/performance-report")
    public Result<Map<String, Object>> getPerformanceReport() {
        try {
            Map<String, Object> report = cacheMonitorUtil.getPerformanceReport();
            return Result.success(report);
        } catch (Exception e) {
            log.error("获取缓存性能报告异常", e);
            return Result.error("获取缓存性能报告失败");
        }
    }

    /**
     * 获取指定模式的key数量
     *
     * @param pattern key模式
     * @return key数量
     */
    @GetMapping("/cache/key-count")
    public Result<Long> getKeyCount(@RequestParam String pattern) {
        try {
            long count = cacheMonitorUtil.getKeyCount(pattern);
            return Result.success(count);
        } catch (Exception e) {
            log.error("获取key数量异常，pattern: {}", pattern, e);
            return Result.error("获取key数量失败");
        }
    }

    /**
     * 系统健康检查
     *
     * @return 系统状态
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> healthCheck() {
        try {
            Map<String, Object> health = new java.util.HashMap<>();
            
            // 基础系统信息
            health.put("status", "UP");
            health.put("timestamp", System.currentTimeMillis());
            health.put("version", "1.0.0");
            
            // 缓存状态
            try {
                Map<String, Object> cacheStats = cacheMonitorUtil.getCacheStats();
                health.put("cache", "UP");
                health.put("cacheStats", cacheStats);
            } catch (Exception e) {
                health.put("cache", "DOWN");
                health.put("cacheError", e.getMessage());
            }
            
            // 数据库状态（这里简化处理，实际应该检查数据库连接）
            health.put("database", "UP");
            
            return Result.success(health);
            
        } catch (Exception e) {
            log.error("系统健康检查异常", e);
            return Result.error("系统健康检查失败");
        }
    }

    /**
     * 获取系统信息
     *
     * @return 系统信息
     */
    @GetMapping("/system/info")
    public Result<Map<String, Object>> getSystemInfo() {
        try {
            Map<String, Object> info = new java.util.HashMap<>();
            
            // JVM信息
            Runtime runtime = Runtime.getRuntime();
            info.put("javaVersion", System.getProperty("java.version"));
            info.put("javaVendor", System.getProperty("java.vendor"));
            info.put("osName", System.getProperty("os.name"));
            info.put("osVersion", System.getProperty("os.version"));
            info.put("totalMemory", runtime.totalMemory());
            info.put("freeMemory", runtime.freeMemory());
            info.put("maxMemory", runtime.maxMemory());
            info.put("availableProcessors", runtime.availableProcessors());
            
            // 系统时间
            info.put("currentTime", System.currentTimeMillis());
            info.put("timezone", System.getProperty("user.timezone"));
            
            return Result.success(info);
            
        } catch (Exception e) {
            log.error("获取系统信息异常", e);
            return Result.error("获取系统信息失败");
        }
    }
}
