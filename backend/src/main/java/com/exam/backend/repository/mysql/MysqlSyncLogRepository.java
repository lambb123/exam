package com.exam.backend.repository.mysql;

import com.exam.backend.entity.SyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface MysqlSyncLogRepository extends JpaRepository<SyncLog, Long> {
    // 1. 获取最新的异常日志 (用于报表下方的列表)
    List<SyncLog> findTop10ByStatusOrderByCreateTimeDesc(String status);

    // 2. 统计近7天的同步情况
    // ✅ 修改：表名改为 sys_sync_log，确保与 Entity 对应
    @Query(value = """
        SELECT 
            DATE_FORMAT(create_time, '%Y-%m-%d') as logDate,
            COUNT(*) as totalCount,
            SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) as successCount,
            SUM(CASE WHEN status = 'FAIL' THEN 1 ELSE 0 END) as failCount
        FROM sys_sync_log
        WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
        GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d')
        ORDER BY logDate ASC
    """, nativeQuery = true)
    List<Map<String, Object>> findDailyStats();

    // 3. 统计整体成功率
    // ✅ 修改：表名改为 sys_sync_log
    @Query(value = """
        SELECT 
            SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) as success,
            SUM(CASE WHEN status = 'FAIL' THEN 1 ELSE 0 END) as fail
        FROM sys_sync_log
    """, nativeQuery = true)
    Map<String, Object> findTotalStatusDist();
}
