package com.seckill.system.controller;

import com.seckill.system.annotation.AdvancedRateLimit;
import com.seckill.system.entity.Result;
import com.seckill.system.service.SeckillService;
import com.seckill.system.util.IdempotencyUtil;
import com.seckill.system.util.JwtUtil;
import com.seckill.system.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;

/**
 * 安全秒杀控制器
 * 
 * 集成JWT身份验证、幂等性检查和限流防护
 * 
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@RestController
@RequestMapping("/secure/seckill")
@Slf4j
public class SecureSeckillController {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private IdempotencyUtil idempotencyUtil;

    @Autowired
    private RedisUtil redisUtil;

    /**
     * 执行秒杀（带身份验证和幂等性检查）
     *
     * @param productId 商品ID
     * @param quantity 购买数量
     * @param idempotencyToken 幂等性Token
     * @param request HTTP请求
     * @return 秒杀结果
     */
    @PostMapping("/do")
    @AdvancedRateLimit(
        key = "'secure-seckill:product:' + #productId",
        algorithm = AdvancedRateLimit.Algorithm.SLIDING_WINDOW,
        windowSize = 30,
        limit = 100,
        enableUserLimit = true,
        userLimitMultiplier = 0.1,
        enableIpLimit = true,
        ipLimitMultiplier = 0.2,
        blockStrategy = AdvancedRateLimit.BlockStrategy.THROW_EXCEPTION,
        errorMessage = "秒杀请求过于频繁，请稍后重试"
    )
    public Result<String> doSecureSeckill(
            @RequestParam Long productId,
            @RequestParam Integer quantity,
            @RequestParam String idempotencyToken,
            HttpServletRequest request) {
        
        try {
            // 1. 身份验证
            String token = extractTokenFromRequest(request);
            if (token == null) {
                return Result.error("缺少身份验证Token");
            }

            if (!jwtUtil.validateToken(token)) {
                return Result.error("身份验证失败或Token已过期");
            }

            Long userId = jwtUtil.getUserIdFromToken(token);
            if (userId == null) {
                return Result.error("无法获取用户身份信息");
            }

            // 2. 幂等性检查
            if (!idempotencyUtil.useIdempotencyToken(idempotencyToken)) {
                return Result.error("请求重复提交或Token无效");
            }

            // 3. 执行秒杀
            Result<String> result = seckillService.doSeckill(userId, productId, quantity);
            
            if (result.getCode() == 200) {
                log.info("用户{}秒杀商品{}成功，订单号：{}", userId, productId, result.getData());
            } else {
                log.warn("用户{}秒杀商品{}失败：{}", userId, productId, result.getMessage());
            }

            return result;

        } catch (Exception e) {
            log.error("安全秒杀异常，productId: {}, quantity: {}", productId, quantity, e);
            return Result.error("秒杀失败，请稍后重试");
        }
    }

    /**
     * 查询秒杀结果（带身份验证）
     *
     * @param productId 商品ID
     * @param request HTTP请求
     * @return 秒杀结果
     */
    @GetMapping("/result")
    public Result<String> getSecureSeckillResult(
            @RequestParam Long productId,
            HttpServletRequest request) {
        
        try {
            // 1. 身份验证
            String token = extractTokenFromRequest(request);
            if (token == null) {
                return Result.error("缺少身份验证Token");
            }

            if (!jwtUtil.validateToken(token)) {
                return Result.error("身份验证失败或Token已过期");
            }

            Long userId = jwtUtil.getUserIdFromToken(token);
            if (userId == null) {
                return Result.error("无法获取用户身份信息");
            }

            // 2. 查询秒杀结果
            return seckillService.getSeckillResult(userId, productId);

        } catch (Exception e) {
            log.error("查询安全秒杀结果异常，productId: {}", productId, e);
            return Result.error("查询失败，请稍后重试");
        }
    }

    /**
     * 获取幂等性Token
     *
     * @param request HTTP请求
     * @return 幂等性Token
     */
    @GetMapping("/idempotency-token")
    public Result<String> getIdempotencyToken(HttpServletRequest request) {
        try {
            // 1. 身份验证
            String token = extractTokenFromRequest(request);
            if (token == null) {
                return Result.error("缺少身份验证Token");
            }

            if (!jwtUtil.validateToken(token)) {
                return Result.error("身份验证失败或Token已过期");
            }

            // 2. 生成幂等性Token
            String idempotencyToken = idempotencyUtil.generateIdempotencyToken("seckill");
            
            // 3. 将Token存储到Redis（24小时过期）
            String tokenKey = "idempotency:token:" + idempotencyToken;
            redisUtil.set(tokenKey, "1", 24, TimeUnit.HOURS);

            return Result.success(idempotencyToken);

        } catch (Exception e) {
            log.error("获取幂等性Token异常", e);
            return Result.error("获取Token失败，请稍后重试");
        }
    }

    /**
     * 刷新JWT Token
     *
     * @param request HTTP请求
     * @return 新的JWT Token
     */
    @PostMapping("/refresh-token")
    public Result<String> refreshToken(HttpServletRequest request) {
        try {
            // 1. 获取原Token
            String token = extractTokenFromRequest(request);
            if (token == null) {
                return Result.error("缺少身份验证Token");
            }

            // 2. 检查Token是否即将过期
            if (!jwtUtil.isTokenExpiringSoon(token)) {
                return Result.error("Token尚未过期，无需刷新");
            }

            // 3. 刷新Token
            String newToken = jwtUtil.refreshToken(token);
            if (newToken == null) {
                return Result.error("Token刷新失败");
            }

            return Result.success(newToken);

        } catch (Exception e) {
            log.error("刷新Token异常", e);
            return Result.error("Token刷新失败，请稍后重试");
        }
    }

    /**
     * 从请求中提取Token
     *
     * @param request HTTP请求
     * @return JWT Token
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        return jwtUtil.extractTokenFromHeader(authorization);
    }
}
