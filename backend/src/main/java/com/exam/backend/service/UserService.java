package com.exam.backend.service;

import com.exam.backend.entity.User;
import com.exam.backend.repository.mysql.MysqlUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private MysqlUserRepository mysqlUserRepository;

    // === 现有方法 ===
    public User login(String username, String password) {
        Optional<User> userOpt = mysqlUserRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getPassword().equals(password)) {
                return user;
            }
        }
        return null;
    }

    public User register(User user) {
        if (mysqlUserRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new RuntimeException("用户名已存在");
        }
        return mysqlUserRepository.save(user);
    }

    // === 新增管理方法 ===

    /**
     * 获取所有用户列表
     */
    public List<User> getAllUsers() {
        // 实际项目中通常会做分页，这里演示方便直接返回所有
        return mysqlUserRepository.findAll();
    }

    /**
     * 添加用户（后台管理用）
     */
    public User addUser(User user) {
        if (mysqlUserRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new RuntimeException("用户名 " + user.getUsername() + " 已存在");
        }
        // 如果没有设置默认密码，可以设置一个
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            user.setPassword("123456"); // 默认密码
        }
        return mysqlUserRepository.save(user);
    }

    /**
     * 更新用户
     */
    public User updateUser(User user) {
        // 检查用户是否存在
        User existing = mysqlUserRepository.findById(user.getId()).orElse(null);
        if (existing == null) {
            throw new RuntimeException("用户不存在");
        }

        // 更新字段 (用户名通常不允许修改，或者需要校验唯一性，这里简单处理只更新其他字段)
        existing.setRealName(user.getRealName());
        existing.setRole(user.getRole());

        // 如果传了密码且不为空，则修改密码
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            existing.setPassword(user.getPassword());
        }

        return mysqlUserRepository.save(existing);
    }

    /**
     * 删除用户
     */
    public void deleteUser(Long id) {
        mysqlUserRepository.deleteById(id);
    }
}
