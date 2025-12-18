package com.exam.backend.repository.mysql;

import com.exam.backend.entity.ExamResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MysqlExamResultRepository extends JpaRepository<ExamResult, Long> {
    // 查某个学生的所有成绩
    List<ExamResult> findByStudentId(Long studentId);
}
