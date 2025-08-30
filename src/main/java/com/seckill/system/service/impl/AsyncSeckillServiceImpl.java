package com.seckill.system.service.impl;

import com.seckill.system.annotation.AdvancedRateLimit;
import com.seckill.system.entity.Result;
import com.seckill.system.mq.SeckillMessageProducer;
import com.seckill.system.mq.SeckillRequestMessage;
import com.seckill.system.service.AsyncSeckillService;
import com.seckill.system.service.SeckillService;
import com.seckill.system.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异步秒杀服务实现类
 *
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Service
@Slf4j
public class AsyncSeckillServiceImpl implements AsyncSeckillService {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private SeckillMessageProducer messageProducer;

    /**
     * 请求ID生成器
     */
    private final AtomicLong requestIdGenerator = new AtomicLong(1);

    /**
     * 秒杀请求线程池
     */
    private final ExecutorService seckillExecutor = new ThreadPoolExecutor(
        10, 50, 60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(1000),
        new ThreadPoolExecutor.CallerRunsPolicy()
    );

    /**
     * 请求状态缓存前缀
     */
    private static final String REQUEST_STATUS_PREFIX = "seckill:request:status:";

    /**
     * 用户请求历史前缀
     */
    private static final String USER_REQUEST_HISTORY_PREFIX = "seckill:user:history:";

    /**
     * 请求状态枚举
     */
    public enum RequestStatus {
        PENDING("待处理"),
        ENQUEUED("已入队"),
        PROCESSING("处理中"),
        SUCCESS("成功"),
        FAILED("失败"),
        TIMEOUT("超时");

        private final String description;

        RequestStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @Override
    @AdvancedRateLimit(
        key = "'async-seckill:submit'",
        algorithm = AdvancedRateLimit.Algorithm.TOKEN_BUCKET,
        capacity = 100,
        rate = 20.0,
        tokens = 1,
        enableUserLimit = true,
        userLimitMultiplier = 0.1,
        enableIpLimit = true,
        ipLimitMultiplier = 0.2,
        blockStrategy = AdvancedRateLimit.BlockStrategy.THROW_EXCEPTION,
        errorMessage = "异步秒杀请求过于频繁，请稍后重试"
    )
    public Result<String> submitSeckillRequest(Long userId, Long productId, Integer quantity) {
        try {
            // 1. 生成请求ID
            String requestId = generateRequestId();
            
            // 2. 创建秒杀请求
            SeckillRequest request = new SeckillRequest(userId, productId, quantity);
            request.setRequestId(requestId);
            
            // 3. 保存请求状态
            saveRequestStatus(requestId, RequestStatus.PENDING);
            
            // 4. 添加到用户历史
            addToUserHistory(userId, request);

            // 5. 发送到MQ
            SeckillRequestMessage msg = new SeckillRequestMessage(userId, productId, quantity, requestId);
            boolean sent = messageProducer.send(msg);
            if (!sent) {
                updateRequestStatus(requestId, RequestStatus.FAILED);
                return Result.error("请求入队失败");
            }
            updateRequestStatus(requestId, RequestStatus.ENQUEUED);
            
            log.info("秒杀请求已入队，requestId: {}, userId: {}, productId: {}", requestId, userId, productId);
            return Result.success(requestId);
            
        } catch (Exception e) {
            log.error("提交秒杀请求异常，userId: {}, productId: {}, quantity: {}", userId, productId, quantity, e);
            return Result.error("提交秒杀请求失败");
        }
    }

    @Override
    public Result<String> getRequestStatus(String requestId) {
        try {
            String status = getRequestStatusFromCache(requestId);
            if (status != null) {
                return Result.success(status);
            } else {
                return Result.error("请求不存在");
            }
        } catch (Exception e) {
            log.error("查询请求状态异常，requestId: {}", requestId, e);
            return Result.error("查询请求状态失败");
        }
    }

