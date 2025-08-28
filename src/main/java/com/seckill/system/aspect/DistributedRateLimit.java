package com.seckill.system.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分布式限流注解
 *
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedRateLimit {

    /**
     * 限流key，支持SpEL表达式
     * 例如：'rate:limit:' + #userId
     */
    String key() default "";

    /**
     * 限制次数
     */
    int limit() default 100;

    /**
     * 时间窗口（秒）
     */
    int window() default 60;

    /**
     * 是否阻塞等待
     */
    boolean block() default false;

    /**
     * 阻塞超时时间（毫秒）
     */
    long timeout() default 1000;

    /**
     * 限流失败时的错误消息
     */
    String message() default "请求过于频繁，请稍后重试";

    /**
     * 限流失败时的错误码
     */
    int code() default 429;
}
