package com.exam.backend.service;

import com.exam.backend.controller.dto.ExamSubmitRequest;
import com.exam.backend.entity.*;
import com.exam.backend.repository.mysql.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.*;

@Service
public class ExamService {

    @Autowired
    private MysqlPaperRepository paperRepository;

    @Autowired
    private MysqlUserRepository userRepository;

    @Autowired
    private MysqlExamResultRepository examResultRepository;

    @Autowired
    private MysqlPaperQuestionRepository paperQuestionRepository;

    @Autowired
    private PaperService paperService;

    // 1. 获取试卷详情
    public Paper getPaperDetail(Long paperId) {
        return paperRepository.findById(paperId)
                .orElseThrow(() -> new RuntimeException("试卷不存在"));
    }

    // 2. 提交并判分
    public ExamResult submitExam(ExamSubmitRequest request) {
        // 获取试卷和学生
        Paper paper = paperRepository.findById(request.getPaperId()).orElseThrow(() -> new RuntimeException("试卷不存在"));
        User student = userRepository.findById(request.getStudentId()).orElseThrow(() -> new RuntimeException("学生不存在"));

        // 获取这张卷子所有的题目关联信息
        List<PaperQuestion> paperQuestions = paperQuestionRepository.findByPaperId(request.getPaperId());

        // 初始化分数
        BigDecimal totalScore = BigDecimal.ZERO;

        Map<Long, String> studentAnswers = request.getAnswers();

        // 遍历试卷里的每一道题
        for (PaperQuestion pq : paperQuestions) {
            Question q = pq.getQuestion();
            // 获取学生对这道题的答案
            String myAnswer = studentAnswers.get(q.getId());

            // 比对答案（忽略大小写）
            if (myAnswer != null && myAnswer.equalsIgnoreCase(q.getAnswer())) {
                // 累加分数
                BigDecimal scoreToAdd = new BigDecimal(pq.getScore());
                totalScore = totalScore.add(scoreToAdd);
            }
        }

        // 保存成绩对象
        ExamResult result = new ExamResult();
        result.setStudent(student);
        result.setPaper(paper);
        result.setScore(totalScore);

        // =======================================================
        // 【核心修复】保存学生的答案详情到数据库
        // 之前这里漏掉了，导致所有答卷在前端都显示“未作答”
        // =======================================================
        try {
            ObjectMapper mapper = new ObjectMapper();
            // 将 Map<Long, String> 转换为 JSON 字符串存储
            String jsonAnswers = mapper.writeValueAsString(studentAnswers);
            result.setStudentAnswers(jsonAnswers);
        } catch (Exception e) {
            e.printStackTrace();
            // 即使序列化失败，也建议不要中断提交，但最好记录日志
            System.err.println("答案序列化失败: " + e.getMessage());
        }

        return examResultRepository.save(result);
    }

    // 获取某学生的成绩列表
    public List<ExamResult> getStudentResults(Long studentId) {
        return examResultRepository.findByStudentId(studentId);
    }

    public List<ExamResult> findAllResults() {
        return examResultRepository.findAll();
    }

    /**
     * 获取考试结果详情（用于查看答卷）
     */
    public Map<String, Object> getExamResultDetail(Long resultId) {
        // 1. 获取考试记录
        ExamResult result = examResultRepository.findById(resultId)
                .orElseThrow(() -> new RuntimeException("考试记录不存在"));

        // 2. 获取试卷题目详情 (复用 PaperService 的逻辑)
        Map<String, Object> paperDetail = paperService.getPaperDetail(result.getPaper().getId());
        List<Map<String, Object>> questions = (List<Map<String, Object>>) paperDetail.get("questionList");

        // 3. 解析学生的答案 (数据库存的是 JSON 字符串)
        Map<Long, String> studentAnswers = new HashMap<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            if (result.getStudentAnswers() != null && !result.getStudentAnswers().isEmpty()) {
                // 兼容处理：尝试读取为 Map<Long, String>
                studentAnswers = mapper.readValue(result.getStudentAnswers(), new TypeReference<Map<Long, String>>() {});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 4. 将学生答案合并到题目列表中
        for (Map<String, Object> qItem : questions) {
            Question q = (Question) qItem.get("question");

            // 获取该题学生填的答案 (兼容 Long 和 String 类型的 Key)
            String myAns = studentAnswers.get(q.getId());
            if (myAns == null) {
                myAns = studentAnswers.getOrDefault(String.valueOf(q.getId()), "");
            }

            qItem.put("studentAnswer", myAns);

            // 判断正误
            boolean isCorrect = myAns != null && myAns.equalsIgnoreCase(q.getAnswer());
            qItem.put("isCorrect", isCorrect);
        }

        Map<String, Object> map = new HashMap<>();
        map.put("examResult", result);
        map.put("paperInfo", paperDetail.get("paperInfo"));
        map.put("questions", questions);

        return map;
    }
}
