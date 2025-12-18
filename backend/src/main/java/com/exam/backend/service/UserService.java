package com.exam.backend.service;

import com.exam.backend.entity.User;
import com.exam.backend.repository.mysql.MysqlUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private MysqlUserRepository mysqlUserRepository;

    /**
     * 用户登录
     */
    public User login(String username, String password) {
        Optional<User> userOpt = mysqlUserRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getPassword().equals(password)) {
                return user; // 登录成功
            }
        }
        return null; // 登录失败
    }

    /**
     * 用户注册（初始化用）
     */
    public User register(User user) {
        // 简单判断用户名是否存在
        if (mysqlUserRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new RuntimeException("用户名已存在");
        }
        return mysqlUserRepository.save(user);
    }
}
