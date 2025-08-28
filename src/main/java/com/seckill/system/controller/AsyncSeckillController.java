package com.seckill.system.controller;

import com.seckill.system.aspect.DistributedRateLimit;
import com.seckill.system.entity.Result;
import com.seckill.system.service.AsyncSeckillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 异步秒杀控制器
 *
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@RestController
@RequestMapping("/async-seckill")
@Slf4j
public class AsyncSeckillController {

    @Autowired
    private AsyncSeckillService asyncSeckillService;

    /**
     * 异步提交秒杀请求
     *
     * @param userId 用户ID
     * @param productId 商品ID
     * @param quantity 购买数量
     * @return 提交结果
     */
    @PostMapping("/submit")
    @DistributedRateLimit(key = "'async:seckill:' + #userId", limit = 50, window = 60, block = false)
    public Result<String> submitSeckillRequest(@RequestParam Long userId,
                                             @RequestParam Long productId,
                                             @RequestParam Integer quantity) {
        try {
            if (userId == null || productId == null || quantity == null || quantity <= 0) {
                return Result.error("参数错误");
            }

            Result<String> result = asyncSeckillService.submitSeckillRequest(userId, productId, quantity);
            log.info("异步秒杀请求提交，userId: {}, productId: {}, quantity: {}, result: {}", 
                    userId, productId, quantity, result.getData());
            
            return result;
            
        } catch (Exception e) {
            log.error("异步提交秒杀请求异常，userId: {}, productId: {}, quantity: {}", userId, productId, quantity, e);
            return Result.error("提交秒杀请求失败");
        }
    }

    /**
     * 查询秒杀请求状态
     *
     * @param requestId 请求ID
     * @return 请求状态
     */
    @GetMapping("/status/{requestId}")
    public Result<String> getRequestStatus(@PathVariable String requestId) {
        try {
            if (requestId == null || requestId.trim().isEmpty()) {
                return Result.error("请求ID不能为空");
            }

            Result<String> result = asyncSeckillService.getRequestStatus(requestId);
            log.debug("查询秒杀请求状态，requestId: {}, status: {}", requestId, result.getData());
            
            return result;
            
        } catch (Exception e) {
            log.error("查询秒杀请求状态异常，requestId: {}", requestId, e);
            return Result.error("查询请求状态失败");
        }
    }

    /**
     * 批量提交秒杀请求
     *
     * @param requests 秒杀请求列表
     * @return 批量提交结果
     */
    @PostMapping("/batch-submit")
    @DistributedRateLimit(key = "'async:batch:seckill'", limit = 10, window = 60, block = false)
    public Result<String> batchSubmitSeckillRequest(@RequestBody List<AsyncSeckillService.SeckillRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                return Result.error("请求列表不能为空");
            }

            if (requests.size() > 100) {
                return Result.error("批量请求数量不能超过100个");
            }

            Result<String> result = asyncSeckillService.batchSubmitSeckillRequest(requests);
            log.info("批量异步秒杀请求提交，数量: {}, result: {}", requests.size(), result.getData());
            
            return result;
            
        } catch (Exception e) {
            log.error("批量提交秒杀请求异常，数量: {}", requests != null ? requests.size() : 0, e);
            return Result.error("批量提交失败");
        }
    }

    /**
     * 获取用户秒杀请求历史
     *
     * @param userId 用户ID
     * @return 请求历史
     */
    @GetMapping("/history/{userId}")
    public Result<List<AsyncSeckillService.SeckillRequest>> getUserRequestHistory(@PathVariable Long userId) {
        try {
            if (userId == null) {
                return Result.error("用户ID不能为空");
            }

            Result<List<AsyncSeckillService.SeckillRequest>> result = asyncSeckillService.getUserRequestHistory(userId);
            log.debug("获取用户秒杀请求历史，userId: {}, 数量: {}", userId, 
                    result.getData() != null ? result.getData().size() : 0);
            
            return result;
            
        } catch (Exception e) {
            log.error("获取用户秒杀请求历史异常，userId: {}", userId, e);
            return Result.error("获取请求历史失败");
        }
    }

    /**
     * 获取线程池状态
     *
     * @return 线程池状态
     */
    @GetMapping("/thread-pool/status")
    public Result<Map<String, Object>> getThreadPoolStatus() {
        try {
            if (asyncSeckillService instanceof com.seckill.system.service.impl.AsyncSeckillServiceImpl) {
                com.seckill.system.service.impl.AsyncSeckillServiceImpl service = 
                    (com.seckill.system.service.impl.AsyncSeckillServiceImpl) asyncSeckillService;
                
                Map<String, Object> status = service.getThreadPoolStatus();
                return Result.success(status);
            } else {
                return Result.error("无法获取线程池状态");
            }
        } catch (Exception e) {
            log.error("获取线程池状态异常", e);
            return Result.error("获取线程池状态失败");
        }
    }

    /**
     * 健康检查
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> healthCheck() {
        try {
            Map<String, Object> health = new java.util.HashMap<>();
            health.put("status", "UP");
            health.put("timestamp", System.currentTimeMillis());
            health.put("service", "AsyncSeckillService");
            
            // 检查线程池状态
            try {
                if (asyncSeckillService instanceof com.seckill.system.service.impl.AsyncSeckillServiceImpl) {
                    com.seckill.system.service.impl.AsyncSeckillServiceImpl service = 
                        (com.seckill.system.service.impl.AsyncSeckillServiceImpl) asyncSeckillService;
                    
                    Map<String, Object> threadPoolStatus = service.getThreadPoolStatus();
                    health.put("threadPool", "UP");
                    health.put("threadPoolStatus", threadPoolStatus);
                } else {
                    health.put("threadPool", "UNKNOWN");
                }
            } catch (Exception e) {
                health.put("threadPool", "DOWN");
                health.put("threadPoolError", e.getMessage());
            }
            
            return Result.success(health);
            
        } catch (Exception e) {
            log.error("异步秒杀服务健康检查异常", e);
            return Result.error("健康检查失败");
        }
    }

    /**
     * 模拟高并发测试
     *
     * @param userId 用户ID
     * @param productId 商品ID
     * @param quantity 购买数量
     * @param concurrentCount 并发数量
     * @return 测试结果
     */
    @PostMapping("/test/concurrent")
    public Result<String> concurrentTest(@RequestParam Long userId,
                                       @RequestParam Long productId,
                                       @RequestParam Integer quantity,
                                       @RequestParam(defaultValue = "100") Integer concurrentCount) {
        try {
            if (concurrentCount > 1000) {
                return Result.error("并发数量不能超过1000");
            }

            log.info("开始并发测试，userId: {}, productId: {}, quantity: {}, concurrentCount: {}", 
                    userId, productId, quantity, concurrentCount);

            // 这里可以实现真正的并发测试逻辑
            // 暂时返回模拟结果
            String result = String.format("并发测试完成，模拟提交%d个请求", concurrentCount);
            
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("并发测试异常，userId: {}, productId: {}, quantity: {}, concurrentCount: {}", 
                    userId, productId, quantity, concurrentCount, e);
            return Result.error("并发测试失败");
        }
    }
}
