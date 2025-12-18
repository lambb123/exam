package com.exam.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

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
}