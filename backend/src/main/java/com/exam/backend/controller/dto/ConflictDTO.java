package com.exam.backend.controller.dto;

import java.time.LocalDateTime;

public class ConflictDTO {
    private Long id;              // 临时的列表ID (前端key)
    private String tableName;     // 表名
    private String conflictType;  // DUPLICATE_KEY, DATA_MISMATCH, MISSING
    private String description;   // 描述
    private String sourceId;      // 对应表的主键ID
    private LocalDateTime createTime; // 发现时间

    public ConflictDTO(Long id, String tableName, String conflictType, String description, String sourceId) {
        this.id = id;
        this.tableName = tableName;
        this.conflictType = conflictType;
        this.description = description;
        this.sourceId = sourceId;
        this.createTime = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getConflictType() { return conflictType; }
    public void setConflictType(String conflictType) { this.conflictType = conflictType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
