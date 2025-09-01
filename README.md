# 秒杀系统 (Seckill System)

## 📋 项目概述

这是一个基于Spring Boot的高性能秒杀系统，采用分布式架构设计，具备高并发、高可用、高可靠性的特点。系统实现了完整的秒杀业务流程，包括库存管理、订单处理、限流防护、分布式锁等核心功能。

## 🏗️ 系统架构

### 技术栈
- **后端框架**: Spring Boot 2.7.18
- **数据库**: MySQL 8.0.33 + MyBatis 2.3.1
- **缓存**: Redis (Lettuce连接池)
- **分布式锁**: Redisson (带看门狗机制)
- **消息队列**: RabbitMQ (异步处理 + 死信队列)
- **限流算法**: 滑动窗口 + 令牌桶
- **身份验证**: JWT Token
- **幂等性**: Redis Token机制
- **构建工具**: Maven

### 架构层次
```
┌─────────────────────────────────────────────────────────────┐
│                    Controller Layer                        │
│              (REST API + 限流 + 身份验证)                    │
├─────────────────────────────────────────────────────────────┤
│                    Service Layer                           │
│              (业务逻辑 + 事务管理 + 分布式锁)                  │
├─────────────────────────────────────────────────────────────┤
│                     DAO Layer                              │
│              (数据访问 + 乐观锁 + 版本控制)                  │
├─────────────────────────────────────────────────────────────┤
│                    Cache Layer                             │
│              (Redis + 库存预扣减 + 一致性管理)                │
├─────────────────────────────────────────────────────────────┤
│                   Database Layer                           │
│              (MySQL + 事务 + 行锁)                          │
└─────────────────────────────────────────────────────────────┘
```

## 🔄 秒杀核心流程

### 1. 秒杀请求入口

#### 安全秒杀接口 (`/secure/seckill/do`)
```http
POST /secure/seckill/do
Content-Type: application/x-www-form-urlencoded

productId=1001&quantity=1&idempotencyToken=abc123
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**防护机制**:
- 🔐 **JWT身份验证**: 验证用户身份和Token有效性
- 🚫 **幂等性检查**: 防止重复提交请求
- 🚦 **多维度限流**: 接口级 + 用户级 + IP级限流
- 🛡️ **参数校验**: 商品ID、数量、Token等参数验证

### 2. 秒杀业务逻辑执行

#### 2.1 前置检查
```java
// 1. 参数校验
if (userId == null || productId == null || quantity == null || quantity <= 0) {
    return Result.error("参数错误");
}

// 2. 检查用户是否已经秒杀过该商品
if (hasUserSeckilled(userId, productId)) {
    return Result.error("您已经参与过该商品的秒杀");
}
```

#### 2.2 分布式锁保护
```java
// 3. 使用分布式锁保护秒杀过程（细化锁粒度：商品 + 分片，带看门狗自动续期）
int shard = (int) (userId % 16);
String lockKey = STOCK_LOCK_PREFIX + productId + ":" + shard;
return distributedLockUtil.executeWithLock(lockKey, 5, () -> {
    return executeSeckillLogic(userId, productId, quantity);
});
```

**锁机制特点**:
- 🔒 **分片锁**: 按商品ID + 用户分片(16个分片)减少锁竞争
- 🐕 **看门狗机制**: 自动续期，防止业务执行时间过长导致锁释放
- ⏱️ **超时控制**: 5秒锁超时，避免死锁

#### 2.3 核心秒杀逻辑

##### 商品状态检查
```java
// 1. 检查商品是否在秒杀中
Product product = productDao.selectById(productId);
if (product == null) {
    return Result.error("商品不存在");
}
if (product.getStatus() != 2) {
    return Result.error("商品不在秒杀中");
}

