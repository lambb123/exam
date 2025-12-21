package com.exam.backend.repository.mysql;

import com.exam.backend.entity.PaperQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MysqlPaperQuestionRepository extends JpaRepository<PaperQuestion, PaperQuestion.PaperQuestionId> {


    void deleteByPaperId(Long paperId);


    // 查某张卷子的所有题目
    List<PaperQuestion> findByPaperId(Long paperId);
}
