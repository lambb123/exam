package com.exam.backend.controller.dto;

import lombok.Data;

@Data
public class PaperCreateRequest {
    private String paperName;
    private Long teacherId;  // 谁出的卷子
    private Integer questionCount; // 要抽多少道题
}