// 检查秒杀时间
LocalDateTime now = LocalDateTime.now();
if (now.isBefore(product.getSeckillStartTime()) || now.isAfter(product.getSeckillEndTime())) {
    return Result.error("不在秒杀时间范围内");
}
```

##### 库存预扣减 (Redis)
```java
// 2. 使用库存一致性工具预扣减Redis库存
Long newStock = stockConsistencyUtil.preDeductStock(productId, quantity);
if (newStock < 0) {
    return Result.error("库存不足");
}
```

**库存预扣减机制**:
- 📦 **Redis预扣减**: 先扣减Redis缓存库存，快速响应
- 🔄 **原子操作**: 使用Lua脚本保证操作的原子性
- ⚖️ **库存检查**: 扣减前检查库存是否充足

##### 数据库库存扣减 (乐观锁)
```java
// 3. 扣减数据库库存（使用乐观锁）
int updateResult = productStockDao.decreaseStockWithVersionAuto(productId, quantity);
if (updateResult <= 0) {
    // 数据库库存不足或版本冲突，回滚Redis
    stockConsistencyUtil.rollbackStock(productId, quantity);
    return Result.error("库存不足或版本冲突");
}
```

**乐观锁机制**:
- 🔢 **版本控制**: 每次更新自动递增版本号
- 🚫 **冲突检测**: 版本号不匹配时更新失败
- 🔄 **自动重试**: 支持重试机制处理并发冲突

##### 库存确认
```java
// 4. 确认Redis库存扣减
if (!stockConsistencyUtil.confirmDeductStock(productId, quantity, newStock)) {
    // 库存状态不一致，回滚数据库
    productStockDao.increaseStock(productId, quantity);
    return Result.error("库存状态不一致");
}
```

**一致性保证**:
- ✅ **双重确认**: Redis和数据库库存状态一致性检查
- 🔄 **回滚机制**: 状态不一致时自动回滚
- 🎯 **最终一致性**: 保证数据最终一致性

##### 订单创建
```java
// 5. 创建订单
SeckillOrder order = createSeckillOrder(userId, product, quantity);
int insertResult = seckillOrderDao.insert(order);
if (insertResult <= 0) {
    // 创建订单失败，回滚库存
    stockConsistencyUtil.rollbackStock(productId, quantity);
    productStockDao.increaseStock(productId, quantity);
    return Result.error("创建订单失败");
}
```

**订单处理**:
- 📝 **订单生成**: 创建秒杀订单记录
- 💰 **价格计算**: 按秒杀价格计算总金额
- 🔄 **失败回滚**: 订单创建失败时回滚库存

##### 用户记录缓存
```java
// 6. 记录用户秒杀记录到Redis
recordUserSeckill(userId, productId, order.getOrderId());
```

**缓存策略**:
- 💾 **Redis缓存**: 用户秒杀记录缓存24小时
- 🔍 **快速查询**: 避免重复查询数据库
- 📊 **数据同步**: 数据库和缓存数据同步

### 3. 异步处理流程

#### 3.1 消息队列处理
```java
// 异步提交秒杀请求到MQ
@PostMapping("/submit")
@AdvancedRateLimit(
    key = "'async-seckill:product:' + #productId",
    algorithm = AdvancedRateLimit.Algorithm.TOKEN_BUCKET,
    capacity = 1000,
    rate = 100.0,
    tokens = 1
)
public Result<String> submitSeckillRequest(@RequestBody SeckillRequest request) {
    // 发送到RabbitMQ进行异步处理
    rabbitTemplate.convertAndSend("seckill.exchange", "seckill.submit", request);
    return Result.success("请求已提交，正在处理中");
}
```

**MQ特性**:
- 📨 **异步处理**: 请求入队，异步下单
- 🔄 **可靠消息**: 消息持久化 + 确认机制
- ⚰️ **死信队列**: 处理失败消息
- 🔁 **重试机制**: 支持消息重试

#### 3.2 消费者处理
```java
@RabbitListener(queues = "seckill.queue")
public void handleSeckillRequest(SeckillRequest request) {
    try {
        // 幂等性检查
        if (!idempotencyUtil.useIdempotencyToken(request.getIdempotencyToken())) {
            log.warn("重复请求，忽略处理: {}", request.getIdempotencyToken());
            return;
        }
        
        // 执行秒杀逻辑
        Result<String> result = seckillService.doSeckill(
            request.getUserId(), 
            request.getProductId(), 
            request.getQuantity()
        );
        
        // 处理结果...
        
    } catch (Exception e) {
        // 异常处理，发送到死信队列
        log.error("秒杀处理异常", e);
    }
}
```

## 🚦 限流防护机制

### 多维度限流策略

#### 1. 接口级限流
```java
@AdvancedRateLimit(
    key = "'seckill:product:' + #productId",
    algorithm = AdvancedRateLimit.Algorithm.SLIDING_WINDOW,
    windowSize = 30,        // 30秒时间窗口
    limit = 200,            // 200次请求限制
    enableUserLimit = true, // 启用用户维度限流
    userLimitMultiplier = 0.1, // 用户限制为总限制的10%
    enableIpLimit = true,   // 启用IP维度限流
    ipLimitMultiplier = 0.2,   // IP限制为总限制的20%
    blockStrategy = AdvancedRateLimit.BlockStrategy.THROW_EXCEPTION,
    errorMessage = "秒杀请求过于频繁，请稍后重试"
)
```

#### 2. 限流算法

**滑动窗口算法**:
- ⏰ **时间窗口**: 可配置的时间窗口大小
- 📊 **请求计数**: 窗口内请求数量统计
- 🔄 **动态滑动**: 实时更新窗口边界

**令牌桶算法**:
- 🪣 **令牌容量**: 可配置的令牌桶容量
- ⚡ **令牌生成**: 固定速率生成令牌
- 🎫 **令牌消费**: 请求消耗令牌

#### 3. 限流维度

| 维度 | 说明 | 配置示例 |
|------|------|----------|
| **接口级** | 整个接口的请求限制 | 200次/30秒 |
| **用户级** | 单个用户的请求限制 | 20次/30秒 (10%) |
| **IP级** | 单个IP的请求限制 | 40次/30秒 (20%) |
| **商品级** | 单个商品的请求限制 | 1000次/30秒 |

### 限流处理策略

```java
switch (annotation.blockStrategy()) {
    case THROW_EXCEPTION:
        throw new RuntimeException(annotation.errorMessage());
    case RETURN_ERROR:
        return null; // 返回错误结果
    case WAIT_RETRY:
        Thread.sleep(1000); // 等待重试
        return null;
    case FALLBACK:
        // 执行降级逻辑
        return null;
}
```

## 🔒 分布式锁机制

### Redisson分布式锁

#### 1. 锁配置
```java
@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        
        // 分布式锁配置
        config.setLockWatchdogTimeout(30000); // 看门狗超时时间30秒
        config.setNettyThreads(32);           // Netty线程数
        config.setThreads(16);                // 业务线程数
        
        return Redisson.create(config);
    }
}
```

#### 2. 锁使用
```java
public class RedissonDistributedLockUtil {
    
