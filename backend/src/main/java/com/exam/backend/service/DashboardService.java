package com.exam.backend.service;

import com.exam.backend.repository.mysql.MysqlUserRepository;
import com.exam.backend.repository.mysql.MysqlQuestionRepository;
import com.exam.backend.repository.mysql.MysqlPaperRepository;
import com.exam.backend.repository.mysql.MysqlExamResultRepository;
import com.exam.backend.repository.oracle.OracleUserRepository;
import com.exam.backend.repository.sqlserver.SqlServerUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class DashboardService {

    // === MySQL ===
    @Autowired private MysqlUserRepository mysqlUserRepo;
    @Autowired private MysqlQuestionRepository mysqlQuestionRepo;
    @Autowired private MysqlPaperRepository mysqlPaperRepo;
    @Autowired private MysqlExamResultRepository mysqlExamResultRepo;

    // === 备份库 ===
    @Autowired private OracleUserRepository oracleUserRepo;
    @Autowired private SqlServerUserRepository sqlServerUserRepo;

    // 获取统计数据
    public Map<String, Object> getStats() {
        Map<String, Object> map = new HashMap<>();
        // 统计数据只查 MySQL 即可，不用管备库
        map.put("userCount", mysqlUserRepo.count());
        map.put("questionCount", mysqlQuestionRepo.count());
        map.put("paperCount", mysqlPaperRepo.count());
        map.put("examCount", mysqlExamResultRepo.count());
        return map;
    }

    /**
     * 【核心优化】并行检测数据库状态
     * 只要超过 3 秒没连上，就强制认为已断开，防止阻塞整个接口。
     */
    public Map<String, Boolean> getDbStatus() {
        Map<String, Boolean> status = new HashMap<>();

        // 1. 定义三个异步任务
        CompletableFuture<Boolean> checkMysql = CompletableFuture.supplyAsync(() -> {
            try { return mysqlUserRepo.count() >= 0; } catch (Exception e) { return false; }
        });

        CompletableFuture<Boolean> checkOracle = CompletableFuture.supplyAsync(() -> {
            try { return oracleUserRepo.count() >= 0; } catch (Exception e) { return false; }
        });

        CompletableFuture<Boolean> checkSqlServer = CompletableFuture.supplyAsync(() -> {
            try { return sqlServerUserRepo.count() >= 0; } catch (Exception e) { return false; }
        });

        // 2. 获取结果，每个任务最多等 2 秒
        status.put("mysql", getResultSafely(checkMysql));
        status.put("oracle", getResultSafely(checkOracle));
        status.put("sqlserver", getResultSafely(checkSqlServer));

        return status;
    }

    // 辅助方法：安全获取结果，带超时控制
    private boolean getResultSafely(CompletableFuture<Boolean> future) {
        try {
            // 最多等待 2 秒，超时就抛异常
            return future.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            // 超时或报错，视为连接断开
            return false;
        }
    }
}
