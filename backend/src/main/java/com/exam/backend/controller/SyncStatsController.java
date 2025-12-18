package com.exam.backend.controller;

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
@RequestMapping("/api/monitor")
@CrossOrigin(origins = "*")
public class SyncStatsController {

    // === MySQL Repositories ===
    @Autowired private MysqlUserRepository mysqlUserRepo;
    @Autowired private MysqlQuestionRepository mysqlQuestionRepo;
    @Autowired private MysqlPaperRepository mysqlPaperRepo;
    @Autowired private MysqlPaperQuestionRepository mysqlPaperQuestionRepo;
    @Autowired private MysqlExamResultRepository mysqlExamResultRepo;

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

    @GetMapping("/table-status")
    public Map<String, Object> getTableStatus() {
        List<Map<String, Object>> list = new ArrayList<>();

        // 1. 用户表 (sys_user)
        list.add(buildRow("用户表 (sys_user)",
                mysqlUserRepo.count(),
                oracleUserRepo.count(),
                sqlServerUserRepo.count()));

        // 2. 题库表 (question)
        list.add(buildRow("题库表 (question)",
                mysqlQuestionRepo.count(),
                oracleQuestionRepo.count(),
                sqlServerQuestionRepo.count()));

        // 3. 试卷表 (paper)
        list.add(buildRow("试卷表 (paper)",
                mysqlPaperRepo.count(),
                oraclePaperRepo.count(),
                sqlServerPaperRepo.count()));

        // 4. 试卷题目关联表 (paper_question)
        list.add(buildRow("试卷题目关联 (paper_question)",
                mysqlPaperQuestionRepo.count(),
                oraclePaperQuestionRepo.count(),
                sqlServerPaperQuestionRepo.count()));

        // 5. 考试成绩表 (exam_result)
        list.add(buildRow("考试成绩表 (exam_result)",
                mysqlExamResultRepo.count(),
                oracleExamResultRepo.count(),
                sqlServerExamResultRepo.count()));

        return Map.of("code", 200, "data", list);
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
