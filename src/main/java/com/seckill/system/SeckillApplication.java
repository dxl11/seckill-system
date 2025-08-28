package com.seckill.system;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 秒杀系统主启动类
 * 
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@SpringBootApplication
@MapperScan("com.seckill.system.dao")
@EnableCaching
@EnableAsync
public class SeckillApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeckillApplication.class, args);
        System.out.println("秒杀系统启动成功！");
    }
}
