package com.exam.backend.controller;

import com.exam.backend.entity.Question;
import com.exam.backend.repository.mysql.MysqlQuestionRepository;
import com.exam.backend.service.QuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/question")
@CrossOrigin(origins = "*")
public class QuestionController {

    @Autowired
    private QuestionService questionService;

    @Autowired
    private MysqlQuestionRepository mysqlQuestionRepo;



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

    // 高级检索接口
    @GetMapping("/advanced-search")
    public Map<String, Object> advancedSearch(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String content
    ) {
        // 调用刚才写的复杂 SQL
        List<Map<String, Object>> list = mysqlQuestionRepo.findComplexQuestions(type, content);

        return Map.of(
                "code", 200,
                "msg", "success",
                "data", list,
                "desc", "执行了包含 [多表连接 + 嵌套子查询 + 动态条件] 的复杂 SQL"
        );
    }

    @GetMapping("/search")
    public Map<String, Object> searchQuestions(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String knowledgePoint
    ) {
        List<Question> list = mysqlQuestionRepo.searchByCondition(type, difficulty, knowledgePoint);
        return Map.of("code", 200, "msg", "success", "data", list);
    }
}
