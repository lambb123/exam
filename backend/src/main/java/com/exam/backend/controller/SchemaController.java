package com.exam.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schema")
@CrossOrigin(origins = "*")
public class SchemaController {

    private final JdbcTemplate jdbcTemplate;

    // 注入 MySQL 的数据源 (注意：这里假设你的 MysqlConfig 中 Bean 名字是 mysqlDataSource)
    // 如果报错找不到 Bean，请检查 MysqlConfig.java 中的 Bean 名称
    public SchemaController(@Qualifier("mysqlDataSource") DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    // 1. 获取当前数据库的所有表名
    @GetMapping("/tables")
    public Map<String, Object> getTables() {
        // 查询当前库的非系统视图表
        String sql = "SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA = (SELECT DATABASE()) AND TABLE_TYPE='BASE TABLE'";
        List<Map<String, Object>> tables = jdbcTemplate.queryForList(sql);
        return Map.of("code", 200, "data", tables);
    }

    // 2. 获取指定表的字段结构
    @GetMapping("/columns")
    public Map<String, Object> getColumns(@RequestParam String tableName) {
        String sql = """
            SELECT 
                COLUMN_NAME as field, 
                COLUMN_TYPE as type, 
                IS_NULLABLE as 'nullable', 
                COLUMN_KEY as 'key', 
                COLUMN_COMMENT as comment,
                COLUMN_DEFAULT as 'defaultVal'
            FROM information_schema.COLUMNS 
            WHERE TABLE_SCHEMA = (SELECT DATABASE()) 
            AND TABLE_NAME = ?
            ORDER BY ORDINAL_POSITION
        """;
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql, tableName);
        return Map.of("code", 200, "data", columns);
    }
}
