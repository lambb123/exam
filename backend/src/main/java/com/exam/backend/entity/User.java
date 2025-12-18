package com.exam.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    private String role; // ADMIN, TEACHER, STUDENT


    @Column(name = "real_name")
    private String realName;


    @Column(name = "create_time")
    private LocalDateTime createTime;

    public User() {
        this.createTime = LocalDateTime.now();
    }
}