package com.exam.backend.controller;

import com.exam.backend.entity.Question;
import com.exam.backend.repository.oracle.OracleQuestionRepository;
import com.exam.backend.repository.sqlserver.SqlServerQuestionRepository;
import com.exam.backend.service.SyncService;
import org.springframework.beans.factory.annotation.Autowired;
// 注意：这里去掉了 Transactional 的引用，因为我们要手动控制提交时机
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 调试专用控制器：用于演示直接向 Oracle/SQL Server 写入数据，
 * 并立即触发反向同步。
 */
@RestController
@RequestMapping("/api/debug")
@CrossOrigin(origins = "*")
public class DebugController {

    @Autowired
    private OracleQuestionRepository oracleQuestionRepo;

    @Autowired
    private SqlServerQuestionRepository sqlServerQuestionRepo;

    // 【新增】注入同步服务
    @Autowired
    private SyncService syncService;

    // 1. 直接向 Oracle 插入一条题目
    @PostMapping("/oracle/question")
    // 【关键】去掉 @Transactional，确保 save 后立即 Commit，这样 syncService 才能读到新数据
    public Map<String, Object> addToOracle(@RequestBody Question q) {
        Map<String, Object> map = new HashMap<>();
        try {
            // 1. 补全时间字段
            if (q.getUpdateTime() == null) q.setUpdateTime(LocalDateTime.now());

            // 2. 强制保存到 Oracle (Repository 自带事务，执行完这行即提交)
            oracleQuestionRepo.save(q);
            System.out.println(">>> [演示] 已向 Oracle 写入题目: " + q.getContent());

            // 3. 【核心】立即触发“题目表”的双向同步
            //    这会把刚才写入 Oracle 的数据瞬间拉回 MySQL
            syncService.syncQuestionsBidirectional();

            map.put("code", 200);
            map.put("msg", "写入 Oracle 并同步成功！请立即刷新列表查看。");
        } catch (Exception e) {
            e.printStackTrace();
            map.put("code", 500);
            map.put("msg", "Oracle 写入失败: " + e.getMessage());
        }
        return map;
    }

    // 2. 直接向 SQL Server 插入一条题目
    @PostMapping("/sqlserver/question")
    // 【关键】去掉 @Transactional
    public Map<String, Object> addToSqlServer(@RequestBody Question q) {
        Map<String, Object> map = new HashMap<>();
        try {
            if (q.getUpdateTime() == null) q.setUpdateTime(LocalDateTime.now());

            // 1. 保存到 SQL Server
            sqlServerQuestionRepo.save(q);
            System.out.println(">>> [演示] 已向 SQL Server 写入题目: " + q.getContent());

            // 2. 【核心】立即触发同步
            syncService.syncQuestionsBidirectional();

            map.put("code", 200);
            map.put("msg", "写入 SQL Server 并同步成功！请立即刷新列表查看。");
        } catch (Exception e) {
            e.printStackTrace();
            map.put("code", 500);
            map.put("msg", "SQL Server 写入失败: " + e.getMessage());
        }
        return map;
    }
}