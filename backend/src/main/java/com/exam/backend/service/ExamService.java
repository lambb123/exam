package com.exam.backend.service;

import com.exam.backend.common.DbSwitchContext;
import com.exam.backend.controller.dto.ExamSubmitRequest;
import com.exam.backend.entity.*;
import com.exam.backend.repository.mysql.*;
import com.exam.backend.repository.oracle.*;
import com.exam.backend.repository.sqlserver.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class ExamService {

    // === MySQL Repos ===
    @Autowired private MysqlPaperRepository mysqlPaperRepo;
    @Autowired private MysqlUserRepository mysqlUserRepo;
    @Autowired private MysqlExamResultRepository mysqlExamResultRepo;
    @Autowired private MysqlPaperQuestionRepository mysqlPaperQuestionRepo;

    // === Oracle Repos ===
    @Autowired private OracleExamResultRepository oracleExamResultRepo;

    // === SQL Server Repos ===
    @Autowired private SqlServerExamResultRepository sqlServerExamResultRepo;

    @Autowired private PaperService paperService;
    @Autowired private SyncService syncService;

    // 获取试卷详情 (读 MySQL)
    public Paper getPaperDetail(Long paperId) {
        return mysqlPaperRepo.findById(paperId)
                .orElseThrow(() -> new RuntimeException("试卷不存在"));
    }

    // === 核心改造：提交考试 (支持主库切换) ===
    public ExamResult submitExam(ExamSubmitRequest request) {
        // 1. 准备数据 (读 MySQL)
        Paper paper = mysqlPaperRepo.findById(request.getPaperId()).orElseThrow(() -> new RuntimeException("试卷不存在"));
        User student = mysqlUserRepo.findById(request.getStudentId()).orElseThrow(() -> new RuntimeException("学生不存在"));
        List<PaperQuestion> paperQuestions = mysqlPaperQuestionRepo.findByPaperId(request.getPaperId());

        // 2. 判分逻辑
        BigDecimal totalScore = BigDecimal.ZERO;
        Map<Long, String> studentAnswers = request.getAnswers();

        for (PaperQuestion pq : paperQuestions) {
            Question q = pq.getQuestion();
            String myAnswer = studentAnswers.get(q.getId());
            if (myAnswer != null && myAnswer.equalsIgnoreCase(q.getAnswer())) {
                BigDecimal scoreToAdd = new BigDecimal(pq.getScore());
                totalScore = totalScore.add(scoreToAdd);
            }
        }

        // 3. 构建结果对象
        ExamResult result = new ExamResult();
        result.setStudent(student);
        result.setPaper(paper);
        result.setScore(totalScore);
        if (result.getCreateTime() == null) result.setCreateTime(LocalDateTime.now());
        result.setUpdateTime(LocalDateTime.now());

        // 设置 JSON 答案
        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonAnswers = mapper.writeValueAsString(studentAnswers);
            result.setStudentAnswers(jsonAnswers);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 4. 路由逻辑
        String currentDb = DbSwitchContext.getCurrentMasterDb();
        ExamResult savedResult = null;
        System.out.println(">>> [Exam业务] 正在向主库 [" + currentDb + "] 提交考卷得分: " + totalScore);

        switch (currentDb) {
            case "Oracle":
                savedResult = oracleExamResultRepo.save(result);
                syncService.syncExamResultsBidirectional(); // 立即同步
                break;

            case "SQLServer":
                savedResult = sqlServerExamResultRepo.save(result);
                syncService.syncExamResultsBidirectional(); // 立即同步
                break;

            case "MySQL":
            default:
                savedResult = mysqlExamResultRepo.save(result);
                break;
        }

        return savedResult;
    }

    // 获取某学生的成绩列表
    public List<ExamResult> getStudentResults(Long studentId) {
        return mysqlExamResultRepo.findByStudentId(studentId);
    }

    public List<ExamResult> findAllResults() {
        return mysqlExamResultRepo.findAll();
    }

    // 获取考试结果详情
    public Map<String, Object> getExamResultDetail(Long resultId) {
        ExamResult result = mysqlExamResultRepo.findById(resultId)
                .orElseThrow(() -> new RuntimeException("考试记录不存在"));

        Map<String, Object> paperDetail = paperService.getPaperDetail(result.getPaper().getId());
        List<Map<String, Object>> questions = (List<Map<String, Object>>) paperDetail.get("questionList");

        Map<Long, String> studentAnswers = new HashMap<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            if (result.getStudentAnswers() != null && !result.getStudentAnswers().isEmpty()) {
                studentAnswers = mapper.readValue(result.getStudentAnswers(), new TypeReference<Map<Long, String>>() {});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (Map<String, Object> qItem : questions) {
            Question q = (Question) qItem.get("question");
            String myAns = studentAnswers.get(q.getId());
            if (myAns == null) {
                myAns = studentAnswers.getOrDefault(String.valueOf(q.getId()), "");
            }
            qItem.put("studentAnswer", myAns);
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
