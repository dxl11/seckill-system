package com.seckill.system.aspect;

import com.seckill.system.entity.Result;
import com.seckill.system.exception.BusinessException;
import com.seckill.system.util.DistributedRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 分布式限流切面
 *
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Aspect
@Component
@Order(1)
@Slf4j
public class DistributedRateLimitAspect {

    @Autowired
    private DistributedRateLimiter rateLimiter;

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 环绕通知，实现限流逻辑
     */
    @Around("@annotation(distributedRateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedRateLimit distributedRateLimit) throws Throwable {
        try {
            // 解析限流key
            String key = resolveKey(joinPoint, distributedRateLimit);
            
            // 执行限流检查
            boolean acquired = executeRateLimit(key, distributedRateLimit);
            
            if (acquired) {
                // 限流通过，执行原方法
                return joinPoint.proceed();
            } else {
                // 限流失败，返回错误信息
                log.warn("限流触发，key: {}, limit: {}, window: {}", 
                        key, distributedRateLimit.limit(), distributedRateLimit.window());
                
                if (distributedRateLimit.code() > 0) {
                    throw new BusinessException(distributedRateLimit.code(), distributedRateLimit.message());
                } else {
                    return Result.error(distributedRateLimit.message());
                }
            }
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("限流切面执行异常", e);
            // 异常情况下默认放行，避免影响业务
            return joinPoint.proceed();
        }
    }

    /**
     * 解析限流key
     */
    private String resolveKey(ProceedingJoinPoint joinPoint, DistributedRateLimit distributedRateLimit) {
        String key = distributedRateLimit.key();
        
        if (key.isEmpty()) {
            // 如果没有指定key，使用默认的key
            String methodName = joinPoint.getSignature().getName();
            String className = joinPoint.getTarget().getClass().getSimpleName();
            key = "rate:limit:" + className + ":" + methodName;
        } else if (key.contains("#")) {
            // 如果包含SpEL表达式，进行解析
            try {
                key = parseSpEL(key, joinPoint);
            } catch (Exception e) {
                log.warn("SpEL表达式解析失败，使用原始key: {}", key, e);
            }
        }
        
        return key;
    }

    /**
     * 解析SpEL表达式
     */
    private String parseSpEL(String spEL, ProceedingJoinPoint joinPoint) {
        try {
            // 获取方法参数名
            Method method = getMethod(joinPoint);
            String[] paramNames = getParameterNames(method);
            Object[] args = joinPoint.getArgs();
            
            // 创建评估上下文
            EvaluationContext context = new StandardEvaluationContext();
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
            
            // 解析表达式
            Expression expression = parser.parseExpression(spEL);
            Object result = expression.getValue(context);
            
            return result != null ? result.toString() : spEL;
            
        } catch (Exception e) {
            log.warn("SpEL表达式解析异常: {}", spEL, e);
            return spEL;
        }
    }

    /**
     * 执行限流逻辑
     */
    private boolean executeRateLimit(String key, DistributedRateLimit distributedRateLimit) {
        if (distributedRateLimit.block()) {
            // 阻塞模式
            return rateLimiter.acquire(key, distributedRateLimit.limit(), 
                    distributedRateLimit.window(), distributedRateLimit.timeout());
        } else {
            // 非阻塞模式
            return rateLimiter.tryAcquire(key, distributedRateLimit.limit(), 
                    distributedRateLimit.window());
        }
    }

    /**
     * 获取方法对象
     */
    private Method getMethod(ProceedingJoinPoint joinPoint) throws NoSuchMethodException {
        String methodName = joinPoint.getSignature().getName();
        Class<?>[] parameterTypes = ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getParameterTypes();
        return joinPoint.getTarget().getClass().getMethod(methodName, parameterTypes);
    }

    /**
     * 获取参数名（简化实现）
     */
    private String[] getParameterNames(Method method) {
        // 这里简化实现，实际可以使用反射或其他方式获取真实的参数名
        int paramCount = method.getParameterCount();
        String[] names = new String[paramCount];
        for (int i = 0; i < paramCount; i++) {
            names[i] = "arg" + i;
        }
        return names;
    }
}
