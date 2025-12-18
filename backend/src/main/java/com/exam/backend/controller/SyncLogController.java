package com.exam.backend.controller;

import com.exam.backend.entity.SyncLog;
import com.exam.backend.repository.mysql.MysqlSyncLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
@CrossOrigin(origins = "*")
public class SyncLogController {

    @Autowired
    private MysqlSyncLogRepository syncLogRepo;

    @GetMapping
    public Map<String, Object> getLogs() {
        // 按开始时间倒序排列，让最新的日志显示在最前面
        List<SyncLog> logs = syncLogRepo.findAll(Sort.by(Sort.Direction.DESC, "startTime"));
        return Map.of("code", 200, "data", logs);
    }
}
