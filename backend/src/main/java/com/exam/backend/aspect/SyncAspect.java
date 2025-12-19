package com.exam.backend.aspect;

import com.exam.backend.service.SyncService;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class SyncAspect {

    @Autowired
    private SyncService syncService;

    // 定义切点：监听 Service 包下所有 save 或 update 开头的方法
    // 注意：你需要确保你的 Service 方法名符合规范，或者直接监听 Repository
    // 这里示例监听 UserService 和 PaperService 的保存方法
    @AfterReturning(pointcut = "execution(* com.exam.backend.service.*Service.save*(..)) || " +
            "execution(* com.exam.backend.service.*Service.update*(..))")
    @Async // 异步执行，不阻塞主业务线程
    public void triggerRealTimeSync() {
        System.out.println(">>> [实时同步触发] 检测到数据变更，立即执行增量/全量同步...");
        // 调用 SyncService 中的同步逻辑
        // 生产环境建议做细粒度同步 (syncSingleUser)，这里演示调用主同步方法
        syncService.syncData();
    }
}