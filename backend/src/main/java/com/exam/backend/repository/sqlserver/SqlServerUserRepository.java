package com.exam.backend.repository.sqlserver;

import com.exam.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SqlServerUserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
}