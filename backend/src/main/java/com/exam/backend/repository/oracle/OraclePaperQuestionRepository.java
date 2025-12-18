package com.exam.backend.repository.oracle;

import com.exam.backend.entity.PaperQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OraclePaperQuestionRepository extends JpaRepository<PaperQuestion, PaperQuestion.PaperQuestionId> {
    List<PaperQuestion> findByPaperId(Long paperId);
}