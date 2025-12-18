package com.exam.backend.controller;

import com.exam.backend.entity.User;
import com.exam.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserService userService;

    // === 现有的登录注册接口 (保持不变) ===
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

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody User user) {
        return handleResult(() -> userService.register(user), "注册成功");
    }

    // === 新增管理接口 ===

    // 1. 获取用户列表
    @GetMapping("/list")
    public Map<String, Object> getUserList() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<User> list = userService.getAllUsers();
            result.put("code", 200);
            result.put("data", list);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", e.getMessage());
        }
        return result;
    }

    // 2. 添加用户
    @PostMapping("/add")
    public Map<String, Object> addUser(@RequestBody User user) {
        return handleResult(() -> userService.addUser(user), "添加成功");
    }

    // 3. 更新用户
    @PostMapping("/update") // 或者用 PUT
    public Map<String, Object> updateUser(@RequestBody User user) {
        return handleResult(() -> userService.updateUser(user), "更新成功");
    }

    // 4. 删除用户
    @DeleteMapping("/delete/{id}")
    public Map<String, Object> deleteUser(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            userService.deleteUser(id);
            result.put("code", 200);
            result.put("msg", "删除成功");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "删除失败：" + e.getMessage());
        }
        return result;
    }

    // 辅助方法：统一处理返回格式
    private interface Action { Object run(); }
    private Map<String, Object> handleResult(Action action, String successMsg) {
        Map<String, Object> result = new HashMap<>();
        try {
            Object data = action.run();
            result.put("code", 200);
            result.put("msg", successMsg);
            result.put("data", data);
        } catch (Exception e) {
            result.put("code", 400);
            result.put("msg", e.getMessage());
        }
        return result;
    }
}
