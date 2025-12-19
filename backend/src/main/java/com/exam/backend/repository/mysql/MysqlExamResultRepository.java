package com.exam.backend.repository.mysql;

import com.exam.backend.entity.ExamResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface MysqlExamResultRepository extends JpaRepository<ExamResult, Long> {
    // 查某个学生的所有成绩
    List<ExamResult> findByStudentId(Long studentId);

    // 复杂 SQL 示例：
    // 查询每张试卷的 平均分、最高分，并关联查出出卷老师的名字
    // 包含：多表连接 (exam_result -> paper -> sys_user)、聚合函数 (AVG, MAX)、分组 (GROUP BY)
    @Query(value = """
            SELECT 
               p.paper_name AS paperName, 
               t.real_name AS teacherName, 
               COUNT(r.id) AS studentCount, 
               AVG(r.score) AS avgScore, 
               MAX(r.score) AS maxScore 
            FROM paper p 
            LEFT JOIN exam_result r ON p.id = r.paper_id 
            LEFT JOIN sys_user t ON p.teacher_id = t.id 
            GROUP BY p.id, p.paper_name, t.real_name 
            HAVING COUNT(r.id) > 0 
            ORDER BY avgScore DESC
            """,
            nativeQuery = true)
    List<Map<String, Object>> findPaperStatistics();

    @Query(value = """
        SELECT 
            r.id,
            u.real_name AS studentName,
            p.paper_name AS paperName,
            r.score,
            (SELECT ROUND(AVG(r2.score), 1) FROM exam_result r2 WHERE r2.paper_id = r.paper_id) AS avgScore
        FROM exam_result r
        LEFT JOIN sys_user u ON r.student_id = u.id
        LEFT JOIN paper p ON r.paper_id = p.id
        WHERE 
            (:studentName IS NULL OR :studentName = '' OR u.real_name LIKE CONCAT('%', :studentName, '%'))
            AND (:paperName IS NULL OR :paperName = '' OR p.paper_name LIKE CONCAT('%', :paperName, '%'))
            
            -- ✅ 核心修改：使用 >=
            AND r.score >= (
                SELECT AVG(r3.score) 
                FROM exam_result r3 
                WHERE r3.paper_id = r.paper_id
            )
        ORDER BY r.score DESC
        """, nativeQuery = true)
    List<Map<String, Object>> findAboveAverageStudents(
            @Param("studentName") String studentName,
            @Param("paperName") String paperName
    );

    // 2. ✅ 新增：查询所有成绩（默认显示用，不去掉低分）
    // 为了让前端表格复用，我们这里也查出 avgScore
    @Query(value = """
        SELECT 
            r.id,
            u.real_name AS studentName,
            p.paper_name AS paperName,
            r.score,
            (SELECT ROUND(AVG(r2.score), 1) FROM exam_result r2 WHERE r2.paper_id = r.paper_id) AS avgScore
        FROM exam_result r
        LEFT JOIN sys_user u ON r.student_id = u.id
        LEFT JOIN paper p ON r.paper_id = p.id
        WHERE 
            (:studentName IS NULL OR :studentName = '' OR u.real_name LIKE CONCAT('%', :studentName, '%'))
            AND (:paperName IS NULL OR :paperName = '' OR p.paper_name LIKE CONCAT('%', :paperName, '%'))
        ORDER BY r.id DESC
        """, nativeQuery = true)
    List<Map<String, Object>> findAllWithAvg(
            @Param("studentName") String studentName,
            @Param("paperName") String paperName
    );
}
