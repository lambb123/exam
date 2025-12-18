package com.exam.backend.controller;

import com.exam.backend.entity.User;
import com.exam.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*") // 允许前端跨域访问
public class UserController {

    @Autowired
    private UserService userService;

    // 登录接口
    // POST http://localhost:8081/api/user/login
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody User loginUser) {
        Map<String, Object> result = new HashMap<>();
        User user = userService.login(loginUser.getUsername(), loginUser.getPassword());

        if (user != null) {
            result.put("code", 200);
            result.put("msg", "登录成功");
            result.put("data", user);
        } else {
            result.put("code", 400);
            result.put("msg", "用户名或密码错误");
        }
        return result;
    }

    // 注册接口 (为了方便你自己造数据)
    // POST http://localhost:8081/api/user/register
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody User user) {
        Map<String, Object> result = new HashMap<>();
        try {
            User newUser = userService.register(user);
            result.put("code", 200);
            result.put("msg", "注册成功");
            result.put("data", newUser);
        } catch (Exception e) {
            result.put("code", 400);
            result.put("msg", e.getMessage());
        }
        return result;
    }
}
