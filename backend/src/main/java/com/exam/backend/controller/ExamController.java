package com.exam.backend.controller;

import com.exam.backend.controller.dto.ExamSubmitRequest;
import com.exam.backend.entity.ExamResult;
import com.exam.backend.entity.Paper;
import com.exam.backend.entity.PaperQuestion;
import com.exam.backend.repository.mysql.MysqlPaperQuestionRepository;
import com.exam.backend.service.ExamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exam")
@CrossOrigin(origins = "*")
public class ExamController {

    @Autowired
    private ExamService examService;


    @Autowired
    private MysqlPaperQuestionRepository paperQuestionRepository;


    // 进入考试，获取试卷题目
    @GetMapping("/{paperId}")
    public Map<String, Object> getPaper(@PathVariable Long paperId) {
        // 1. 查试卷基本信息
        Paper paper = examService.getPaperDetail(paperId);

        // 2. 【核心修复】手动查这张卷子里的所有题目
        List<PaperQuestion> questions = paperQuestionRepository.findByPaperId(paperId);

        // 3. 拼装返回数据
        Map<String, Object> data = new HashMap<>();
        data.put("paperInfo", paper);   // 试卷名、总分等
        data.put("questionList", questions); // 具体的题目列表

        return Map.of("code", 200, "data", data);
    }

    // 提交试卷，返回成绩
    @PostMapping("/submit")
    public Map<String, Object> submit(@RequestBody ExamSubmitRequest request) {
        ExamResult result = examService.submitExam(request);
        return Map.of("code", 200, "msg", "交卷成功", "data", result);
    }

    // 查看我的成绩
    @GetMapping("/result/list/{studentId}")
    public Map<String, Object> myResult(@PathVariable Long studentId) {
        List<ExamResult> list = examService.getStudentResults(studentId);
        return Map.of("code", 200, "data", list);
    }

    @GetMapping("/result/all")
    public Map<String, Object> allResults() {
        List<ExamResult> list = examService.findAllResults();
        return Map.of("code", 200, "data", list);
    }
}
