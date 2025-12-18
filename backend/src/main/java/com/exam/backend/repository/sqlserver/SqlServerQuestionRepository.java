package com.exam.backend.repository.sqlserver;

import com.exam.backend.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SqlServerQuestionRepository extends JpaRepository<Question, Long> {
}