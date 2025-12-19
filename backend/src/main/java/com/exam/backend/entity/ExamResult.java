package com.exam.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "exam_result")
public class ExamResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private User student;

    @ManyToOne
    @JoinColumn(name = "paper_id")
    private Paper paper;

    private BigDecimal score;

    @Lob // 告诉 Hibernate 这是一个大文本字段
    @Column(name = "student_answers") //
    private String studentAnswers;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    public ExamResult() {
        this.createTime = LocalDateTime.now();
    }
}