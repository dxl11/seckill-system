package com.seckill.system.dao;

import com.seckill.system.entity.ProductStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 商品库存DAO接口
 *
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Mapper
public interface ProductStockDao {

    /**
     * 根据商品ID查询库存信息
     *
     * @param productId 商品ID
     * @return 库存信息
     */
    ProductStock selectByProductId(Long productId);

    /**
     * 扣减库存
     *
     * @param productId 商品ID
     * @param quantity 扣减数量
     * @return 影响行数
     */
    int decreaseStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 增加库存
     *
     * @param productId 商品ID
     * @param quantity 增加数量
     * @return 影响行数
     */
    int increaseStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 带乐观锁的库存扣减
     *
     * @param productId 商品ID
     * @param quantity 扣减数量
     * @param version 版本号
     * @return 影响行数
     */
    int decreaseStockWithVersion(@Param("productId") Long productId, 
                                @Param("quantity") Integer quantity, 
                                @Param("version") Integer version);

    /**
     * 带乐观锁的库存扣减（自动获取版本号）
     *
     * @param productId 商品ID
     * @param quantity 扣减数量
     * @return 影响行数
     */
    int decreaseStockWithVersionAuto(@Param("productId") Long productId, 
                                    @Param("quantity") Integer quantity);

    /**
     * 带乐观锁的库存增加
     *
     * @param productId 商品ID
     * @param quantity 增加数量
     * @param version 版本号
     * @return 影响行数
     */
    int increaseStockWithVersion(@Param("productId") Long productId, 
                                @Param("quantity") Integer quantity, 
                                @Param("version") Integer version);

    /**
     * 获取商品库存版本号
     *
     * @param productId 商品ID
     * @return 版本号
     */
    Integer getVersion(@Param("productId") Long productId);

    /**
     * 锁定库存行（悲观锁）
     *
     * @param productId 商品ID
     * @return 库存信息
     */
    ProductStock selectForUpdate(@Param("productId") Long productId);
}
