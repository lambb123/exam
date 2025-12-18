package com.exam.backend.service;

import com.exam.backend.entity.Question;
import com.exam.backend.repository.mysql.MysqlQuestionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.exam.backend.service.SyncService;

import java.util.List;

@Service
public class QuestionService {

    @Autowired
    private MysqlQuestionRepository questionRepository;


    @Autowired
    private SyncService syncService; // 注入同步服务

    // 获取所有试题
    public List<Question> findAll() {
        return questionRepository.findAll();
    }

    // 添加试题
    public Question add(Question question) {
        Question saved = questionRepository.save(question);

        // 2. 【修改点】调用 syncData() 而不是 syncQuestions()
        // 使用新线程异步执行，防止卡顿
        new Thread(() -> syncService.syncData()).start();

        return saved;
    }

    // 删除试题
    public void delete(Long id) {
        questionRepository.deleteById(id);

        // 3. 【修改点】同样调用 syncData()
        new Thread(() -> syncService.syncData()).start();
    }
}