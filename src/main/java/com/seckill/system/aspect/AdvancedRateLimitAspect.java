package com.seckill.system.aspect;

import com.seckill.system.annotation.AdvancedRateLimit;
import com.seckill.system.config.RateLimitConfig;
import com.seckill.system.exception.BusinessException;
import com.seckill.system.util.SlidingWindowRateLimiter;
import com.seckill.system.util.TokenBucketRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 高级限流切面
 * 
 * 支持多维度限流、动态配置和多种限流算法
 * 
 * @author seckill-system
 * @version 2.0.0
 * @since 2025-08-28
 */
@Aspect
@Component
@Slf4j
public class AdvancedRateLimitAspect {

    @Autowired
    private SlidingWindowRateLimiter slidingWindowRateLimiter;

    @Autowired
    private TokenBucketRateLimiter tokenBucketRateLimiter;

    @Autowired
    private RateLimitConfig rateLimitConfig;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 环绕通知，实现限流逻辑
     */
    @Around("@annotation(advancedRateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, AdvancedRateLimit advancedRateLimit) throws Throwable {
        try {
            // 1. 解析限流key
            String rateLimitKey = resolveRateLimitKey(joinPoint, advancedRateLimit);
            
            // 2. 获取限流策略
            RateLimitConfig.DefaultStrategy strategy = getRateLimitStrategy(advancedRateLimit);
            
            // 3. 执行限流检查
            if (!checkRateLimit(rateLimitKey, strategy, advancedRateLimit)) {
                return handleRateLimitExceeded(advancedRateLimit);
            }
            
            // 4. 执行用户维度限流
            if (strategy.isEnableUserLimit()) {
                Long userId = getUserId(joinPoint);
                if (userId != null && !checkUserRateLimit(rateLimitKey, userId, strategy)) {
                    return handleRateLimitExceeded(advancedRateLimit);
                }
            }
            
            // 5. 执行IP维度限流
            if (strategy.isEnableIpLimit()) {
                String ip = getClientIp();
                if (ip != null && !checkIpRateLimit(rateLimitKey, ip, strategy)) {
                    return handleRateLimitExceeded(advancedRateLimit);
                }
            }
            
            // 6. 执行原方法
            return joinPoint.proceed();
            
        } catch (Exception e) {
            log.error("限流切面执行异常", e);
            // 限流异常时默认放行，避免影响业务
            return joinPoint.proceed();
        }
    }

    /**
     * 解析限流key
     */
    private String resolveRateLimitKey(ProceedingJoinPoint joinPoint, AdvancedRateLimit advancedRateLimit) {
        String key = advancedRateLimit.key();
        if (key.isEmpty()) {
            // 默认使用类名+方法名作为key
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            return method.getDeclaringClass().getSimpleName() + ":" + method.getName();
        }
        
        try {
            // 解析SpEL表达式
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            String[] paramNames = nameDiscoverer.getParameterNames(method);
            Object[] args = joinPoint.getArgs();
            
            EvaluationContext context = new StandardEvaluationContext();
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    context.setVariable(paramNames[i], args[i]);
                }
            }
            