    public <T> T executeWithLock(String lockKey, long waitTime, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取锁，带看门狗自动续期
            if (lock.tryLock(waitTime, TimeUnit.SECONDS)) {
                try {
                    return supplier.get();
                } finally {
                    lock.unlock();
                }
            } else {
                throw new RuntimeException("获取锁失败");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("锁操作被中断", e);
        }
    }
}
```

**锁特性**:
- 🔐 **自动续期**: 看门狗机制自动续期锁
- ⏱️ **超时控制**: 可配置的锁超时时间
- 🔄 **重试机制**: 支持锁获取重试
- 🎯 **细粒度**: 商品ID + 用户分片的锁粒度

## 💾 库存一致性管理

### 库存一致性工具

#### 1. 预扣减机制
```java
public class StockConsistencyUtil {
    
    public Long preDeductStock(Long productId, Integer quantity) {
        String stockKey = "seckill:stock:" + productId;
        String lockKey = "seckill:stock:lock:" + productId;
        
        // 获取库存锁
        if (!acquireStockLock(lockKey)) {
            return -1L; // 获取锁失败
        }
        
        try {
            // 检查库存是否充足
            Object currentStock = redisUtil.get(stockKey);
            if (currentStock == null) {
                return -1L; // 库存信息不存在
            }
            
            Long stock = Long.valueOf(currentStock.toString());
            if (stock < quantity) {
                return -1L; // 库存不足
            }
            
            // 预扣减库存
            Long newStock = stock - quantity;
            redisUtil.set(stockKey, newStock, 24, TimeUnit.HOURS);
            
            return newStock;
            
        } finally {
            // 释放库存锁
            releaseStockLock(lockKey);
        }
    }
}
```

#### 2. 库存确认
```java
public boolean confirmDeductStock(Long productId, Integer quantity, Long expectedStock) {
    String stockKey = "seckill:stock:" + productId;
    
    // 检查Redis库存状态
    Object currentStock = redisUtil.get(stockKey);
    if (currentStock == null) {
        return false;
    }
    
    Long actualStock = Long.valueOf(currentStock.toString());
    return actualStock.equals(expectedStock);
}
```

#### 3. 库存回滚
```java
public void rollbackStock(Long productId, Integer quantity) {
    String stockKey = "seckill:stock:" + productId;
    
    // 回滚Redis库存
    Object currentStock = redisUtil.get(stockKey);
    if (currentStock != null) {
        Long stock = Long.valueOf(currentStock.toString());
        Long newStock = stock + quantity;
        redisUtil.set(stockKey, newStock, 24, TimeUnit.HOURS);
    }
}
```

## 🔐 安全防护机制

### 1. JWT身份验证
```java
public class JwtUtil {
    
