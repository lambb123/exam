package com.exam.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;

@Data
@Entity
@Table(name = "paper_question")
@IdClass(PaperQuestion.PaperQuestionId.class)
public class PaperQuestion {

    @Id
    @ManyToOne
    @JoinColumn(name = "paper_id")
    private Paper paper;

    @Id
    @ManyToOne
    @JoinColumn(name = "question_id")
    private Question question;

    private Integer score; // 这道题多少分

    // 复合主键类
    @Data
    public static class PaperQuestionId implements Serializable {
        private Long paper;
        private Long question;
    }
}