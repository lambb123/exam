package com.exam.backend.service;

import com.exam.backend.controller.dto.PaperCreateRequest;
import com.exam.backend.entity.*;
import com.exam.backend.repository.mysql.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PaperService {

    @Autowired
    private MysqlPaperRepository paperRepository;

    @Autowired
    private MysqlQuestionRepository questionRepository;

    @Autowired
    private MysqlPaperQuestionRepository paperQuestionRepository;

    @Autowired
    private MysqlUserRepository userRepository;

    /**
     * 获取所有试卷列表
     */
    public List<Paper> findAll() {
        return paperRepository.findAll();
    }

    /**
     * 获取试卷详情（包含题目列表）
     * 用于前端点击“查看详情”或“开始考试”
     */
    public Map<String, Object> getPaperDetail(Long paperId) {
        // 1. 查试卷基本信息
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new RuntimeException("试卷不存在"));

        // 2. 查该试卷关联的所有题目关系
        List<PaperQuestion> pqs = paperQuestionRepository.findByPaperId(paperId);

        // 3. 组装题目详细信息
        List<Map<String, Object>> questionList = pqs.stream().map(pq -> {
            Question q = pq.getQuestion();
            Map<String, Object> item = new HashMap<>();
            item.put("question", q); // 包含题目id, content, type, answer等所有字段
            item.put("score", pq.getScore());  // 该题在试卷中的分值
            return item;
        }).collect(Collectors.toList());

        // 4. 返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("paperInfo", paper);
        result.put("questionList", questionList);
        return result;
    }

    /**
     * 智能组卷核心逻辑 (升级版：按题型分类抽取)
     */
    @Transactional(rollbackFor = Exception.class)
    public void createPaper(PaperCreateRequest request) {
        User teacher = userRepository.findById(request.getTeacherId())
                .orElseThrow(() -> new RuntimeException("教师不存在"));

        // 定义要抽取的题目列表
        List<Question> finalQuestions = new ArrayList<>();

        // 1. 抽取单选题
        if (request.getSingleCount() != null && request.getSingleCount() > 0) {
            finalQuestions.addAll(getRandomQuestions("单选", request.getSingleCount()));
        }
        // 2. 抽取多选题
        if (request.getMultiCount() != null && request.getMultiCount() > 0) {
            finalQuestions.addAll(getRandomQuestions("多选", request.getMultiCount()));
        }
        // 3. 抽取判断题
        if (request.getJudgeCount() != null && request.getJudgeCount() > 0) {
            finalQuestions.addAll(getRandomQuestions("判断", request.getJudgeCount()));
        }
        // 4. 抽取填空题
        if (request.getFillCount() != null && request.getFillCount() > 0) {
            finalQuestions.addAll(getRandomQuestions("填空", request.getFillCount()));
        }
        // 5. 抽取简答题
        if (request.getEssayCount() != null && request.getEssayCount() > 0) {
            finalQuestions.addAll(getRandomQuestions("简答", request.getEssayCount()));
        }

        if (finalQuestions.isEmpty()) {
            throw new RuntimeException("未选择任何题目，请至少输入一种题型的数量！");
        }

        // 设定分值 (简单策略：所有题统一10分)
        int scorePerQuestion = 10;
        int totalScore = finalQuestions.size() * scorePerQuestion;

        // 保存试卷主体
        Paper paper = new Paper();
        paper.setPaperName(request.getPaperName());
        paper.setTeacher(teacher);
        paper.setTotalScore(totalScore);
        paper = paperRepository.save(paper);

        // 保存试卷与题目的关联
        for (Question q : finalQuestions) {
            PaperQuestion pq = new PaperQuestion();
            pq.setPaper(paper);
            pq.setQuestion(q);
            pq.setScore(scorePerQuestion);
            paperQuestionRepository.save(pq);
        }
    }

    /**
     * 辅助方法：从题库随机抽取指定类型的题目
     */
    private List<Question> getRandomQuestions(String type, int count) {
        // 这里使用了 MysqlQuestionRepository 中新增的 findByType 方法
        // 如果你还没加，记得去 Repository 接口里加一下 List<Question> findByType(String type);
        List<Question> all = questionRepository.findByType(type);

        if (all.size() < count) {
            throw new RuntimeException("【" + type + "】题库不足！需要 " + count + " 道，当前仅有 " + all.size() + " 道。");
        }

        // 随机打乱
        Collections.shuffle(all);

        // 取前 count 个
        return all.stream().limit(count).collect(Collectors.toList());
    }
}