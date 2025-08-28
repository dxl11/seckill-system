package com.seckill.system.dao;

import com.seckill.system.entity.SeckillOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 秒杀订单DAO接口
 *
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Mapper
public interface SeckillOrderDao {

    /**
     * 根据订单ID查询订单
     *
     * @param orderId 订单ID
     * @return 订单信息
     */
    SeckillOrder selectById(@Param("orderId") Long orderId);

    /**
     * 根据用户ID和商品ID查询订单
     *
     * @param userId 用户ID
     * @param productId 商品ID
     * @return 订单信息
     */
    SeckillOrder selectByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);

    /**
     * 查询用户的所有订单
     *
     * @param userId 用户ID
     * @return 订单列表
     */
    List<SeckillOrder> selectByUserId(@Param("userId") Long userId);

    /**
     * 插入订单
     *
     * @param order 订单信息
     * @return 影响行数
     */
    int insert(SeckillOrder order);

    /**
     * 更新订单
     *
     * @param order 订单信息
     * @return 影响行数
     */
    int update(SeckillOrder order);

    /**
     * 更新订单状态
     *
     * @param orderId 订单ID
     * @param status 订单状态
     * @return 影响行数
     */
    int updateStatus(@Param("orderId") Long orderId, @Param("status") Integer status);
}
