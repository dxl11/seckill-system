package com.seckill.system.aspect;

import com.seckill.system.exception.BusinessException;
import com.seckill.system.util.RateLimiterUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 限流切面
 * 
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Aspect
@Component
@Slf4j
public class RateLimitAspect {
    
    /**
     * SpEL表达式解析器
     */
    private final ExpressionParser parser = new SpelExpressionParser();
    
    /**
     * 参数名发现器
     */
    private final LocalVariableTableParameterNameDiscoverer discoverer = new LocalVariableTableParameterNameDiscoverer();
    
    /**
     * 定义切点
     */
    @Pointcut("@annotation(com.seckill.system.aspect.RateLimit)")
    public void rateLimitPointcut() {}
    
    /**
     * 环绕通知
     * 
     * @param joinPoint 连接点
     * @return 方法执行结果
     * @throws Throwable 异常
     */
    @Around("rateLimitPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        // 获取限流注解
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return joinPoint.proceed();
        }
        
        // 解析限流标识
        String key = parseKey(rateLimit.key(), method, joinPoint.getArgs());
        if (StringUtils.isEmpty(key)) {
            key = method.getDeclaringClass().getSimpleName() + ":" + method.getName();
        }
        
        // 获取QPS限制
        double qps = rateLimit.qps();
        
        // 执行限流逻辑
        boolean acquired = false;
        if (rateLimit.block()) {
            // 阻塞模式
            double waitTime = RateLimiterUtil.acquire(key, qps);
            if (waitTime > 0) {
                log.info("限流等待时间: {}ms, key: {}", waitTime * 1000, key);
            }
            acquired = true;
        } else {
            // 非阻塞模式
            if (rateLimit.timeout() > 0) {
                acquired = RateLimiterUtil.tryAcquire(key, qps, rateLimit.timeout(), TimeUnit.MILLISECONDS);
            } else {
                acquired = RateLimiterUtil.tryAcquire(key, qps);
            }
        }
        
        if (!acquired) {
            log.warn("请求被限流, key: {}, qps: {}", key, qps);
            throw new BusinessException(429, "请求过于频繁，请稍后再试");
        }
        
        // 执行原方法
        return joinPoint.proceed();
    }
    
    /**
     * 解析限流标识
     * 
     * @param keyExpression SpEL表达式
     * @param method 方法
     * @param args 方法参数
     * @return 解析后的限流标识
     */
    private String parseKey(String keyExpression, Method method, Object[] args) {
        if (StringUtils.isEmpty(keyExpression)) {
            return null;
        }
        
        try {
            // 获取参数名
            String[] parameterNames = discoverer.getParameterNames(method);
            if (parameterNames == null) {
                return keyExpression;
            }
            
            // 创建评估上下文
            EvaluationContext context = new StandardEvaluationContext();
            for (int i = 0; i < parameterNames.length && i < args.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
            
            // 解析表达式
            Expression expression = parser.parseExpression(keyExpression);
            Object result = expression.getValue(context);
            return result != null ? result.toString() : keyExpression;
        } catch (Exception e) {
            log.warn("解析限流标识失败: {}, 使用原表达式", keyExpression, e);
            return keyExpression;
        }
    }
}
