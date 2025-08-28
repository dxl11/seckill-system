# 秒杀系统 (Seckill System)

## 项目简介

这是一个基于Spring Boot + MyBatis + Redis + MySQL的秒杀系统，具备高并发、高可用的特性。系统采用分库分表架构，支持限流削峰，实现了完整的秒杀业务流程。

## 技术架构

### 后端技术栈
- **Spring Boot 2.7.18** - 主框架
- **MyBatis 3.5.13** - ORM框架
- **Sharding-JDBC 5.3.2** - 分库分表
- **Redis 3.0.0** - 缓存和限流
- **MySQL 8.0.33** - 数据库
- **Druid 1.2.20** - 数据库连接池
- **Guava 31.1-jre** - 限流算法
- **Hutool 5.8.22** - 工具包

### 核心特性
- ✅ **分库分表** - 支持水平分库分表，提升系统扩展性
- ✅ **限流削峰** - 基于令牌桶算法的限流机制
- ✅ **缓存优化** - Redis缓存热点数据，提升响应速度
- ✅ **事务管理** - 分布式事务保证数据一致性
- ✅ **异常处理** - 统一异常处理和错误响应
- ✅ **接口文档** - 完整的RESTful API设计

## 系统架构

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   前端应用      │    │   负载均衡      │    │   API网关       │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                        秒杀系统集群                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐            │
│  │ 秒杀服务1   │  │ 秒杀服务2   │  │ 秒杀服务N   │            │
│  └─────────────┘  └─────────────┘  └─────────────┘            │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                        数据层                                   │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐            │
│  │   Redis     │  │   MySQL-0   │  │   MySQL-1   │            │
│  │  缓存/限流  │  │  分库分表   │  │  分库分表   │            │
│  └─────────────┘  └─────────────┘  └─────────────┘            │
└─────────────────────────────────────────────────────────────────┘
```

## 数据库设计

### 分库分表策略
- **商品表**: 按商品ID分库分表 (2库 × 4表)
- **订单表**: 按用户ID分库分表 (2库 × 8表)  
- **库存表**: 按商品ID分库分表 (2库 × 4表)

### 核心表结构
- `product_{0-3}` - 商品信息表
- `product_stock_{0-3}` - 商品库存表
- `seckill_order_{0-7}` - 秒杀订单表

## 核心功能

### 1. 秒杀功能
- 商品秒杀
- 库存扣减
- 订单创建
- 防重复秒杀

### 2. 商品管理
- 商品CRUD
- 库存管理
- 秒杀状态控制

### 3. 限流机制
- 接口级限流
- 商品级限流
- 用户级限流
- 支持阻塞/非阻塞模式

### 4. 缓存策略
- 商品信息缓存
- 库存信息缓存
- 用户秒杀记录缓存
- 缓存预热机制

## 快速开始

### 环境要求
- JDK 8+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+

### 1. 克隆项目
```bash
git clone https://github.com/your-username/seckill-system.git
cd seckill-system
```

### 2. 配置数据库
```bash
# 创建数据库
mysql -u root -p < src/main/resources/sql/init.sql

# 修改配置文件
vim src/main/resources/application.yml
```

### 3. 配置Redis
```bash
# 启动Redis服务
redis-server

# 修改配置文件中的Redis连接信息
```

### 4. 启动应用
```bash
# 编译项目
mvn clean compile

# 启动应用
mvn spring-boot:run
```

### 5. 访问接口
```
健康检查: http://localhost:8080/seckill/api/seckill/health
商品列表: http://localhost:8080/seckill/api/product/list
秒杀商品: http://localhost:8080/seckill/api/product/seckill
```

## API接口文档

### 秒杀相关接口

#### 执行秒杀
```http
POST /api/seckill/do
Content-Type: application/x-www-form-urlencoded

userId=1&productId=1&quantity=1
```

#### 查询秒杀结果
```http
GET /api/seckill/result?userId=1&productId=1
```

#### 预热商品库存
```http
POST /api/seckill/preload?productId=1
```

#### 查询商品库存
```http
GET /api/seckill/stock?productId=1
```

### 商品管理接口

#### 查询商品
```http
GET /api/product/{productId}
```

#### 商品列表
```http
GET /api/product/list
```

#### 秒杀商品
```http
GET /api/product/seckill
```

#### 添加商品
```http
POST /api/product/add
Content-Type: application/json

{
  "productName": "测试商品",
  "description": "商品描述",
  "price": 99.99,
  "seckillPrice": 79.99,
  "status": 2
}
```

## 性能优化

### 1. 缓存优化
- Redis缓存热点数据
- 缓存预热机制
- 缓存更新策略

### 2. 数据库优化
- 分库分表提升并发能力
- 索引优化查询性能
- 连接池配置优化

### 3. 限流策略
- 接口级限流保护
- 商品级限流控制
- 用户级限流防刷

### 4. 异步处理
- 异步创建订单
- 异步更新库存
- 异步发送通知

## 监控与运维

### 1. 日志管理
- 结构化日志输出
- 日志级别配置
- 日志文件轮转

### 2. 性能监控
- 接口响应时间
- 系统资源使用
- 业务指标统计

### 3. 健康检查
- 数据库连接检查
- Redis连接检查
- 系统状态检查

## 部署说明

### 1. 开发环境
```bash
# 本地开发
mvn spring-boot:run
```

### 2. 测试环境
```bash
# 打包
mvn clean package -Dmaven.test.skip=true

# 运行
java -jar target/seckill-system-1.0.0.jar --spring.profiles.active=test
```

### 3. 生产环境
```bash
# 打包
mvn clean package -Dmaven.test.skip=true -Pprod

# 运行
java -Xms2g -Xmx4g -jar target/seckill-system-1.0.0.jar --spring.profiles.active=prod
```

## 常见问题

### Q1: 如何处理高并发场景？
A: 系统采用Redis缓存 + 分库分表 + 限流机制，通过多层防护确保系统稳定。

### Q2: 如何保证数据一致性？
A: 使用分布式事务和Redis原子操作，确保库存扣减和订单创建的原子性。

### Q3: 如何防止超卖？
A: 通过Redis原子递减操作和数据库乐观锁机制，双重保证防止超卖。

### Q4: 如何扩展系统容量？
A: 支持水平扩展，可以增加应用实例和数据库分片数量。

## 贡献指南

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开 Pull Request

## 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 联系方式

- 项目维护者: [Your Name](mailto:your.email@example.com)
- 项目地址: [https://github.com/your-username/seckill-system](https://github.com/your-username/seckill-system)
- 问题反馈: [Issues](https://github.com/your-username/seckill-system/issues)

## 更新日志

### v1.0.0 (2025-08-28)
- ✅ 基础秒杀功能
- ✅ 分库分表架构
- ✅ 限流削峰机制
- ✅ 缓存优化策略
- ✅ 商品管理功能
- ✅ 统一异常处理
- ✅ 完整API接口
- ✅ 数据库初始化脚本
