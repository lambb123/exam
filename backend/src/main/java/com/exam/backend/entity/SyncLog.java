package com.exam.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_sync_log") // 明确指定数据库表名，防止和关键字冲突
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 明确指定数据库列名，确保 createTime -> create_time 的映射准确
    @Column(name = "start_time")
    private LocalDateTime startTime;


    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "status", length = 50)
    private String status; // SUCCESS, FAILED, RUNNING

    // 报错信息通常较长，建议设为 2000 或 4000，甚至可以用 @Lob
    @Column(name = "message", length = 4000)
    private String message;

    public SyncLog() {
        this.createTime = LocalDateTime.now();
    }


    // === Getters & Setters ===
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}