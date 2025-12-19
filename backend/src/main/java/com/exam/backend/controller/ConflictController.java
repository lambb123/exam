package com.exam.backend.controller;

import com.exam.backend.controller.dto.ConflictDTO;
import com.exam.backend.entity.Paper;
import com.exam.backend.entity.User;
import com.exam.backend.repository.mysql.MysqlPaperRepository;
import com.exam.backend.repository.mysql.MysqlUserRepository;
import com.exam.backend.repository.oracle.OraclePaperRepository;
import com.exam.backend.repository.oracle.OracleUserRepository;
import com.exam.backend.repository.sqlserver.SqlServerPaperRepository; // 新增 SQL Server 依赖
import com.exam.backend.repository.sqlserver.SqlServerUserRepository; // 新增 SQL Server 依赖
import com.exam.backend.service.SyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/conflict")
@CrossOrigin(origins = "*")
public class ConflictController {

    // === MySQL 源 ===
    @Autowired private MysqlUserRepository mysqlUserRepo;
    @Autowired private MysqlPaperRepository mysqlPaperRepo;

    // === Oracle 目标 ===
    @Autowired private OracleUserRepository oracleUserRepo;
    @Autowired private OraclePaperRepository oraclePaperRepo;

    // === SQL Server 目标 (新增) ===
    @Autowired private SqlServerUserRepository sqlServerUserRepo;
    @Autowired private SqlServerPaperRepository sqlServerPaperRepo;

    @Autowired private SyncService syncService;

    // 获取冲突列表
    @GetMapping("/list")
    public Map<String, Object> getConflictList() {
        List<ConflictDTO> conflicts = new ArrayList<>();
        long tempIdCounter = 1;

        // =======================================================
        // 1. 检查用户表 (sys_user)
        // =======================================================
        List<User> mysqlUsers = mysqlUserRepo.findAll();

        // 获取 Oracle 和 SQL Server 的全量数据映射
        Map<Long, User> oracleUserMap = oracleUserRepo.findAll().stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, User> sqlServerUserMap = sqlServerUserRepo.findAll().stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        for (User mUser : mysqlUsers) {
            // Check Oracle
            checkUserConflict(conflicts, tempIdCounter++, "Oracle", mUser, oracleUserMap.get(mUser.getId()));

            // Check SQL Server
            checkUserConflict(conflicts, tempIdCounter++, "SQL Server", mUser, sqlServerUserMap.get(mUser.getId()));
        }

        // =======================================================
        // 2. 检查试卷表 (paper)
        // =======================================================
        List<Paper> mysqlPapers = mysqlPaperRepo.findAll();

        Map<Long, Paper> oraclePaperMap = oraclePaperRepo.findAll().stream()
                .collect(Collectors.toMap(Paper::getId, Function.identity()));
        Map<Long, Paper> sqlServerPaperMap = sqlServerPaperRepo.findAll().stream()
                .collect(Collectors.toMap(Paper::getId, Function.identity()));

        for (Paper mPaper : mysqlPapers) {
            // Check Oracle
            checkPaperConflict(conflicts, tempIdCounter++, "Oracle", mPaper, oraclePaperMap.get(mPaper.getId()));

            // Check SQL Server
            checkPaperConflict(conflicts, tempIdCounter++, "SQL Server", mPaper, sqlServerPaperMap.get(mPaper.getId()));
        }

        return Map.of("code", 200, "data", conflicts);
    }

    // 处理/解决冲突
    @PostMapping("/resolve")
    public Map<String, Object> resolveConflict(@RequestBody Map<String, String> payload) {
        String tableName = payload.get("tableName");
        String sourceIdStr = payload.get("sourceId");
        String action = payload.get("action"); // force, ignore

        if ("ignore".equals(action)) {
            return Map.of("code", 200, "msg", "已忽略");
        }

        try {
            Long id = Long.parseLong(sourceIdStr);

            // 根据表名，同时修复 Oracle 和 SQL Server
            if ("sys_user".equalsIgnoreCase(tableName)) {
                syncService.syncSingleUserToOracle(id);
                syncService.syncSingleUserToSqlServer(id); // 修复 SQL Server
            } else if ("paper".equalsIgnoreCase(tableName)) {
                syncService.syncSinglePaperToOracle(id);
                syncService.syncSinglePaperToSqlServer(id); // 修复 SQL Server
            }
            // 后续可以在这里扩展 Question, ExamResult 等表的单条修复逻辑

            return Map.of("code", 200, "msg", "全平台强制同步成功 (Oracle & SQL Server)");
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("code", 500, "msg", "处理失败: " + e.getMessage());
        }
    }

    // ==========================================
    // 辅助方法：比对用户逻辑
    // ==========================================
    private void checkUserConflict(List<ConflictDTO> list, long tempId, String targetDbName, User source, User target) {
        if (target == null) {
            // 缺失
            list.add(new ConflictDTO(tempId, "sys_user", "MISSING_IN_TARGET",
                    targetDbName + " 备库缺少用户 ID: " + source.getId() + " (" + source.getUsername() + ")",
                    String.valueOf(source.getId())));
        } else {
            // 存在但数据不一致 (对比用户名和角色)
            boolean isDiff = !Objects.equals(source.getUsername(), target.getUsername()) ||
                    !Objects.equals(source.getRole(), target.getRole());
            if (isDiff) {
                list.add(new ConflictDTO(tempId, "sys_user", "DATA_MISMATCH",
                        "用户信息不一致 [" + targetDbName + "]。主库: " + source.getUsername() + ", 备库: " + target.getUsername(),
                        String.valueOf(source.getId())));
            }
        }
    }

    // ==========================================
    // 辅助方法：比对试卷逻辑
    // ==========================================
    private void checkPaperConflict(List<ConflictDTO> list, long tempId, String targetDbName, Paper source, Paper target) {
        if (target == null) {
            list.add(new ConflictDTO(tempId, "paper", "MISSING_IN_TARGET",
                    targetDbName + " 备库缺少试卷 ID: " + source.getId(),
                    String.valueOf(source.getId())));
        } else {
            // 对比试卷名和总分
            boolean isDiff = !Objects.equals(source.getPaperName(), target.getPaperName()) ||
                    !Objects.equals(source.getTotalScore(), target.getTotalScore());
            if (isDiff) {
                list.add(new ConflictDTO(tempId, "paper", "DATA_MISMATCH",
                        "试卷信息不一致 [" + targetDbName + "]。主库: " + source.getPaperName(),
                        String.valueOf(source.getId())));
            }
        }
    }
}