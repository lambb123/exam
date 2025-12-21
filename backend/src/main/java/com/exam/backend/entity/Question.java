package com.exam.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "question")
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Lob
    @Column(nullable = false)
    private String content; // 题目内容

    private String type;       // 单选/多选
    private String difficulty; // 难度

    @Column(name = "knowledge_point")
    private String knowledgePoint; // 知识点

    @Column(name = "answer")
    private String answer;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    public Question() {
        this.updateTime = LocalDateTime.now();
    }

    // 重写 equals: 用于判断内容是否实质变化
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Question q = (Question) o;
        return java.util.Objects.equals(content, q.content) &&
                java.util.Objects.equals(type, q.type) &&
                java.util.Objects.equals(difficulty, q.difficulty) &&
                java.util.Objects.equals(knowledgePoint, q.knowledgePoint) &&
                java.util.Objects.equals(answer, q.answer);
    }
}