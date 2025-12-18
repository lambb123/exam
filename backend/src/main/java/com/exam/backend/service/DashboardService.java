package com.exam.backend.service;

import com.exam.backend.repository.mysql.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class DashboardService {

    @Autowired
    private MysqlUserRepository userRepository;
    @Autowired
    private MysqlQuestionRepository questionRepository;
    @Autowired
    private MysqlPaperRepository paperRepository;
    @Autowired
    private MysqlExamResultRepository examResultRepository;

    public Map<String, Long> getStats() {
        Map<String, Long> map = new HashMap<>();
        // JPA 自带的 count() 方法，直接查总数
        map.put("userCount", userRepository.count());
        map.put("questionCount", questionRepository.count());
        map.put("paperCount", paperRepository.count());
        map.put("examCount", examResultRepository.count());
        return map;
    }
}
