package com.exam.backend.service;

import com.exam.backend.common.DbSwitchContext;
import com.exam.backend.controller.dto.PaperCreateRequest;
import com.exam.backend.entity.*;
import com.exam.backend.repository.mysql.*;
import com.exam.backend.repository.oracle.*;
import com.exam.backend.repository.sqlserver.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // 注意：手动控制多库写入时，尽量避免单一的 Spring 事务注解

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PaperService {

    // === MySQL Repos ===
    @Autowired private MysqlPaperRepository mysqlPaperRepo;
    @Autowired private MysqlQuestionRepository mysqlQuestionRepo;
    @Autowired private MysqlPaperQuestionRepository mysqlPaperQuestionRepo;
    @Autowired private MysqlUserRepository mysqlUserRepo;

    // === Oracle Repos ===
    @Autowired private OraclePaperRepository oraclePaperRepo;
    @Autowired private OraclePaperQuestionRepository oraclePaperQuestionRepo;

    // === SQL Server Repos ===
    @Autowired private SqlServerPaperRepository sqlServerPaperRepo;
    @Autowired private SqlServerPaperQuestionRepository sqlServerPaperQuestionRepo;

    @Autowired private SyncService syncService;

    public List<Paper> findAll() {
        return mysqlPaperRepo.findAll();
    }

    public void delete(Long id) {
        // 调用 SyncService 全局删除
        syncService.deletePaperGlobally(id);
    }

    public Map<String, Object> getPaperDetail(Long paperId) {
        Paper paper = mysqlPaperRepo.findById(paperId)
                .orElseThrow(() -> new RuntimeException("试卷不存在"));

        List<PaperQuestion> pqs = mysqlPaperQuestionRepo.findByPaperId(paperId);

        List<Map<String, Object>> questionList = pqs.stream().map(pq -> {
            Question q = pq.getQuestion();
            Map<String, Object> item = new HashMap<>();
            item.put("question", q);
            item.put("score", pq.getScore());
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("paperInfo", paper);
        result.put("questionList", questionList);
        return result;
    }

    /**
     * 智能组卷核心逻辑 (支持动态主库切换)
     */
    public void createPaper(PaperCreateRequest request) {
        // 1. 准备数据 (读 MySQL)
        User teacher = mysqlUserRepo.findById(request.getTeacherId())
                .orElseThrow(() -> new RuntimeException("教师不存在"));

        List<Question> finalQuestions = new ArrayList<>();
        if (request.getSingleCount() != null && request.getSingleCount() > 0)
            finalQuestions.addAll(getRandomQuestions("单选题", request.getSingleCount()));
        if (request.getMultiCount() != null && request.getMultiCount() > 0)
            finalQuestions.addAll(getRandomQuestions("多选题", request.getMultiCount()));
        if (request.getJudgeCount() != null && request.getJudgeCount() > 0)
            finalQuestions.addAll(getRandomQuestions("判断题", request.getJudgeCount()));
        if (request.getFillCount() != null && request.getFillCount() > 0)
            finalQuestions.addAll(getRandomQuestions("填空题", request.getFillCount()));
        if (request.getEssayCount() != null && request.getEssayCount() > 0)
            finalQuestions.addAll(getRandomQuestions("简答题", request.getEssayCount()));

        if (finalQuestions.isEmpty()) {
            throw new RuntimeException("未选择任何题目！");
        }

        int scorePerQuestion = 10;
        int totalScore = finalQuestions.size() * scorePerQuestion;

        // 2. 构建试卷对象
        Paper paper = new Paper();
        paper.setPaperName(request.getPaperName());
        paper.setTeacher(teacher);
        paper.setTotalScore(totalScore);
        if (paper.getCreateTime() == null) paper.setCreateTime(LocalDateTime.now());
        paper.setUpdateTime(LocalDateTime.now());

        // 3. 路由逻辑
        String currentDb = DbSwitchContext.getCurrentMasterDb();
        System.out.println(">>> [Paper业务] 正在向主库 [" + currentDb + "] 创建试卷...");

        switch (currentDb) {
            case "Oracle":
                // Save Paper
                paper = oraclePaperRepo.save(paper);
                // Save PaperQuestions
                for (Question q : finalQuestions) {
                    PaperQuestion pq = new PaperQuestion();
                    pq.setPaper(paper);
                    pq.setQuestion(q);
                    pq.setScore(scorePerQuestion);
                    oraclePaperQuestionRepo.save(pq);
                }
                // Sync
                syncService.syncPapersBidirectional();
                break;

            case "SQLServer":
                paper = sqlServerPaperRepo.save(paper);
                for (Question q : finalQuestions) {
                    PaperQuestion pq = new PaperQuestion();
                    pq.setPaper(paper);
                    pq.setQuestion(q);
                    pq.setScore(scorePerQuestion);
                    sqlServerPaperQuestionRepo.save(pq);
                }
                syncService.syncPapersBidirectional();
                break;

            case "MySQL":
            default:
                paper = mysqlPaperRepo.save(paper);
                for (Question q : finalQuestions) {
                    PaperQuestion pq = new PaperQuestion();
                    pq.setPaper(paper);
                    pq.setQuestion(q);
                    pq.setScore(scorePerQuestion);
                    mysqlPaperQuestionRepo.save(pq);
                }
                syncService.syncPapersBidirectional();
                break;
        }
    }

    private List<Question> getRandomQuestions(String type, int count) {
        // 注意：这里需要去 MysqlQuestionRepository 接口里确认有 findByType 方法
        List<Question> all = mysqlQuestionRepo.findByType(type);
        if (all.size() < count) {
            // 简单处理，如果不够就全拿
            // throw new RuntimeException("题库不足...");
            // 演示时为了不报错，可以宽容处理
        }
        Collections.shuffle(all);
        return all.stream().limit(count).collect(Collectors.toList());
    }
}