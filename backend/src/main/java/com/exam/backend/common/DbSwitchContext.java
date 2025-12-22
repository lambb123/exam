package com.exam.backend.common;

import org.springframework.stereotype.Component;

/**
 * 全局数据库路由上下文
 * 用于记录当前系统的“主写入库”是哪个
 */
@Component
public class DbSwitchContext {
    // 默认主库为 MySQL
    private static String currentMasterDb = "MySQL";

    public static String getCurrentMasterDb() {
        return currentMasterDb;
    }

    public static void setCurrentMasterDb(String dbType) {
        // 允许的值: MySQL, Oracle, SQLServer
        currentMasterDb = dbType;
        System.out.println(">>> [系统通知] 主写入库已切换为: " + dbType);
    }
}