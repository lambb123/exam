package com.exam.backend.controller;

import com.exam.backend.controller.dto.ConflictDTO;
import com.exam.backend.entity.User;
import com.exam.backend.repository.mysql.MysqlUserRepository;
import com.exam.backend.service.SyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conflict")
@CrossOrigin(origins = "*")
public class ConflictController {

    @Autowired private SyncService syncService;
    @Autowired private MysqlUserRepository mysqlUserRepo;

    // 获取冲突列表 (直接调用 SyncService 中的复用逻辑)
    @GetMapping("/list")
    public Map<String, Object> getConflictList() {
        // 调用 Service 中的统一检测逻辑
        List<ConflictDTO> conflicts = syncService.detectConflicts();
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

            if ("sys_user".equalsIgnoreCase(tableName)) {
                User u = mysqlUserRepo.findById(id).orElse(null);
                if (u != null) {
                    // 强制覆盖 Oracle 和 SQL Server
                    syncService.syncToOracleNative(u);
                    syncService.syncToSqlServerNative(u);
                } else {
                    return Map.of("code", 400, "msg", "源数据不存在(MySQL)，无法同步");
                }

            } else if ("paper".equalsIgnoreCase(tableName)) {
                // 强制修复试卷
                syncService.syncSinglePaperToOracle(id);
                syncService.syncSinglePaperToSqlServer(id);
            }

            return Map.of("code", 200, "msg", "全平台强制同步成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("code", 500, "msg", "处理失败: " + e.getMessage());
        }
    }
}