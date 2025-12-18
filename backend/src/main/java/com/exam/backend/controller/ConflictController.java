package com.exam.backend.controller;

import com.exam.backend.controller.dto.ConflictDTO;
import com.exam.backend.entity.Paper;
import com.exam.backend.entity.User;
import com.exam.backend.repository.mysql.MysqlPaperRepository;
import com.exam.backend.repository.mysql.MysqlUserRepository;
import com.exam.backend.repository.oracle.OraclePaperRepository;
import com.exam.backend.repository.oracle.OracleUserRepository;
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

    @Autowired private MysqlUserRepository mysqlUserRepo;
    @Autowired private OracleUserRepository oracleUserRepo;

    @Autowired private MysqlPaperRepository mysqlPaperRepo;
    @Autowired private OraclePaperRepository oraclePaperRepo;

    @Autowired private SyncService syncService;

    // 获取冲突列表
    @GetMapping("/list")
    public Map<String, Object> getConflictList() {
        List<ConflictDTO> conflicts = new ArrayList<>();
        long tempIdCounter = 1;

        // === 1. 检查用户表 (sys_user) ===
        List<User> mysqlUsers = mysqlUserRepo.findAll();
        List<User> oracleUsers = oracleUserRepo.findAll();

        // 转为 Map 方便查找: ID -> User
        Map<Long, User> oracleUserMap = oracleUsers.stream().collect(Collectors.toMap(User::getId, Function.identity()));

        for (User mUser : mysqlUsers) {
            User oUser = oracleUserMap.get(mUser.getId());
            if (oUser == null) {
                // 情况A: Oracle 缺失数据
                conflicts.add(new ConflictDTO(tempIdCounter++, "sys_user", "MISSING_IN_TARGET",
                        "Oracle 备库缺少用户 ID: " + mUser.getId() + " (" + mUser.getUsername() + ")",
                        String.valueOf(mUser.getId())));
            } else {
                // 情况B: 数据存在但内容不一致 (简单对比几个核心字段)
                boolean isDiff = !Objects.equals(mUser.getUsername(), oUser.getUsername()) ||
                        !Objects.equals(mUser.getRole(), oUser.getRole());
                if (isDiff) {
                    conflicts.add(new ConflictDTO(tempIdCounter++, "sys_user", "DATA_MISMATCH",
                            "用户信息不一致。主库: " + mUser.getUsername() + ", 备库: " + oUser.getUsername(),
                            String.valueOf(mUser.getId())));
                }
            }
        }

        // === 2. 检查试卷表 (paper) ===
        List<Paper> mysqlPapers = mysqlPaperRepo.findAll();
        List<Paper> oraclePapers = oraclePaperRepo.findAll();
        Map<Long, Paper> oraclePaperMap = oraclePapers.stream().collect(Collectors.toMap(Paper::getId, Function.identity()));

        for (Paper mPaper : mysqlPapers) {
            Paper oPaper = oraclePaperMap.get(mPaper.getId());
            if (oPaper == null) {
                conflicts.add(new ConflictDTO(tempIdCounter++, "paper", "MISSING_IN_TARGET",
                        "Oracle 备库缺少试卷 ID: " + mPaper.getId(),
                        String.valueOf(mPaper.getId())));
            } else {
                if (!Objects.equals(mPaper.getPaperName(), oPaper.getPaperName()) ||
                        !Objects.equals(mPaper.getTotalScore(), oPaper.getTotalScore())) {
                    conflicts.add(new ConflictDTO(tempIdCounter++, "paper", "DATA_MISMATCH",
                            "试卷信息不一致 (名称或总分)。主库: " + mPaper.getPaperName(),
                            String.valueOf(mPaper.getId())));
                }
            }
        }

        // 可以继续添加其他表的检查...

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

            // 根据表名，同时调用 Oracle 和 SQL Server 的单条修复方法
            if ("sys_user".equalsIgnoreCase(tableName)) {
                syncService.syncSingleUserToOracle(id);
                syncService.syncSingleUserToSqlServer(id); // ✅ 新增：同时也修复 SQL Server
            } else if ("paper".equalsIgnoreCase(tableName)) {
                syncService.syncSinglePaperToOracle(id);
                syncService.syncSinglePaperToSqlServer(id); // ✅ 新增：同时也修复 SQL Server
            }
            // 如果有其他表 (question, exam_result)，也可以在这里继续加 else if

            return Map.of("code", 200, "msg", "强制同步成功 (Oracle & SQL Server)");
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("code", 500, "msg", "处理失败: " + e.getMessage());
        }
    }
}