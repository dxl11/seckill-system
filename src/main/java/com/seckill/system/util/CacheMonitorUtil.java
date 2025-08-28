package com.seckill.system.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 缓存监控工具类
 *
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Component
@Slf4j
public class CacheMonitorUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 获取缓存统计信息
     *
     * @return 缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        try {
            // 获取Redis服务器信息
            Properties info = redisTemplate.getConnectionFactory().getConnection().info();
            
            // 内存使用情况
            stats.put("usedMemory", info.getProperty("used_memory_human"));
            stats.put("usedMemoryPeak", info.getProperty("used_memory_peak_human"));
            stats.put("maxMemory", info.getProperty("maxmemory_human"));
            
            // 连接数
            stats.put("connectedClients", info.getProperty("connected_clients"));
            stats.put("blockedClients", info.getProperty("blocked_clients"));
            
            // 命令统计
            stats.put("totalCommandsProcessed", info.getProperty("total_commands_processed"));
            stats.put("totalConnectionsReceived", info.getProperty("total_connections_received"));
            
            // 命中率统计
            stats.put("keyspaceHits", info.getProperty("keyspace_hits"));
            stats.put("keyspaceMisses", info.getProperty("keyspace_misses"));
            
            // 计算命中率
            long hits = Long.parseLong(info.getProperty("keyspace_hits", "0"));
            long misses = Long.parseLong(info.getProperty("keyspace_misses", "0"));
            double hitRate = (hits + misses) > 0 ? (double) hits / (hits + misses) * 100 : 0;
            stats.put("hitRate", String.format("%.2f%%", hitRate));
            
            log.debug("获取缓存统计信息成功");
            
        } catch (Exception e) {
            log.error("获取缓存统计信息异常", e);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }

    /**
     * 获取指定模式的key数量
     *
     * @param pattern key模式
     * @return key数量
     */
    public long getKeyCount(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.error("获取key数量异常，pattern: {}", pattern, e);
            return 0;
        }
    }

    /**
     * 获取缓存大小分布
     *
     * @return 缓存大小分布
     */
    public Map<String, Long> getCacheSizeDistribution() {
        Map<String, Long> distribution = new HashMap<>();
        
        try {
            // 秒杀相关缓存
            distribution.put("seckill:stock", getKeyCount("seckill:stock:*"));
            distribution.put("seckill:user", getKeyCount("seckill:user:*"));
            distribution.put("seckill:product", getKeyCount("seckill:product:*"));
            distribution.put("seckill:lock", getKeyCount("seckill:lock:*"));
            
            // 限流相关缓存
            distribution.put("rate:limit", getKeyCount("rate:limit:*"));
            
            // 其他缓存
            distribution.put("other", getKeyCount("*") - distribution.values().stream().mapToLong(Long::longValue).sum());
            
        } catch (Exception e) {
            log.error("获取缓存大小分布异常", e);
        }
        
        return distribution;
    }

    /**
     * 获取缓存过期时间分布
     *
     * @param pattern key模式
     * @return 过期时间分布
     */
    public Map<String, Long> getExpireTimeDistribution(String pattern) {
        Map<String, Long> distribution = new HashMap<>();
        
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null) {
                for (String key : keys) {
                    Long expireTime = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                    if (expireTime != null && expireTime > 0) {
                        String range = getExpireTimeRange(expireTime);
                        distribution.put(range, distribution.getOrDefault(range, 0L) + 1);
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取过期时间分布异常，pattern: {}", pattern, e);
        }
        
        return distribution;
    }

    /**
     * 获取过期时间范围
     *
     * @param expireTime 过期时间（秒）
     * @return 时间范围描述
     */
    private String getExpireTimeRange(Long expireTime) {
        if (expireTime < 60) {
            return "1分钟内";
        } else if (expireTime < 3600) {
            return "1小时内";
        } else if (expireTime < 86400) {
            return "1天内";
        } else if (expireTime < 604800) {
            return "1周内";
        } else {
            return "1周以上";
        }
    }

    /**
     * 清理过期缓存
     *
     * @param pattern key模式
     * @return 清理的key数量
     */
    public long cleanExpiredCache(String pattern) {
        long count = 0;
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null) {
                for (String key : keys) {
                    if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
                        redisTemplate.delete(key);
                        count++;
                    }
                }
            }
            log.info("清理过期缓存完成，pattern: {}, 清理数量: {}", pattern, count);
        } catch (Exception e) {
            log.error("清理过期缓存异常，pattern: {}", pattern, e);
        }
        return count;
    }

    /**
     * 预热缓存
     *
     * @param key 缓存key
     * @param value 缓存值
     * @param expireTime 过期时间
     * @param timeUnit 时间单位
     * @return 是否预热成功
     */
    public boolean warmUpCache(String key, Object value, long expireTime, TimeUnit timeUnit) {
        try {
            redisTemplate.opsForValue().set(key, value, expireTime, timeUnit);
            log.debug("缓存预热成功，key: {}, expireTime: {}", key, expireTime);
            return true;
        } catch (Exception e) {
            log.error("缓存预热异常，key: {}", key, e);
            return false;
        }
    }

    /**
     * 批量预热缓存
     *
     * @param cacheData 缓存数据
     * @param expireTime 过期时间
     * @param timeUnit 时间单位
     * @return 预热成功的数量
     */
    public long batchWarmUpCache(Map<String, Object> cacheData, long expireTime, TimeUnit timeUnit) {
        long successCount = 0;
        try {
            for (Map.Entry<String, Object> entry : cacheData.entrySet()) {
                if (warmUpCache(entry.getKey(), entry.getValue(), expireTime, timeUnit)) {
                    successCount++;
                }
            }
            log.info("批量缓存预热完成，总数: {}, 成功: {}", cacheData.size(), successCount);
        } catch (Exception e) {
            log.error("批量缓存预热异常", e);
        }
        return successCount;
    }

    /**
     * 获取缓存性能报告
     *
     * @return 性能报告
     */
    public Map<String, Object> getPerformanceReport() {
        Map<String, Object> report = new HashMap<>();
        
        try {
            // 基础统计信息
            report.put("cacheStats", getCacheStats());
            
            // 缓存大小分布
            report.put("sizeDistribution", getCacheSizeDistribution());
            
            // 秒杀缓存过期时间分布
            report.put("seckillExpireDistribution", getExpireTimeDistribution("seckill:*"));
            
            // 限流缓存过期时间分布
            report.put("rateLimitExpireDistribution", getExpireTimeDistribution("rate:limit:*"));
            
            // 建议
            report.put("recommendations", generateRecommendations(report));
            
        } catch (Exception e) {
            log.error("生成性能报告异常", e);
            report.put("error", e.getMessage());
        }
        
        return report;
    }

    /**
     * 生成优化建议
     *
     * @param report 性能报告
     * @return 优化建议列表
     */
    private List<String> generateRecommendations(Map<String, Object> report) {
        List<String> recommendations = new ArrayList<>();
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> cacheStats = (Map<String, Object>) report.get("cacheStats");
            
            if (cacheStats != null) {
                // 内存使用建议
                String usedMemory = (String) cacheStats.get("usedMemory");
                if (usedMemory != null && usedMemory.contains("MB") && 
                    Integer.parseInt(usedMemory.replace("MB", "")) > 100) {
                    recommendations.add("建议增加Redis内存或优化缓存策略");
                }
                
                // 命中率建议
                String hitRate = (String) cacheStats.get("hitRate");
                if (hitRate != null) {
                    double rate = Double.parseDouble(hitRate.replace("%", ""));
                    if (rate < 80) {
                        recommendations.add("缓存命中率较低，建议优化缓存策略和预热机制");
                    }
                }
            }
            
            // 默认建议
            if (recommendations.isEmpty()) {
                recommendations.add("缓存运行正常，建议定期监控");
            }
            
        } catch (Exception e) {
            log.error("生成优化建议异常", e);
            recommendations.add("无法生成建议，请检查系统状态");
        }
        
        return recommendations;
    }
}
