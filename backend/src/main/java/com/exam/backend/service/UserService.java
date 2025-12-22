package com.exam.backend.service;

import com.exam.backend.common.DbSwitchContext;
import com.exam.backend.entity.User;
import com.exam.backend.repository.mysql.MysqlUserRepository;
import com.exam.backend.repository.oracle.OracleUserRepository;
import com.exam.backend.repository.sqlserver.SqlServerUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    @Autowired private MysqlUserRepository mysqlUserRepository;
    @Autowired private OracleUserRepository oracleUserRepository;
    @Autowired private SqlServerUserRepository sqlServerUserRepository;

    @Autowired private SyncService syncService; // 注入同步服务

    // === 现有方法 (读操作保持走 MySQL) ===
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

    // === 核心改造：注册 (支持主库切换) ===
    public User register(User user) {
        // 1. 检查唯一性 (读 MySQL 即可)
        if (mysqlUserRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new RuntimeException("用户名已存在");
        }

        // 2. 补全时间
        if (user.getCreateTime() == null) user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        // 3. 路由逻辑
        return saveUserInternal(user);
    }

    // === 核心改造：获取所有用户 ===
    public List<User> getAllUsers() {
        return mysqlUserRepository.findAll();
    }

    // === 核心改造：添加用户 (后台用) ===
    public User addUser(User user) {
        if (mysqlUserRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new RuntimeException("用户名 " + user.getUsername() + " 已存在");
        }
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            user.setPassword("123456");
        }
        if (user.getCreateTime() == null) user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        return saveUserInternal(user);
    }

    // === 核心改造：更新用户 ===
    public User updateUser(User user) {
        User existing = mysqlUserRepository.findById(user.getId()).orElse(null);
        if (existing == null) {
            throw new RuntimeException("用户不存在");
        }

        existing.setRealName(user.getRealName());
        existing.setRole(user.getRole());
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            existing.setPassword(user.getPassword());
        }
        existing.setUpdateTime(LocalDateTime.now());

        return saveUserInternal(existing);
    }

    // === 核心改造：删除用户 ===
    public void deleteUser(Long id) {
        // 旧代码：mysqlUserRepository.deleteById(id);

        // 新代码：调用三库同时删除
        syncService.deleteUserGlobally(id);
    }

    // === 内部私有方法：统一处理写入路由 ===
    private User saveUserInternal(User user) {
        String currentDb = DbSwitchContext.getCurrentMasterDb();
        User result = null;
        System.out.println(">>> [User业务] 正在向主库 [" + currentDb + "] 写入用户: " + user.getUsername());

        switch (currentDb) {
            case "Oracle":
                result = oracleUserRepository.save(user);
                syncService.syncUsersBidirectional(); // 立即同步
                break;
            case "SQLServer":
                result = sqlServerUserRepository.save(user);
                syncService.syncUsersBidirectional(); // 立即同步
                break;
            case "MySQL":
            default:
                result = mysqlUserRepository.save(user);
                break;
        }
        return result;
    }


}
