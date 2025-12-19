package com.exam.backend.repository.mysql;

import com.exam.backend.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface MysqlQuestionRepository extends JpaRepository<Question, Long> {
    // 可以在这里加：根据难度查询、根据类型查询等
    List<Question> findByType(String type);

    /**
     * 【核心代码】体现：
     * 1. 多表连接 (JOIN): Question -> PaperQuestion -> Paper
     * 2. 嵌套子查询 (Subquery): p.total_score > (SELECT AVG(total_score)...)
     * 3. 多条件检索 (Dynamic Condition): (:type IS NULL OR ...)
     */
    @Query(value = """
        SELECT DISTINCT q.id, q.content, q.type, q.difficulty, q.knowledge_point, p.paper_name as sourcePaper
        FROM question q
        LEFT JOIN paper_question pq ON q.id = pq.question_id
        LEFT JOIN paper p ON pq.paper_id = p.id
        WHERE 
            -- 1. 多条件检索：如果 type 为空则忽略该条件，否则精确匹配
            (:type IS NULL OR :type = '' OR q.type = :type)
            -- 2. 多条件检索：如果 content 为空则忽略，否则模糊匹配
            AND (:content IS NULL OR :content = '' OR q.content LIKE CONCAT('%', :content, '%'))
            -- 3. 嵌套子查询：只筛选出那些“属于高分试卷（总分 > 所有试卷平均分）”的题目
            AND p.total_score >= (
                SELECT AVG(p2.total_score) FROM paper p2
            )
        ORDER BY q.id DESC
        """, nativeQuery = true)
    List<Map<String, Object>> findComplexQuestions(
            @Param("type") String type,
            @Param("content") String content
    );

    // 简单实用的多条件查询：类型 + 难度 + 知识点(模糊查询)
    @Query("SELECT q FROM Question q WHERE " +
            "(:type IS NULL OR :type = '' OR q.type = :type) AND " +
            "(:difficulty IS NULL OR :difficulty = '' OR q.difficulty = :difficulty) AND " +
            "(:kp IS NULL OR :kp = '' OR q.knowledgePoint LIKE CONCAT('%', :kp, '%')) " +
            "ORDER BY q.id DESC")
    List<Question> searchByCondition(
            @Param("type") String type,
            @Param("difficulty") String difficulty,
            @Param("kp") String knowledgePoint
    );
}