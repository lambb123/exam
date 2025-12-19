package com.exam.backend.controller;

import com.exam.backend.repository.mysql.MysqlExamResultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/score")
@CrossOrigin(origins = "*")
public class ScoreController {

    @Autowired
    private MysqlExamResultRepository examResultRepo;





    @GetMapping("/list")
    public Map<String, Object> getAllScores(
            @RequestParam(required = false) String studentName,
            @RequestParam(required = false) String paperName
    ) {
        // 调用刚才写的 findAllWithAvg
        List<Map<String, Object>> list = examResultRepo.findAllWithAvg(studentName, paperName);
        return Map.of("code", 200, "data", list);
    }

    // 学霸筛选接口
    @GetMapping("/analysis/above-average")
    public Map<String, Object> getAboveAverage(
            @RequestParam(required = false) String studentName,
            @RequestParam(required = false) String paperName
    ) {
        List<Map<String, Object>> list = examResultRepo.findAboveAverageStudents(studentName, paperName);
        return Map.of("code", 200, "data", list);
    }
}