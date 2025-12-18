package com.exam.backend.controller.dto;

import lombok.Data;
import java.util.Map;

@Data
public class ExamSubmitRequest {
    private Long studentId;
    private Long paperId;

    // 前端传来的答案集合：key是题目ID，value是答案字符串
    private Map<Long, String> answers;
}