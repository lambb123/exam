package com.exam.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "paper")
public class Paper {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paper_name")
    private String paperName;

    @Column(name = "total_score")
    private Integer totalScore;

    @ManyToOne
    @JoinColumn(name = "teacher_id")
    private User teacher; // 出卷人

    @Column(name = "create_time")
    private LocalDateTime createTime;

    public Paper() {
        this.createTime = LocalDateTime.now();
    }
}