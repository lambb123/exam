package com.exam.backend.repository.sqlserver;

import com.exam.backend.entity.Paper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SqlServerPaperRepository extends JpaRepository<Paper, Long> {
    List<Paper> findByTeacherId(Long teacherId);
}