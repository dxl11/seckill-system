package com.seckill.system.aspect;

import com.seckill.system.annotation.AdvancedRateLimit;
import com.seckill.system.config.RateLimitConfig;
import com.seckill.system.util.SlidingWindowRateLimiter;
import com.seckill.system.util.TokenBucketRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
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

/**
 * 高级限流切面
 * 
 * 实现@AdvancedRateLimit注解的限流逻辑
 * 
 * @author seckill-system
 * @version 1.0.0
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
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 环绕通知，实现限流逻辑
     */
    @Around("@annotation(com.seckill.system.annotation.AdvancedRateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        AdvancedRateLimit annotation = method.getAnnotation(AdvancedRateLimit.class);

        try {
            // 1. 解析限流key
            String rateLimitKey = parseRateLimitKey(annotation.key(), method, joinPoint.getArgs());
            
            // 2. 执行限流检查
            boolean allowed = checkRateLimit(annotation, rateLimitKey);
            
            if (!allowed) {
                // 限流失败，根据策略处理
                return handleRateLimitFailure(annotation);
            }

            // 3. 限流通过，执行原方法
            return joinPoint.proceed();

        } catch (Exception e) {
            log.error("限流切面异常", e);
            // 异常时默认放行，避免影响业务
            return joinPoint.proceed();
        }
    }

    /**
     * 解析限流key（支持SpEL表达式）
     */
    private String parseRateLimitKey(String keyExpression, Method method, Object[] args) {
        if (keyExpression == null || keyExpression.trim().isEmpty()) {
            // 如果没有指定key，使用默认key
            return "default:rate:limit:" + method.getDeclaringClass().getSimpleName() + ":" + method.getName();
        }

        try {
            // 获取方法参数名
            String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
            if (parameterNames == null) {
                return keyExpression;
            }

            // 创建SpEL上下文
            EvaluationContext context = new StandardEvaluationContext();
            for (int i = 0; i < parameterNames.length && i < args.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }

            // 解析SpEL表达式
            Expression expression = parser.parseExpression(keyExpression);
            Object result = expression.getValue(context);
            return result != null ? result.toString() : keyExpression;

        } catch (Exception e) {
            log.warn("解析限流key异常，使用原始表达式: {}", keyExpression, e);
            return keyExpression;
        }
    }

    /**
     * 执行限流检查
     */
    private boolean checkRateLimit(AdvancedRateLimit annotation, String rateLimitKey) {
        try {
            // 根据算法类型选择限流器
            switch (annotation.algorithm()) {
                case SLIDING_WINDOW:
                    return checkSlidingWindowLimit(annotation, rateLimitKey);
                case TOKEN_BUCKET:
                    return checkTokenBucketLimit(annotation, rateLimitKey);
                case COUNTER:
                    return checkCounterLimit(annotation, rateLimitKey);
                default:
                    log.warn("不支持的限流算法: {}", annotation.algorithm());
                    return true;
            }
        } catch (Exception e) {
            log.error("限流检查异常", e);
            return true; // 异常时默认放行
        }
    }

    /**
     * 滑动窗口限流检查
     */
    private boolean checkSlidingWindowLimit(AdvancedRateLimit annotation, String rateLimitKey) {
        boolean allowed = slidingWindowRateLimiter.tryAcquire(rateLimitKey, annotation.windowSize(), annotation.limit());
        
        // 检查用户维度限流
        if (allowed && annotation.enableUserLimit()) {
            String userKey = rateLimitKey + ":user:" + getCurrentUserId();
            int userLimit = (int) (annotation.limit() * annotation.userLimitMultiplier());
            allowed = slidingWindowRateLimiter.tryAcquire(userKey, annotation.windowSize(), userLimit);
        }

        // 检查IP维度限流
        if (allowed && annotation.enableIpLimit()) {
            String ipKey = rateLimitKey + ":ip:" + getCurrentUserIp();
            int ipLimit = (int) (annotation.limit() * annotation.ipLimitMultiplier());
            allowed = slidingWindowRateLimiter.tryAcquire(ipKey, annotation.windowSize(), ipLimit);
        }

        return allowed;
    }

    /**
     * 令牌桶限流检查
     */
    private boolean checkTokenBucketLimit(AdvancedRateLimit annotation, String rateLimitKey) {
        boolean allowed = tokenBucketRateLimiter.tryAcquire(rateLimitKey, annotation.capacity(), annotation.rate(), annotation.tokens());
        
        // 检查用户维度限流
        if (allowed && annotation.enableUserLimit()) {
            String userKey = rateLimitKey + ":user:" + getCurrentUserId();
            int userCapacity = (int) (annotation.capacity() * annotation.userLimitMultiplier());
            double userRate = annotation.rate() * annotation.userLimitMultiplier();
            allowed = tokenBucketRateLimiter.tryAcquire(userKey, userCapacity, userRate, annotation.tokens());
        }

        // 检查IP维度限流
        if (allowed && annotation.enableIpLimit()) {
            String ipKey = rateLimitKey + ":ip:" + getCurrentUserIp();
            int ipCapacity = (int) (annotation.capacity() * annotation.ipLimitMultiplier());
            double ipRate = annotation.rate() * annotation.ipLimitMultiplier();
            allowed = tokenBucketRateLimiter.tryAcquire(ipKey, ipCapacity, ipRate, annotation.tokens());
        }

        return allowed;
    }

    /**
     * 计数器限流检查（兼容旧版本）
     */
    private boolean checkCounterLimit(AdvancedRateLimit annotation, String rateLimitKey) {
        // 使用滑动窗口实现计数器限流
        return checkSlidingWindowLimit(annotation, rateLimitKey);
    }

    /**
     * 处理限流失败
     */
    private Object handleRateLimitFailure(AdvancedRateLimit annotation) throws Exception {
        switch (annotation.blockStrategy()) {
            case THROW_EXCEPTION:
                throw new RuntimeException(annotation.errorMessage());
            case RETURN_ERROR:
                // 返回错误结果，这里需要根据实际返回类型处理
                log.warn("限流触发，返回错误: {}", annotation.errorMessage());
                return null;
            case WAIT_RETRY:
                // 等待重试，这里可以实现等待逻辑
                log.warn("限流触发，等待重试: {}", annotation.errorMessage());
                Thread.sleep(1000); // 简单等待1秒
                return null;
            case FALLBACK:
                // 降级处理，这里可以实现降级逻辑
                log.warn("限流触发，执行降级: {}", annotation.errorMessage());
                return null;
            default:
                throw new RuntimeException(annotation.errorMessage());
        }
    }

    /**
     * 获取当前用户ID
     */
    private String getCurrentUserId() {
        try {
            // 这里应该从JWT Token或Session中获取用户ID
            // 暂时返回默认值
            return "default";
        } catch (Exception e) {
            log.warn("获取用户ID失败", e);
            return "unknown";
        }
    }

    /**
     * 获取当前用户IP
     */
    private String getCurrentUserIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("Proxy-Client-IP");
                }
                if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("WL-Proxy-Client-IP");
                }
                if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("HTTP_CLIENT_IP");
                }
                if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("HTTP_X_FORWARDED_FOR");
                }
                if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                return ip;
            }
        } catch (Exception e) {
            log.warn("获取用户IP失败", e);
        }
        return "unknown";
    }
}
