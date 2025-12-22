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


    @Autowired private SyncService syncService;



    // === 登录 (读操作保持走 MySQL) ===
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

    // === 注册 ===
    public User register(User user) {
        // 【修复】根据当前主库检查唯一性
        checkUsernameUnique(user.getUsername());

        // 补全时间
        if (user.getCreateTime() == null) user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        return saveUserInternal(user);
    }

    // === 获取所有用户 ===
    public List<User> getAllUsers() {
        return mysqlUserRepository.findAll();
    }

    // === 添加用户 (后台用) ===
    public User addUser(User user) {
        // 【修复】根据当前主库检查唯一性
        checkUsernameUnique(user.getUsername());

        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            user.setPassword("123456");
        }
        if (user.getCreateTime() == null) user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        return saveUserInternal(user);
    }

    // === 更新用户 ===
    public User updateUser(User user) {
        // 更新操作需要先找到原对象，建议先在 MySQL 找，如果找不到再去主库找，或者直接根据 ID 更新
        // 这里简化逻辑，先在 MySQL 找
        User existing = mysqlUserRepository.findById(user.getId()).orElse(null);
        if (existing == null) {
            throw new RuntimeException("用户不存在 (MySQL)");
        }

        existing.setRealName(user.getRealName());
        existing.setRole(user.getRole());
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            existing.setPassword(user.getPassword());
        }
        existing.setUpdateTime(LocalDateTime.now());

        return saveUserInternal(existing);
    }

    // === 删除用户 ===
    public void deleteUser(Long id) {
        syncService.deleteUserGlobally(id);
    }

    // ==========================================
    // 私有辅助方法
    // ==========================================

    /**
     * 【新增】根据当前主库模式，动态检查用户名是否已存在
     */
    private void checkUsernameUnique(String username) {
        String currentDb = DbSwitchContext.getCurrentMasterDb();
        boolean exists = false;

        switch (currentDb) {
            case "Oracle":
                exists = oracleUserRepository.findByUsername(username).isPresent();
                break;
            case "SQLServer":
                exists = sqlServerUserRepository.findByUsername(username).isPresent();
                break;
            case "MySQL":
            default:
                exists = mysqlUserRepository.findByUsername(username).isPresent();
                break;
        }

        if (exists) {
            throw new RuntimeException("用户名 " + username + " 已存在于 [" + currentDb + "] 中，无法重复添加！");
        }
    }

    /**
     * 统一写入路由
     */
    private User saveUserInternal(User user) {
        String currentDb = DbSwitchContext.getCurrentMasterDb();
        User result = null;
        System.out.println(">>> [User业务] 正在向主库 [" + currentDb + "] 写入用户: " + user.getUsername());

        switch (currentDb) {
            case "Oracle":
                result = oracleUserRepository.save(user);
                syncService.syncUsersBidirectional();
                break;
            case "SQLServer":
                result = sqlServerUserRepository.save(user);
                syncService.syncUsersBidirectional();
                break;
            case "MySQL":
            default:
                result = mysqlUserRepository.save(user);
                syncService.syncUsersBidirectional();
                break;
        }
        return result;
    }
}
