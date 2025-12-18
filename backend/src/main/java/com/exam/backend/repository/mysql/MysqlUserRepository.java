package com.exam.backend.repository.mysql;

import com.exam.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MysqlUserRepository extends JpaRepository<User, Long> {
    // 登录用：根据用户名查用户
    Optional<User> findByUsername(String username);
}