    @Override
    public Result<String> batchSubmitSeckillRequest(List<SeckillRequest> requests) {
        try {
            int success = 0;
            List<String> requestIds = new ArrayList<>();
            
            for (SeckillRequest request : requests) {
                Result<String> result = submitSeckillRequest(request.getUserId(), request.getProductId(), request.getQuantity());
                if (result.getCode() == 200) {
                    success++;
                    requestIds.add(result.getData());
                }
            }
            
            log.info("批量提交秒杀请求完成，总数: {}, 成功: {}", requests.size(), success);
            return Result.success("批量提交完成，成功" + success + "个");
            
        } catch (Exception e) {
            log.error("批量提交秒杀请求异常", e);
            return Result.error("批量提交失败");
        }
    }

    @Override
    public Result<List<SeckillRequest>> getUserRequestHistory(Long userId) {
        try {
            List<SeckillRequest> history = getUserHistoryFromCache(userId);
            return Result.success(history);
        } catch (Exception e) {
            log.error("获取用户请求历史异常，userId: {}", userId, e);
            return Result.error("获取请求历史失败");
        }
    }

    /**
     * 异步处理秒杀请求
     */
    @Async
    public void processSeckillRequestAsync(SeckillRequest request) {
        // 保留但不再直接使用（兼容旧调用）
        log.info("Deprecated async path called, requestId={}", request.getRequestId());
    }

    /**
     * 生成请求ID
     */
    private String generateRequestId() {
        return "REQ_" + System.currentTimeMillis() + "_" + requestIdGenerator.getAndIncrement();
    }

    /**
     * 保存请求状态
     */
    private void saveRequestStatus(String requestId, RequestStatus status) {
        String key = REQUEST_STATUS_PREFIX + requestId;
        redisUtil.set(key, status.name(), 24, TimeUnit.HOURS);
    }

    /**
     * 更新请求状态
     */
    public void updateRequestStatus(String requestId, RequestStatus status) {
        String key = REQUEST_STATUS_PREFIX + requestId;
        redisUtil.set(key, status.name(), 24, TimeUnit.HOURS);
    }

    /**
     * 从缓存获取请求状态
     */
    private String getRequestStatusFromCache(String requestId) {
        String key = REQUEST_STATUS_PREFIX + requestId;
        Object status = redisUtil.get(key);
        return status != null ? status.toString() : null;
    }

    /**
     * 添加到用户历史
     */
    private void addToUserHistory(Long userId, SeckillRequest request) {
        String key = USER_REQUEST_HISTORY_PREFIX + userId;
        
        try {
            // 获取现有历史
            List<SeckillRequest> history = getUserHistoryFromCache(userId);
            if (history == null) {
                history = new ArrayList<>();
            }
            
            // 添加新请求（限制历史记录数量）
            history.add(0, request);
            if (history.size() > 100) {
                history = history.subList(0, 100);
            }
            
            // 保存到缓存
            redisUtil.set(key, history, 24, TimeUnit.HOURS);
            
        } catch (Exception e) {
            log.error("添加到用户历史异常，userId: {}", userId, e);
        }
    }

    /**
     * 从缓存获取用户历史
     */
    @SuppressWarnings("unchecked")
    private List<SeckillRequest> getUserHistoryFromCache(Long userId) {
        String key = USER_REQUEST_HISTORY_PREFIX + userId;
        Object history = redisUtil.get(key);
        return history != null ? (List<SeckillRequest>) history : new ArrayList<>();
    }

    /**
     * 获取线程池状态
     */
    public Map<String, Object> getThreadPoolStatus() {
        Map<String, Object> status = new HashMap<>();
        
        if (seckillExecutor instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor executor = (ThreadPoolExecutor) seckillExecutor;
            
            status.put("corePoolSize", executor.getCorePoolSize());
            status.put("maximumPoolSize", executor.getMaximumPoolSize());
            status.put("currentPoolSize", executor.getPoolSize());
            status.put("activeThreads", executor.getActiveCount());
            status.put("queueSize", executor.getQueue().size());
            status.put("completedTasks", executor.getCompletedTaskCount());
            status.put("totalTasks", executor.getTaskCount());
        }
        
        return status;
    }

    /**
     * 关闭线程池
     */
    public void shutdown() {
        if (seckillExecutor != null && !seckillExecutor.isShutdown()) {
            seckillExecutor.shutdown();
            try {
                if (!seckillExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    seckillExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                seckillExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
