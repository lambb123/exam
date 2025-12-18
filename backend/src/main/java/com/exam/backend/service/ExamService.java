package com.exam.backend.service;

import com.exam.backend.controller.dto.ExamSubmitRequest;
import com.exam.backend.entity.*;
import com.exam.backend.repository.mysql.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal; // 必须导入这个
import java.util.List;
import java.util.Map;

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

    // 1. 获取试卷详情
    public Paper getPaperDetail(Long paperId) {
        return paperRepository.findById(paperId)
                .orElseThrow(() -> new RuntimeException("试卷不存在"));
    }

    // 2. 提交并判分（这里针对 BigDecimal 做了适配）
    public ExamResult submitExam(ExamSubmitRequest request) {
        // 获取试卷和学生
        Paper paper = paperRepository.findById(request.getPaperId()).orElseThrow();
        User student = userRepository.findById(request.getStudentId()).orElseThrow();

        // 获取这张卷子所有的题目关联信息
        List<PaperQuestion> paperQuestions = paperQuestionRepository.findByPaperId(request.getPaperId());

        // 【修正点1】初始化必须用 BigDecimal.ZERO
        BigDecimal totalScore = BigDecimal.ZERO;

        Map<Long, String> studentAnswers = request.getAnswers();

        // 遍历试卷里的每一道题
        for (PaperQuestion pq : paperQuestions) {
            Question q = pq.getQuestion();
            // 获取学生对这道题的答案
            String myAnswer = studentAnswers.get(q.getId());

            // 比对答案（忽略大小写）
            if (myAnswer != null && myAnswer.equalsIgnoreCase(q.getAnswer())) {
                // 【修正点2】整数转 BigDecimal 才能相加
                // 假设 pq.getScore() 还是 Integer，我们需要包一层
                BigDecimal scoreToAdd = new BigDecimal(pq.getScore());
                totalScore = totalScore.add(scoreToAdd);
            }
        }

        // 保存成绩
        ExamResult result = new ExamResult();
        result.setStudent(student);
        result.setPaper(paper);
        result.setScore(totalScore); // 现在这里类型匹配了

        return examResultRepository.save(result);
    }

    // 获取某学生的成绩列表
    public List<ExamResult> getStudentResults(Long studentId) {
        return examResultRepository.findByStudentId(studentId);
    }

    public List<ExamResult> findAllResults() {
        return examResultRepository.findAll();
    }

}
