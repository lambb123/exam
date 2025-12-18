package com.exam.backend.service;

import com.exam.backend.controller.dto.ExamSubmitRequest;
import com.exam.backend.entity.*;
import com.exam.backend.repository.mysql.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal; // 必须导入这个
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
            // 假设存的是 {"101": "A", "102": "A,B"} 这种格式
            if (result.getStudentAnswers() != null) {
                studentAnswers = mapper.readValue(result.getStudentAnswers(), new TypeReference<Map<Long, String>>() {});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 4. 将学生答案合并到题目列表中
        for (Map<String, Object> qItem : questions) {
            Question q = (Question) qItem.get("question");
            // 获取该题学生填的答案
            String myAns = studentAnswers.getOrDefault(q.getId(), ""); // 如果用Long做key
            // 兼容性处理：如果上面的Map key是String
            if (myAns.isEmpty()) {
                myAns = studentAnswers.getOrDefault(String.valueOf(q.getId()), "");
            }

            qItem.put("studentAnswer", myAns);

            // 判断正误 (简单判断)
            boolean isCorrect = myAns.equals(q.getAnswer());
            qItem.put("isCorrect", isCorrect);
        }

        Map<String, Object> map = new HashMap<>();
        map.put("examResult", result);
        map.put("paperInfo", paperDetail.get("paperInfo"));
        map.put("questions", questions);

        return map;
    }


}
