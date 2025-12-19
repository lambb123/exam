package com.exam.backend.controller;

import com.exam.backend.repository.mysql.MysqlExamResultRepository;
import com.exam.backend.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;


    @Autowired private MysqlExamResultRepository mysqlExamResultRepo;

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of("code", 200, "data", dashboardService.getStats());
    }



    @GetMapping("/stats/papers")
    public Map<String, Object> getPaperStats() {
        List<Map<String, Object>> stats = mysqlExamResultRepo.findPaperStatistics();
        return Map.of("code", 200, "data", stats);
    }


}
