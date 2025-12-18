package com.exam.backend.controller;

import com.exam.backend.entity.Question;
import com.exam.backend.service.QuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/question")
@CrossOrigin(origins = "*")
public class QuestionController {

    @Autowired
    private QuestionService questionService;

    @GetMapping("/list")
    public Map<String, Object> list() {
        List<Question> list = questionService.findAll();
        Map<String, Object> map = new HashMap<>();
        map.put("code", 200);
        map.put("data", list);
        return map;
    }

    @PostMapping("/add")
    public Map<String, Object> add(@RequestBody Question question) {
        questionService.add(question);
        Map<String, Object> map = new HashMap<>();
        map.put("code", 200);
        map.put("msg", "添加成功");
        return map;
    }

    @DeleteMapping("/delete/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        questionService.delete(id);
        Map<String, Object> map = new HashMap<>();
        map.put("code", 200);
        map.put("msg", "删除成功");
        return map;
    }
}
