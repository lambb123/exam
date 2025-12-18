package com.exam.backend.repository.mysql;

import com.exam.backend.entity.Paper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MysqlPaperRepository extends JpaRepository<Paper, Long> {
    // 查询某个老师出的所有卷子
    List<Paper> findByTeacherId(Long teacherId);
}