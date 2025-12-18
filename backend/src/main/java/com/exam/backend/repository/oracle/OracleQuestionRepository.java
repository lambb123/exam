package com.exam.backend.repository.oracle;

import com.exam.backend.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OracleQuestionRepository extends JpaRepository<Question, Long> {
}