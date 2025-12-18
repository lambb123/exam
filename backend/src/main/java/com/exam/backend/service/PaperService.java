package com.exam.backend.service;

import com.exam.backend.controller.dto.PaperCreateRequest;
import com.exam.backend.entity.*;
import com.exam.backend.repository.mysql.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
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

    // 获取所有试卷列表
    public List<Paper> findAll() {
        return paperRepository.findAll();
    }

    /**
     * 智能组卷核心逻辑
     */
    @Transactional(rollbackFor = Exception.class) // 开启事务，任何异常都回滚
    public void createPaper(PaperCreateRequest request) {
        // 1. 获取出卷老师信息
        User teacher = userRepository.findById(request.getTeacherId())
                .orElseThrow(() -> new RuntimeException("教师不存在"));

        // 2. 获取题库所有题目
        List<Question> allQuestions = questionRepository.findAll();

        // 简单校验：如果题库不够，直接报错
        if (allQuestions.size() < request.getQuestionCount()) {
            throw new RuntimeException("题库题目不足，无法组卷！当前只有 " + allQuestions.size() + " 道题");
        }

        // 3. 【核心算法】随机打乱，取前 N 个
        Collections.shuffle(allQuestions);
        List<Question> selectedQuestions = allQuestions.stream()
                .limit(request.getQuestionCount())
                .collect(Collectors.toList());

        // 4. 设定每道题的分数 (简单起见，假设每题 10 分)
        int scorePerQuestion = 10;
        int totalScore = selectedQuestions.size() * scorePerQuestion;

        // 5. 保存试卷主体
        Paper paper = new Paper();
        paper.setPaperName(request.getPaperName());
        paper.setTeacher(teacher);
        paper.setTotalScore(totalScore);
        paper = paperRepository.save(paper); // 保存后会生成 paper.id

        // 6. 保存试卷和题目的关联关系 (PaperQuestion)
        for (Question q : selectedQuestions) {
            PaperQuestion pq = new PaperQuestion();
            pq.setPaper(paper);
            pq.setQuestion(q);
            pq.setScore(scorePerQuestion);
            paperQuestionRepository.save(pq);
        }
    }
}