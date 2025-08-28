package com.seckill.system.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流注解
 * 
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    
    /**
     * 限流标识，支持SpEL表达式
     * 
     * @return 限流标识
     */
    String key() default "";
    
    /**
     * 每秒允许的请求数
     * 
     * @return QPS限制
     */
    double qps() default 100.0;
    
    /**
     * 是否阻塞等待
     * 
     * @return 是否阻塞
     */
    boolean block() default false;
    
    /**
     * 超时时间（毫秒），仅在非阻塞模式下有效
     * 
     * @return 超时时间
     */
    long timeout() default 1000L;
}
