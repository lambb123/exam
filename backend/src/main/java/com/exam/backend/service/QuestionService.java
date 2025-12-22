package com.exam.backend.service;

import com.exam.backend.common.DbSwitchContext;
import com.exam.backend.entity.Question;
import com.exam.backend.repository.mysql.MysqlQuestionRepository;
import com.exam.backend.repository.oracle.OracleQuestionRepository;
import com.exam.backend.repository.sqlserver.SqlServerQuestionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class QuestionService {

    @Autowired
    private MysqlQuestionRepository mysqlQuestionRepository;

    @Autowired
    private OracleQuestionRepository oracleQuestionRepository;

    @Autowired
    private SqlServerQuestionRepository sqlServerQuestionRepository;

    @Autowired
    private SyncService syncService; // 注入同步服务

    // 获取所有试题 (默认走 MySQL 读)
    public List<Question> findAll() {
        return mysqlQuestionRepository.findAll();
    }

    // 添加试题 (支持动态切换主写入库)
    public Question add(Question question) {
        // 1. 设置时间 (修正：只设置 updateTime，不设置 createTime)
        question.setUpdateTime(LocalDateTime.now());

        // 2. 获取当前系统设置的“主库”
        String currentDb = DbSwitchContext.getCurrentMasterDb();
        Question result = null;

        System.out.println(">>> [业务层] 正在向主库 [" + currentDb + "] 写入数据...");

        switch (currentDb) {
            case "Oracle":
                // === 模式 A: 写 Oracle -> 同步回 MySQL ===
                result = oracleQuestionRepository.save(question);
                // 立即触发同步，把 Oracle 的新数据拉回 MySQL 以便前端显示
                syncService.syncQuestionsBidirectional();
                break;

            case "SQLServer":
                // === 模式 B: 写 SQL Server -> 同步回 MySQL ===
                result = sqlServerQuestionRepository.save(question);
                // 立即触发同步
                syncService.syncQuestionsBidirectional();
                break;

            case "MySQL":
            default:
                // === 模式 C: 写 MySQL (默认) ===
                // 这种情况下，AOP 切面或定时任务会负责后续同步
                result = mysqlQuestionRepository.save(question);
                break;
        }

        return result;
    }

    // 删除试题
    public void delete(Long id) {
        // 旧代码：mysqlQuestionRepository.deleteById(id);

        // 【修改】调用三库全局删除，防止数据复活
        syncService.deleteQuestionGlobally(id);
    }
}