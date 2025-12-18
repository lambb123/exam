package com.exam.backend.controller;

import com.exam.backend.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of("code", 200, "data", dashboardService.getStats());
    }

    @GetMapping("/db-status")
    public Map<String, Object> dbStatus() {
        return Map.of("code", 200, "data", dashboardService.getDbStatus());
    }
}
