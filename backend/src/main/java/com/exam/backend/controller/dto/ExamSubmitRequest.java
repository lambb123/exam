package com.exam.backend.controller.dto;

import lombok.Data;
import java.util.Map;

@Data
public class ExamSubmitRequest {
    private Long studentId;
    private Long paperId;
    // 键是题目ID，值是学生的答案（例如：{1: "A", 2: "B"}）
    private Map<Long, String> answers;
}