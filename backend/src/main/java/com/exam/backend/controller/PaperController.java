package com.exam.backend.controller;

import com.exam.backend.entity.Paper;
import com.exam.backend.service.PaperService;
import com.exam.backend.controller.dto.PaperCreateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/paper")
@CrossOrigin(origins = "*")
public class PaperController {

    @Autowired
    private PaperService paperService;

    // 获取试卷列表
    @GetMapping("/list")
    public Map<String, Object> list() {
        List<Paper> list = paperService.findAll();
        Map<String, Object> map = new HashMap<>();
        map.put("code", 200);
        map.put("data", list);
        return map;
    }

    // 【新增】获取试卷详情
    @GetMapping("/{id}")
    public Map<String, Object> getDetail(@PathVariable Long id) {
        Map<String, Object> map = new HashMap<>();
        try {
            Map<String, Object> detail = paperService.getPaperDetail(id);
            map.put("code", 200);
            map.put("data", detail);
        } catch (Exception e) {
            map.put("code", 400);
            map.put("msg", e.getMessage());
        }
        return map;
    }

    // 智能组卷
    @PostMapping("/create")
    public Map<String, Object> create(@RequestBody PaperCreateRequest request) {
        Map<String, Object> map = new HashMap<>();
        try {
            paperService.createPaper(request);
            map.put("code", 200);
            map.put("msg", "组卷成功");
        } catch (Exception e) {
            map.put("code", 400);
            map.put("msg", e.getMessage());
        }
        return map;
    }
}