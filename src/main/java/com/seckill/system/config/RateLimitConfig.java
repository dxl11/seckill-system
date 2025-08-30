package com.seckill.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 限流配置管理器
 * 
 * 支持动态配置和多维度限流策略，可通过配置文件或配置中心动态调整
 * 
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Component
@ConfigurationProperties(prefix = "seckill.rate-limit")
@Data
public class RateLimitConfig {

    /**
     * 默认限流策略
     */
    private DefaultStrategy defaultStrategy = new DefaultStrategy();

    /**
     * 商品维度限流策略
     */
    private Map<String, ProductStrategy> products = new HashMap<>();

    /**
     * 用户维度限流策略
     */
    private Map<String, UserStrategy> users = new HashMap<>();

    /**
     * IP维度限流策略
     */
    private Map<String, IpStrategy> ips = new HashMap<>();

    /**
     * 接口维度限流策略
     */
    private Map<String, ApiStrategy> apis = new HashMap<>();

    /**
     * 默认限流策略
     */
    @Data
    public static class DefaultStrategy {
        /**
         * 限流算法类型：sliding-window, token-bucket
         */
        private String algorithm = "sliding-window";
        
        /**
         * 滑动窗口大小（秒）
         */
        private int windowSize = 60;
        
        /**
         * 最大请求数
         */
        private int limit = 100;
        
        /**
         * 令牌桶容量
         */
        private int capacity = 100;
        
        /**
         * 令牌生成速率（个/秒）
         */
        private double rate = 10.0;
        
        /**
         * 请求令牌数
         */
        private int tokens = 1;
        
        /**
         * 是否启用用户维度限流
         */
        private boolean enableUserLimit = true;
        
        /**
         * 用户维度限流倍数
         */
        private double userLimitMultiplier = 0.1;
        
        /**
         * 是否启用IP维度限流
         */
        private boolean enableIpLimit = true;
        
        /**
         * IP维度限流倍数
         */
        private double ipLimitMultiplier = 0.2;
    }

    /**
     * 商品维度限流策略
     */
    @Data
    public static class ProductStrategy {
        private String algorithm = "sliding-window";
        private int windowSize = 60;
        private int limit = 50;
        private int capacity = 50;
        private double rate = 5.0;
        private int tokens = 1;
        private boolean enableUserLimit = true;
        private double userLimitMultiplier = 0.1;
        private boolean enableIpLimit = true;
        private double ipLimitMultiplier = 0.2;
    }

    /**
     * 用户维度限流策略
     */
    @Data
    public static class UserStrategy {
        private String algorithm = "sliding-window";
        private int windowSize = 60;
        private int limit = 10;
        private int capacity = 10;
        private double rate = 1.0;
        private int tokens = 1;
    }

    /**
     * IP维度限流策略
     */
    @Data
    public static class IpStrategy {
        private String algorithm = "sliding-window";
        private int windowSize = 60;
        private int limit = 20;
        private int capacity = 20;
        private double rate = 2.0;
        private int tokens = 1;
    }

    /**
     * 接口维度限流策略
     */
    @Data
    public static class ApiStrategy {
        private String algorithm = "sliding-window";
        private int windowSize = 60;
        private int limit = 200;
        private int capacity = 200;
        private double rate = 20.0;
        private int tokens = 1;
        private boolean enableUserLimit = true;
        private double userLimitMultiplier = 0.1;
        private boolean enableIpLimit = true;
        private double ipLimitMultiplier = 0.2;
    }

    /**
     * 获取商品限流策略
     */
    public ProductStrategy getProductStrategy(String productId) {
        return products.getOrDefault(productId, new ProductStrategy());
    }

    /**
     * 获取用户限流策略
     */
    public UserStrategy getUserStrategy(String userId) {
        return users.getOrDefault(userId, new UserStrategy());
    }

    /**
     * 获取IP限流策略
     */
    public IpStrategy getIpStrategy(String ip) {
        return ips.getOrDefault(ip, new IpStrategy());
    }

    /**
     * 获取接口限流策略
     */
    public ApiStrategy getApiStrategy(String apiPath) {
        return apis.getOrDefault(apiPath, new ApiStrategy());
    }

    /**
     * 动态更新商品限流策略
     */
    public void updateProductStrategy(String productId, ProductStrategy strategy) {
        products.put(productId, strategy);
    }

    /**
     * 动态更新用户限流策略
     */
    public void updateUserStrategy(String userId, UserStrategy strategy) {
        users.put(userId, strategy);
    }

    /**
     * 动态更新IP限流策略
     */
    public void updateIpStrategy(String ip, IpStrategy strategy) {
        ips.put(ip, strategy);
    }

    /**
     * 动态更新接口限流策略
     */
    public void updateApiStrategy(String apiPath, ApiStrategy strategy) {
        apis.put(apiPath, strategy);
    }

    /**
     * 重置所有限流策略
     */
    public void resetAllStrategies() {
        products.clear();
        users.clear();
        ips.clear();
        apis.clear();
    }
}
