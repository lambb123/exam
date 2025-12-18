package com.exam.backend.controller.dto;

import lombok.Data;

@Data
public class PaperCreateRequest {
    private String paperName;
    private Long teacherId;  // 谁出的卷子

    // 各题型抽取的数量
    private Integer singleCount;   // 单选题数量
    private Integer multiCount;    // 多选题数量
    private Integer judgeCount;    // 判断题数量
    private Integer fillCount;     // 填空题数量
    private Integer essayCount;    // 简答题数量
}