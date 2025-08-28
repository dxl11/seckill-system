package com.seckill.system.entity;

import lombok.Data;

/**
 * 统一响应结果类
 * 
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 * @param <T> 响应数据类型
 */
@Data
public class Result<T> {
    
    /**
     * 响应码
     */
    private Integer code;
    
    /**
     * 响应消息
     */
    private String message;
    
    /**
     * 响应数据
     */
    private T data;
    
    /**
     * 响应时间戳
     */
    private Long timestamp;
    
    public Result() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public Result(Integer code, String message) {
        this();
        this.code = code;
        this.message = message;
    }
    
    public Result(Integer code, String message, T data) {
        this(code, message);
        this.data = data;
    }
    
    /**
     * 成功响应
     * 
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 成功响应结果
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }
    
    /**
     * 成功响应（无数据）
     * 
     * @return 成功响应结果
     */
    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功");
    }
    
    /**
     * 失败响应
     * 
     * @param code 错误码
     * @param message 错误消息
     * @return 失败响应结果
     */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message);
    }
    
    /**
     * 失败响应（默认错误码500）
     * 
     * @param message 错误消息
     * @return 失败响应结果
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message);
    }
}