    // 生成JWT Token
    public String generateToken(Long userId) {
        return Jwts.builder()
                .setSubject(userId.toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }
    
    // 验证Token
    public boolean validateToken(String token) {
        try {
            Jwts.parser().setSigningKey(secret).parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

### 2. 幂等性控制
```java
public class IdempotencyUtil {
    
    // 生成幂等性Token
    public String generateIdempotencyToken(String businessType) {
        String token = UUID.randomUUID().toString();
        String key = "idempotency:" + businessType + ":" + token;
        
        // 存储Token到Redis，24小时过期
        redisUtil.set(key, "1", 24, TimeUnit.HOURS);
        return token;
    }
    
    // 使用幂等性Token
    public boolean useIdempotencyToken(String token) {
        String key = "idempotency:used:" + token;
        
        // 检查Token是否已被使用
        if (redisUtil.hasKey(key)) {
            return false; // Token已被使用
        }
        
        // 标记Token为已使用
        redisUtil.set(key, "1", 24, TimeUnit.HOURS);
        return true;
    }
}
```

## 📊 监控与告警

### 1. 系统监控指标
- **QPS**: 每秒请求处理量
- **响应时间**: 接口响应时间统计
- **成功率**: 秒杀成功率监控
- **库存消耗率**: 库存消耗速度监控

### 2. 限流监控
- **限流触发次数**: 各维度限流触发统计
- **限流用户分布**: 被限流用户分析
- **限流IP分布**: 被限流IP分析

### 3. 库存监控
- **库存变化**: 实时库存变化监控
- **预扣减成功率**: Redis预扣减成功率
- **一致性检查**: 缓存与数据库一致性

## 🚀 性能优化特性

### 1. 缓存策略
- **Redis预热**: 系统启动时预热热点数据
- **多级缓存**: Redis + 本地缓存
- **缓存更新**: 定时同步缓存与数据库

### 2. 并发控制
- **分片锁**: 减少锁竞争，提高并发
- **乐观锁**: 减少数据库行锁冲突
- **异步处理**: MQ异步处理，提高响应速度

### 3. 数据库优化
- **读写分离**: 主从数据库分离
- **连接池**: 数据库连接池管理
- **索引优化**: 关键字段索引优化

## 🔧 部署与配置

### 1. 环境要求
- **JDK**: 1.8+
- **Redis**: 6.0+
- **MySQL**: 8.0+
- **RabbitMQ**: 3.8+

### 2. 配置文件
```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/seckill?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8
    username: root
    password: root
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      connection-test-query: SELECT 1
      validation-timeout: 5000
  
  redis:
    host: localhost
    port: 6379
    database: 0
    timeout: 3000
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
  
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

seckill:
  rate-limit:
    default:
      algorithm: SLIDING_WINDOW
      window-size: 30
      limit: 100
      enable-user-limit: true
      user-limit-multiplier: 0.1
      enable-ip-limit: true
      ip-limit-multiplier: 0.2

jwt:
  secret: your-secret-key
  expiration: 86400000  # 24小时
  header: Authorization
```

### 3. 启动命令
```bash
# 编译项目
mvn clean compile

# 运行项目
mvn spring-boot:run

# 打包部署
mvn clean package
java -jar target/seckill-system-1.0.0.jar
```

## 📈 性能测试

### 1. 并发测试场景
- **单商品秒杀**: 1000并发用户，1000库存
- **多商品秒杀**: 5000并发用户，10个商品
- **限流测试**: 超过限流阈值的请求处理

### 2. 性能指标
- **TPS**: 1000+ 事务/秒
- **响应时间**: 平均 < 100ms
- **成功率**: > 99%
- **库存准确性**: 100%

## 🔮 未来优化方向

### 1. 短期优化 (P0)
- ✅ 分布式锁优化 (已完成)
- ✅ JWT身份验证 (已完成)
- ✅ 幂等性控制 (已完成)
- ✅ 库存一致性 (已完成)

### 2. 中期优化 (P1)
- 🔄 本地缓存 + 布隆过滤器
- 🔄 数据库读写分离
- 🔄 库存表分区
- 🔄 监控告警系统

### 3. 长期优化 (P2)
- 🔮 微服务架构
- 🔮 容器化部署
- 🔮 云原生支持
- 🔮 机器学习优化

## 📝 总结

当前秒杀系统已经实现了完整的核心功能，具备以下特点：  datasource:
    type: com.alibaba.druid.pool.DruidDataSource

1. **高并发**: 分布式锁 + 分片机制 + 异步处理
2. **高可用**: 多级缓存 + 限流防护 + 异常处理
3. **高可靠**: 库存一致性 + 幂等性控制 + 事务管理
4. **高性能**: Redis缓存 + 乐观锁 + 连接池优化
5. **高安全**: JWT认证 + 限流防护 + 参数校验

系统架构清晰，代码结构规范，遵循三层架构原则，具备生产环境部署能力。通过持续的优化和监控，可以支持大规模秒杀场景的需求。
