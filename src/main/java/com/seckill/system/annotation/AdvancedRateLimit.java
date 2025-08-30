package com.seckill.system.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 高级限流注解
 * 
 * 支持多维度限流、动态配置和多种限流算法
 * 
 * @author seckill-system
 * @version 2.0.0
 * @since 2025-08-28
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdvancedRateLimit {

    /**
     * 限流key（支持SpEL表达式）
     */
    String key() default "";

    /**
     * 限流算法类型
     */
    Algorithm algorithm() default Algorithm.SLIDING_WINDOW;

    /**
     * 滑动窗口大小（秒）
     */
    int windowSize() default 60;

    /**
     * 最大请求数
     */
    int limit() default 100;

    /**
     * 令牌桶容量
     */
    int capacity() default 100;

    /**
     * 令牌生成速率（个/秒）
     */
    double rate() default 10.0;

    /**
     * 请求令牌数
     */
    int tokens() default 1;

    /**
     * 是否启用用户维度限流
     */
    boolean enableUserLimit() default true;

    /**
     * 用户维度限流倍数
     */
    double userLimitMultiplier() default 0.1;

    /**
     * 是否启用IP维度限流
     */
    boolean enableIpLimit() default true;

    /**
     * IP维度限流倍数
     */
    double ipLimitMultiplier() default 0.2;

    /**
     * 限流策略来源
     */
    StrategySource strategySource() default StrategySource.ANNOTATION;

    /**
     * 策略配置key（当strategySource为CONFIG时使用）
     */
    String strategyKey() default "";

    /**
     * 限流失败时的处理策略
     */
    BlockStrategy blockStrategy() default BlockStrategy.THROW_EXCEPTION;

    /**
     * 限流失败时的错误消息
     */
    String errorMessage() default "请求过于频繁，请稍后重试";

    /**
     * 限流算法枚举
     */
    enum Algorithm {
        /**
         * 滑动窗口
         */
        SLIDING_WINDOW,
        
        /**
         * 令牌桶
         */
        TOKEN_BUCKET,
        
        /**
         * 计数器（兼容旧版本）
         */
        COUNTER
    }

    /**
     * 策略来源枚举
     */
    enum StrategySource {
        /**
         * 注解配置
         */
        ANNOTATION,
        
        /**
         * 配置文件
         */
        CONFIG,
        
        /**
         * 配置中心
         */
        CONFIG_CENTER
    }

    /**
     * 限流失败处理策略枚举
     */
    enum BlockStrategy {
        /**
         * 抛出异常
         */
        THROW_EXCEPTION,
        
        /**
         * 返回错误结果
         */
        RETURN_ERROR,
        
        /**
         * 等待重试
         */
        WAIT_RETRY,
        
        /**
         * 降级处理
         */
        FALLBACK
    }
}
