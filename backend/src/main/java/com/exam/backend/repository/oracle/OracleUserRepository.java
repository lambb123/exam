package com.exam.backend.repository.oracle;

import com.exam.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface OracleUserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
}