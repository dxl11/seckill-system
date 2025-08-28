-- 秒杀系统简化数据库初始化脚本
-- 创建数据库
CREATE DATABASE IF NOT EXISTS seckill DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE seckill;

-- 商品表
CREATE TABLE IF NOT EXISTS product (
    product_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '商品ID',
    product_name VARCHAR(255) NOT NULL COMMENT '商品名称',
    description TEXT COMMENT '商品描述',
    price DECIMAL(10,2) NOT NULL COMMENT '商品价格',
    seckill_price DECIMAL(10,2) COMMENT '秒杀价格',
    image_url VARCHAR(500) COMMENT '商品图片',
    status INT DEFAULT 0 COMMENT '商品状态：0-下架，1-上架，2-秒杀中',
    seckill_start_time DATETIME COMMENT '秒杀开始时间',
    seckill_end_time DATETIME COMMENT '秒杀结束时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_status (status),
    INDEX idx_seckill_time (seckill_start_time, seckill_end_time)
) COMMENT '商品表';

-- 商品库存表
CREATE TABLE IF NOT EXISTS product_stock (
    stock_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '库存ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    total_stock INT NOT NULL DEFAULT 0 COMMENT '总库存数量',
    available_stock INT NOT NULL DEFAULT 0 COMMENT '可用库存数量',
    locked_stock INT NOT NULL DEFAULT 0 COMMENT '已锁定库存数量',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_product_id (product_id),
    INDEX idx_product_id (product_id)
) COMMENT '商品库存表';

-- 秒杀订单表
CREATE TABLE IF NOT EXISTS seckill_order (
    order_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    product_name VARCHAR(255) NOT NULL COMMENT '商品名称',
    seckill_price DECIMAL(10,2) NOT NULL COMMENT '秒杀价格',
    quantity INT NOT NULL COMMENT '购买数量',
    total_amount DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
    status INT DEFAULT 0 COMMENT '订单状态：0-待支付，1-已支付，2-已取消，3-已退款',
    pay_time DATETIME COMMENT '支付时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_product_id (product_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) COMMENT '秒杀订单表';

-- 插入测试数据
INSERT INTO product (product_name, description, price, seckill_price, status, seckill_start_time, seckill_end_time) VALUES
('iPhone 15 Pro', '苹果最新旗舰手机', 8999.00, 7999.00, 2, '2025-01-01 00:00:00', '2025-12-31 23:59:59'),
('MacBook Pro M3', '苹果专业级笔记本电脑', 15999.00, 13999.00, 2, '2025-01-01 00:00:00', '2025-12-31 23:59:59'),
('AirPods Pro', '苹果无线降噪耳机', 1999.00, 1699.00, 2, '2025-01-01 00:00:00', '2025-12-31 23:59:59'),
('Samsung Galaxy S24', '三星最新旗舰手机', 7999.00, 6999.00, 2, '2025-01-01 00:00:00', '2025-12-31 23:59:59'),
('Dell XPS 13', '戴尔超薄笔记本电脑', 12999.00, 10999.00, 2, '2025-01-01 00:00:00', '2025-12-31 23:59:59');

INSERT INTO product_stock (product_id, total_stock, available_stock, locked_stock) VALUES
(1, 100, 100, 0),
(2, 50, 50, 0),
(3, 200, 200, 0),
(4, 80, 80, 0),
(5, 30, 30, 0);
