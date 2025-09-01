package com.seckill.system.util;

import io.jsonwebtoken.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类
 * 
 * 用于用户身份验证和Token管理
 * 
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret:seckill-system-secret-key}")
    private String secret;

    @Value("${jwt.expiration:86400}")
    private long expiration; // 默认24小时

    @Value("${jwt.header:Authorization}")
    private String header;

    /**
     * 生成JWT Token
     *
     * @param userId 用户ID
     * @param username 用户名
     * @return JWT Token
     */
    public String generateToken(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        return generateToken(claims, userId.toString());
    }

    /**
     * 生成JWT Token
     *
     * @param claims 声明信息
     * @param subject 主题（通常是用户ID）
     * @return JWT Token
     */
    public String generateToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration * 1000);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }

    /**
     * 从Token中获取用户ID
     *
     * @param token JWT Token
     * @return 用户ID
     */
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return Long.valueOf(claims.get("userId").toString());
        } catch (Exception e) {
            log.error("从Token获取用户ID失败", e);
            return null;
        }
    }

    /**
     * 从Token中获取用户名
     *
     * @param token JWT Token
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims.get("username").toString();
        } catch (Exception e) {
            log.error("从Token获取用户名失败", e);
            return null;
        }
    }

    /**
     * 从Token中获取主题（用户ID）
     *
     * @param token JWT Token
     * @return 主题
     */
    public String getSubjectFromToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims.getSubject();
        } catch (Exception e) {
            log.error("从Token获取主题失败", e);
            return null;
        }
    }

    /**
     * 从Token中获取过期时间
     *
     * @param token JWT Token
     * @return 过期时间
     */
    public Date getExpirationDateFromToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims.getExpiration();
        } catch (Exception e) {
            log.error("从Token获取过期时间失败", e);
            return null;
        }
    }

    /**
     * 从Token中获取所有声明信息
     *
     * @param token JWT Token
     * @return 声明信息
     */
    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 验证Token是否过期
     *
     * @param token JWT Token
     * @return 是否过期
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = getExpirationDateFromToken(token);
            return expiration.before(new Date());
        } catch (Exception e) {
            log.error("验证Token过期状态失败", e);
            return true;
        }
    }

    /**
     * 验证Token是否有效
     *
     * @param token JWT Token
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().setSigningKey(secret).parseClaimsJws(token);
            return !isTokenExpired(token);
        } catch (SignatureException e) {
            log.error("JWT签名验证失败", e);
            return false;
        } catch (MalformedJwtException e) {
            log.error("JWT格式错误", e);
            return false;
        } catch (ExpiredJwtException e) {
            log.error("JWT已过期", e);
            return false;
        } catch (UnsupportedJwtException e) {
            log.error("不支持的JWT", e);
            return false;
        } catch (IllegalArgumentException e) {
            log.error("JWT参数错误", e);
            return false;
        } catch (Exception e) {
            log.error("JWT验证异常", e);
            return false;
        }
    }

    /**
     * 从Authorization头中提取Token
     *
     * @param authorization Authorization头
     * @return JWT Token
     */
    public String extractTokenFromHeader(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }

    /**
     * 刷新Token
     *
     * @param token 原Token
     * @return 新Token
     */
    public String refreshToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            claims.setIssuedAt(new Date());
            return generateToken(claims, claims.getSubject());
        } catch (Exception e) {
            log.error("刷新Token失败", e);
            return null;
        }
    }

    /**
     * 获取Token剩余有效时间（秒）
     *
     * @param token JWT Token
     * @return 剩余有效时间（秒）
     */
    public long getRemainingTime(String token) {
        try {
            Date expiration = getExpirationDateFromToken(token);
            Date now = new Date();
            long remaining = (expiration.getTime() - now.getTime()) / 1000;
            return Math.max(0, remaining);
        } catch (Exception e) {
            log.error("获取Token剩余时间失败", e);
            return 0;
        }
    }

    /**
     * 检查Token是否即将过期（默认5分钟内过期）
     *
     * @param token JWT Token
     * @param thresholdSeconds 阈值（秒）
     * @return 是否即将过期
     */
    public boolean isTokenExpiringSoon(String token, long thresholdSeconds) {
        long remainingTime = getRemainingTime(token);
        return remainingTime <= thresholdSeconds;
    }

    /**
     * 检查Token是否即将过期（默认5分钟内过期）
     *
     * @param token JWT Token
     * @return 是否即将过期
     */
    public boolean isTokenExpiringSoon(String token) {
        return isTokenExpiringSoon(token, 300); // 5分钟
    }
}
