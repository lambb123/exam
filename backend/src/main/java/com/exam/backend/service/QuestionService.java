package com.exam.backend.service;

import com.exam.backend.entity.Question;
import com.exam.backend.repository.mysql.MysqlQuestionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QuestionService {

    @Autowired
    private MysqlQuestionRepository questionRepository;

    // 获取所有试题
    public List<Question> findAll() {
        return questionRepository.findAll();
    }

    // 添加试题
    public Question add(Question question) {
        return questionRepository.save(question);
    }

    // 删除试题
    public void delete(Long id) {
        questionRepository.deleteById(id);
    }
}