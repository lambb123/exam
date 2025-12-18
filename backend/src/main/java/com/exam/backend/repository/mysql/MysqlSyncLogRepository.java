package com.exam.backend.repository.mysql;

import com.exam.backend.entity.SyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MysqlSyncLogRepository extends JpaRepository<SyncLog, Long> {
    // 这里继承了 JpaRepository，自带 save(), findAll() 等方法，无需额外写代码
}
