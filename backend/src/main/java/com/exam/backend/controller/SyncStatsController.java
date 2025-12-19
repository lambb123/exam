package com.exam.backend.controller;

import com.exam.backend.entity.SyncLog;
import com.exam.backend.repository.mysql.*;
import com.exam.backend.repository.oracle.*;
import com.exam.backend.repository.sqlserver.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/monitor") // 注意：你原来的前缀是 /api/monitor，前端记得对应
@CrossOrigin(origins = "*")
public class SyncStatsController {

    // === MySQL Repositories ===
    @Autowired private MysqlUserRepository mysqlUserRepo;
    @Autowired private MysqlQuestionRepository mysqlQuestionRepo;
    @Autowired private MysqlPaperRepository mysqlPaperRepo;
    @Autowired private MysqlPaperQuestionRepository mysqlPaperQuestionRepo;
    @Autowired private MysqlExamResultRepository mysqlExamResultRepo;
    @Autowired private MysqlSyncLogRepository mysqlSyncLogRepo; // ✅ 新增：用于查日志表

    // === Oracle Repositories ===
    @Autowired private OracleUserRepository oracleUserRepo;
    @Autowired private OracleQuestionRepository oracleQuestionRepo;
    @Autowired private OraclePaperRepository oraclePaperRepo;
    @Autowired private OraclePaperQuestionRepository oraclePaperQuestionRepo;
    @Autowired private OracleExamResultRepository oracleExamResultRepo;

    // === SQL Server Repositories ===
    @Autowired private SqlServerUserRepository sqlServerUserRepo;
    @Autowired private SqlServerQuestionRepository sqlServerQuestionRepo;
    @Autowired private SqlServerPaperRepository sqlServerPaperRepo;
    @Autowired private SqlServerPaperQuestionRepository sqlServerPaperQuestionRepo;
    @Autowired private SqlServerExamResultRepository sqlServerExamResultRepo;

    // ==========================================
    // 1. 原有功能：三库数据量实时核对 (Table Status)
    // ==========================================
    @GetMapping("/table-status")
    public Map<String, Object> getTableStatus() {
        List<Map<String, Object>> list = new ArrayList<>();

        list.add(buildRow("用户表 (sys_user)", mysqlUserRepo.count(), oracleUserRepo.count(), sqlServerUserRepo.count()));
        list.add(buildRow("题库表 (question)", mysqlQuestionRepo.count(), oracleQuestionRepo.count(), sqlServerQuestionRepo.count()));
        list.add(buildRow("试卷表 (paper)", mysqlPaperRepo.count(), oraclePaperRepo.count(), sqlServerPaperRepo.count()));
        list.add(buildRow("试卷题目关联 (paper_question)", mysqlPaperQuestionRepo.count(), oraclePaperQuestionRepo.count(), sqlServerPaperQuestionRepo.count()));
        list.add(buildRow("考试成绩表 (exam_result)", mysqlExamResultRepo.count(), oracleExamResultRepo.count(), sqlServerExamResultRepo.count()));

        return Map.of("code", 200, "data", list);
    }

    // ==========================================
    // 2. ✅ 新增功能：移动端/大屏监控数据 (Dashboard)
    // ==========================================
    @GetMapping("/dashboard")
    public Map<String, Object> getDashboardData() {
        // 1. 每日趋势 (折线图/柱状图数据)
        List<Map<String, Object>> trend = mysqlSyncLogRepo.findDailyStats();

        // 2. 整体分布 (饼图数据)
        Map<String, Object> distribution = mysqlSyncLogRepo.findTotalStatusDist();

        // 3. 最新异常 (异常报表列表)
        List<SyncLog> recentErrors = mysqlSyncLogRepo.findTop10ByStatusOrderByCreateTimeDesc("FAIL");

        return Map.of(
                "code", 200,
                "data", Map.of(
                        "trend", trend,
                        "distribution", distribution,
                        "recentErrors", recentErrors
                )
        );
    }

    // 辅助方法：构建一行数据并判断状态
    private Map<String, Object> buildRow(String tableName, long mysql, long oracle, long sqlserver) {
        Map<String, Object> row = new HashMap<>();
        row.put("tableName", tableName);
        row.put("mysqlCount", mysql);
        row.put("oracleCount", oracle);
        row.put("sqlServerCount", sqlserver);

        // 判断状态：只要有一个不相等，就是“数据不一致”
        boolean isSynced = (mysql == oracle) && (mysql == sqlserver);
        row.put("status", isSynced ? "SYNCED" : "DIFF");

        return row;
    }
}
