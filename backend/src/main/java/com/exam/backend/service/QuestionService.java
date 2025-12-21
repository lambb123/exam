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


    //@Autowired
    //private SyncService syncService; // 注入同步服务

    // 获取所有试题
    public List<Question> findAll() {
        return questionRepository.findAll();
    }

    // 添加试题
    public Question add(Question question) {
        // 这里的保存操作会被 AOP (SyncAspect) 自动捕获并触发同步
        return questionRepository.save(question);
    }

    // 删除试题
    public void delete(Long id) {
        // 这里的删除操作也会被 AOP 捕获 (前提是切面配置了 delete*)
        questionRepository.deleteById(id);
    }
}