            Expression expression = parser.parseExpression(key);
            Object result = expression.getValue(context);
            return result != null ? result.toString() : key;
            
        } catch (Exception e) {
            log.warn("解析限流key异常，使用原始key: {}", key, e);
            return key;
        }
    }

    /**
     * 获取限流策略
     */
    private RateLimitConfig.DefaultStrategy getRateLimitStrategy(AdvancedRateLimit advancedRateLimit) {
        if (advancedRateLimit.strategySource() == AdvancedRateLimit.StrategySource.CONFIG) {
            // 从配置文件获取策略
            String strategyKey = advancedRateLimit.strategyKey();
            if (!strategyKey.isEmpty()) {
                // 这里可以根据strategyKey从配置中获取具体策略
                // 暂时返回默认策略
            }
        }
        
        // 从注解获取策略
        RateLimitConfig.DefaultStrategy strategy = new RateLimitConfig.DefaultStrategy();
        strategy.setAlgorithm(advancedRateLimit.algorithm().name());
        strategy.setWindowSize(advancedRateLimit.windowSize());
        strategy.setLimit(advancedRateLimit.limit());
        strategy.setCapacity(advancedRateLimit.capacity());
        strategy.setRate(advancedRateLimit.rate());
        strategy.setTokens(advancedRateLimit.tokens());
        strategy.setEnableUserLimit(advancedRateLimit.enableUserLimit());
        strategy.setUserLimitMultiplier(advancedRateLimit.userLimitMultiplier());
        strategy.setEnableIpLimit(advancedRateLimit.enableIpLimit());
        strategy.setIpLimitMultiplier(advancedRateLimit.ipLimitMultiplier());
        
        return strategy;
    }

    /**
     * 执行限流检查
     */
    private boolean checkRateLimit(String key, RateLimitConfig.DefaultStrategy strategy, AdvancedRateLimit advancedRateLimit) {
        try {
            switch (AdvancedRateLimit.Algorithm.valueOf(strategy.getAlgorithm())) {
                case SLIDING_WINDOW:
                    return slidingWindowRateLimiter.tryAcquire(key, strategy.getWindowSize(), strategy.getLimit());
                    
                case TOKEN_BUCKET:
                    return tokenBucketRateLimiter.tryAcquire(key, strategy.getCapacity(), strategy.getRate(), strategy.getTokens());
                    
                case COUNTER:
                    // 兼容旧版本的计数器限流
                    return checkCounterRateLimit(key, strategy);
                    
                default:
                    log.warn("不支持的限流算法: {}", strategy.getAlgorithm());
                    return true;
            }
        } catch (Exception e) {
            log.error("限流检查异常，key: {}", key, e);
            return true;
        }
    }

    /**
     * 执行用户维度限流检查
     */
    private boolean checkUserRateLimit(String key, Long userId, RateLimitConfig.DefaultStrategy strategy) {
        try {
            int userLimit = (int) (strategy.getLimit() * strategy.getUserLimitMultiplier());
            switch (AdvancedRateLimit.Algorithm.valueOf(strategy.getAlgorithm())) {
                case SLIDING_WINDOW:
                    return slidingWindowRateLimiter.tryAcquire(key, userId, strategy.getWindowSize(), userLimit);
                    
                case TOKEN_BUCKET:
                    return tokenBucketRateLimiter.tryAcquire(key, userId, strategy.getCapacity(), 
                        strategy.getRate() * strategy.getUserLimitMultiplier(), strategy.getTokens());
                    
                default:
                    return true;
            }
        } catch (Exception e) {
            log.error("用户维度限流检查异常，key: {}, userId: {}", key, userId, e);
            return true;
        }
    }

    /**
     * 执行IP维度限流检查
     */
    private boolean checkIpRateLimit(String key, String ip, RateLimitConfig.DefaultStrategy strategy) {
        try {
            int ipLimit = (int) (strategy.getLimit() * strategy.getIpLimitMultiplier());
            switch (AdvancedRateLimit.Algorithm.valueOf(strategy.getAlgorithm())) {
                case SLIDING_WINDOW:
                    return slidingWindowRateLimiter.tryAcquire(key, ip, strategy.getWindowSize(), ipLimit);
                    
                case TOKEN_BUCKET:
                    return tokenBucketRateLimiter.tryAcquire(key, ip, strategy.getCapacity(), 
                        strategy.getRate() * strategy.getIpLimitMultiplier(), strategy.getTokens());
                    
                default:
                    return true;
            }
        } catch (Exception e) {
            log.error("IP维度限流检查异常，key: {}, ip: {}", key, ip, e);
            return true;
        }
    }

    /**
     * 兼容旧版本的计数器限流
     */
    private boolean checkCounterRateLimit(String key, RateLimitConfig.DefaultStrategy strategy) {
        // 这里可以调用原有的计数器限流逻辑
        // 暂时返回true，避免影响现有功能
        return true;
    }

    /**
     * 获取用户ID
     */
    private Long getUserId(ProceedingJoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            for (Object arg : args) {
                if (arg instanceof Long) {
                    return (Long) arg;
                }
            }
        } catch (Exception e) {
            log.debug("获取用户ID异常", e);
        }
        return null;
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("Proxy-Client-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("WL-Proxy-Client-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                return ip;
            }
        } catch (Exception e) {
            log.debug("获取客户端IP异常", e);
        }
        return null;
    }

    /**
     * 处理限流超限
     */
    private Object handleRateLimitExceeded(AdvancedRateLimit advancedRateLimit) throws Throwable {
        log.warn("请求被限流，策略: {}", advancedRateLimit.blockStrategy());
        
        switch (advancedRateLimit.blockStrategy()) {
            case THROW_EXCEPTION:
                throw new BusinessException(advancedRateLimit.errorMessage());
                
            case RETURN_ERROR:
                // 返回错误结果，这里需要根据具体业务场景调整
                throw new BusinessException(advancedRateLimit.errorMessage());
                
            case WAIT_RETRY:
                // 等待重试，这里可以实现等待逻辑
                try {
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new BusinessException(advancedRateLimit.errorMessage());
                
            case FALLBACK:
                // 降级处理，这里可以实现降级逻辑
                throw new BusinessException(advancedRateLimit.errorMessage());
                
            default:
                throw new BusinessException(advancedRateLimit.errorMessage());
        }
    }
}
