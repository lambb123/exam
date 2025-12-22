package com.exam.backend.service;

import com.exam.backend.common.DbSwitchContext;
import com.exam.backend.controller.dto.PaperCreateRequest;
import com.exam.backend.entity.*;
import com.exam.backend.repository.mysql.*;
import com.exam.backend.repository.oracle.*;
import com.exam.backend.repository.sqlserver.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    @Autowired private OracleQuestionRepository oracleQuestionRepo;
    @Autowired private OracleUserRepository oracleUserRepo;

    // === SQL Server Repos ===
    @Autowired private SqlServerPaperRepository sqlServerPaperRepo;
    @Autowired private SqlServerPaperQuestionRepository sqlServerPaperQuestionRepo;
    @Autowired private SqlServerQuestionRepository sqlServerQuestionRepo;
    @Autowired private SqlServerUserRepository sqlServerUserRepo;

    @Autowired private SyncService syncService;

    public List<Paper> findAll() {
        return mysqlPaperRepo.findAll();
    }

    public void delete(Long id) {
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
     * 智能组卷核心逻辑
     */
    public void createPaper(PaperCreateRequest request) {
        // 1. 获取当前主库
        String currentDb = DbSwitchContext.getCurrentMasterDb();
        System.out.println(">>> [Paper业务] 正在向主库 [" + currentDb + "] 创建试卷...");

        // 2. 动态获取教师信息
        User teacher = getTeacherFromCurrentDb(currentDb, request.getTeacherId());

        // 3. 动态抽取题目
        // 【修复】关键点：这里必须用 "单选" 而不是 "单选题"，因为数据库存的是两个字！
        List<Question> finalQuestions = new ArrayList<>();
        if (request.getSingleCount() != null && request.getSingleCount() > 0)
            finalQuestions.addAll(getRandomQuestions(currentDb, "单选", request.getSingleCount()));

        if (request.getMultiCount() != null && request.getMultiCount() > 0)
            finalQuestions.addAll(getRandomQuestions(currentDb, "多选", request.getMultiCount()));

        if (request.getJudgeCount() != null && request.getJudgeCount() > 0)
            finalQuestions.addAll(getRandomQuestions(currentDb, "判断", request.getJudgeCount()));

        if (request.getFillCount() != null && request.getFillCount() > 0)
            finalQuestions.addAll(getRandomQuestions(currentDb, "填空", request.getFillCount()));

        if (request.getEssayCount() != null && request.getEssayCount() > 0)
            finalQuestions.addAll(getRandomQuestions(currentDb, "简答", request.getEssayCount()));

        if (finalQuestions.isEmpty()) {
            throw new RuntimeException("【" + currentDb + "】库中未找到符合条件的题目，请检查题库或减少抽题数量！");
        }

        int scorePerQuestion = 10;
        int totalScore = finalQuestions.size() * scorePerQuestion;

        // 4. 构建试卷对象
        Paper paper = new Paper();
        paper.setPaperName(request.getPaperName());
        paper.setTeacher(teacher);
        paper.setTotalScore(totalScore);
        if (paper.getCreateTime() == null) paper.setCreateTime(LocalDateTime.now());
        paper.setUpdateTime(LocalDateTime.now());

        // 5. 路由写入逻辑
        switch (currentDb) {
            case "Oracle":
                paper = oraclePaperRepo.save(paper);
                for (Question q : finalQuestions) {
                    PaperQuestion pq = new PaperQuestion();
                    pq.setPaper(paper);
                    pq.setQuestion(q);
                    pq.setScore(scorePerQuestion);
                    oraclePaperQuestionRepo.save(pq);
                }
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

    private User getTeacherFromCurrentDb(String currentDb, Long teacherId) {
        Optional<User> userOpt;
        switch (currentDb) {
            case "Oracle": userOpt = oracleUserRepo.findById(teacherId); break;
            case "SQLServer": userOpt = sqlServerUserRepo.findById(teacherId); break;
            case "MySQL": default: userOpt = mysqlUserRepo.findById(teacherId); break;
        }
        return userOpt.orElseThrow(() -> new RuntimeException("在 [" + currentDb + "] 中未找到 ID=" + teacherId + " 的教师信息"));
    }

    private List<Question> getRandomQuestions(String currentDb, String type, int count) {
        List<Question> all = new ArrayList<>();

        if ("Oracle".equals(currentDb)) {
            all = oracleQuestionRepo.findAll().stream()
                    .filter(q -> type.equals(q.getType()))
                    .collect(Collectors.toList());
        } else if ("SQLServer".equals(currentDb)) {
            all = sqlServerQuestionRepo.findAll().stream()
                    .filter(q -> type.equals(q.getType()))
                    .collect(Collectors.toList());
        } else {
            all = mysqlQuestionRepo.findByType(type);
        }

        if (all.size() < count) {
            System.out.println(">>> 警告: [" + currentDb + "] " + type + " 题库不足，请求 " + count + " 实际 " + all.size());
        }

        Collections.shuffle(all);
        return all.stream().limit(count).collect(Collectors.toList());
    }
}