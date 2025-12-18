package com.exam.backend.repository.mysql;

import com.exam.backend.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MysqlQuestionRepository extends JpaRepository<Question, Long> {
    // 可以在这里加：根据难度查询、根据类型查询等
}