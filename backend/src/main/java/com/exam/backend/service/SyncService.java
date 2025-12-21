package com.exam.backend.service;

import com.exam.backend.entity.*;
import com.exam.backend.repository.mysql.*;
import com.exam.backend.repository.oracle.*;
import com.exam.backend.repository.sqlserver.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@EnableScheduling
public class SyncService {

    @Autowired @Lazy private SyncService self; // 解决事务自调用

    @Autowired private MysqlSyncLogRepository syncLogRepo;

    // === 邮件组件 ===
    @Autowired(required = false) private JavaMailSender mailSender;
    @Value("${spring.mail.username}") private String fromEmail;

    // === MySQL Repositories ===
    @Autowired private MysqlUserRepository mysqlUserRepo;
    @Autowired private MysqlQuestionRepository mysqlQuestionRepo;
    @Autowired private MysqlPaperRepository mysqlPaperRepo;
    @Autowired private MysqlPaperQuestionRepository mysqlPaperQuestionRepo;
    @Autowired private MysqlExamResultRepository mysqlExamResultRepo;

    // === Oracle Repositories ===
    @Autowired private OracleUserRepository oracleUserRepo;
    @Autowired private OracleQuestionRepository oracleQuestionRepo;
    @Autowired private OraclePaperRepository oraclePaperRepo;
    @Autowired private OraclePaperQuestionRepository oraclePaperQuestionRepo;
    @Autowired private OracleExamResultRepository oracleExamResultRepo;

    // === SQL Server Repositories ===
    @Autowired private SqlServerUserRepository sqlServerUserRepo;
    @Autowired private SqlServerQuestionRepository sqlServerQuestionRepo;
    @Autowired private SqlServerPaperRepository sqlServerPaperRepo;
    @Autowired private SqlServerPaperQuestionRepository sqlServerPaperQuestionRepo;
    @Autowired private SqlServerExamResultRepository sqlServerExamResultRepo;

    // === Entity Managers (用于原生 SQL) ===
    @PersistenceContext(unitName = "oraclePersistenceUnit")
    private EntityManager oracleEm;

    @PersistenceContext(unitName = "sqlServerPersistenceUnit")
    private EntityManager sqlServerEm;


    // =========================================================
    // 1. [入口] 定时检查 (兜底机制) - 每 30 秒执行
    // =========================================================
    @Scheduled(cron = "0/30 * * * * ?")
    public void scheduledCheck() {
        // 记录日志到数据库
        SyncLog log = new SyncLog();
        log.setStartTime(LocalDateTime.now());
        log.setStatus("RUNNING");
        log.setMessage("定时同步检查开始...");
        log = syncLogRepo.save(log);

        try {
            System.out.println(">>> [定时检查] 开始全量数据一致性扫描...");

            // 1. 执行 User 表的双向同步
            self.syncUsersBidirectional();

            // 2. 执行其他表的单向同步
            self.syncOthersLegacy();

            // 更新数据库日志状态
            log.setEndTime(LocalDateTime.now());
            log.setStatus("SUCCESS");
            log.setMessage("同步检查完成");
            syncLogRepo.save(log);

            // 【新增】控制台打印完成提示
            System.out.println(">>> [定时检查] 同步检查完成 (SUCCESS)");

        } catch (Exception e) {
            e.printStackTrace();
            log.setEndTime(LocalDateTime.now());
            log.setStatus("FAILED");
            String error = e.getMessage() != null ? e.getMessage() : "Unknown Error";
            log.setMessage(error.length() > 3900 ? error.substring(0, 3900) : error);
            syncLogRepo.save(log);

            // 打印失败提示
            System.err.println(">>> [定时检查] 同步失败: " + e.getMessage());
        }
    }

    // =========================================================
    // 2. [入口] 实时同步 (AOP 调用)
    // =========================================================
    @Async
    public void executeImmediateSync() {
        System.out.println(">>> [实时同步] 响应业务操作，立即执行增量同步...");
        try {
            // 重点同步用户表 (响应注册/修改)
            self.syncUsersBidirectional();

            // 如果业务涉及试卷修改，也可以在这里调用 syncOthersLegacy()
            // 但为了性能，通常只同步核心变动表
        } catch (Exception e) {
            System.err.println("实时同步执行异常: " + e.getMessage());
            // 异常不阻断主流程，留给定时任务修复
        }
    }

    @Async
    public void syncData() {
        // 直接复用新的实时同步逻辑
        this.executeImmediateSync();
    }

    // =========================================================
    // 3. [核心] User 表双向同步逻辑 (Last Write Wins)
    // =========================================================
    @Transactional
    public void syncUsersBidirectional() {
        // 1. 获取全量数据
        List<User> mysqlList = mysqlUserRepo.findAll();
        List<User> oracleList = oracleUserRepo.findAll();
        List<User> sqlList = sqlServerUserRepo.findAll();

        // 2. 聚合所有出现的 ID
        Set<Long> allIds = new HashSet<>();
        mysqlList.forEach(u -> allIds.add(u.getId()));
        oracleList.forEach(u -> allIds.add(u.getId()));
        sqlList.forEach(u -> allIds.add(u.getId()));

        // 3. 转 Map 方便查找
        Map<Long, User> mHelper = mysqlList.stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, User> oHelper = oracleList.stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, User> sHelper = sqlList.stream().collect(Collectors.toMap(User::getId, Function.identity()));

        // 4. 逐个 ID 处理
        for (Long id : allIds) {
            User m = mHelper.get(id);
            User o = oHelper.get(id);
            User s = sHelper.get(id);

            // 4.1 决出胜者 (时间最新的那个)
            User winner = determineWinner(m, o, s);
            if (winner == null) continue;

            // 4.2 同步给所有旧的/缺失的库

            // -> Sync to MySQL
            if (m == null || !m.equals(winner)) {
                mysqlUserRepo.save(winner);
            }

            // -> Sync to Oracle
            if (o == null || !o.equals(winner)) {
                // 【修改点 1】加上 self. 避免事务失效
                self.syncToOracleNative(winner);
            }

            // -> Sync to SQL Server
            if (s == null || !s.equals(winner)) {
                // 【修改点 2】加上 self. 避免事务失效
                self.syncToSqlServerNative(winner);
            }
        }
    }

    /**
     * 辅助逻辑：比较三个 User 对象，返回 updateTime 最大的那个
     */
    private User determineWinner(User... users) {
        User winner = null;
        for (User u : users) {
            if (u == null) continue;
            if (winner == null) {
                winner = u;
            } else {
                // 如果 u 的时间比 winner 晚，u 获胜
                // 注意判空 (如果 updateTime 为空，视为最旧)
                if (u.getUpdateTime() != null &&
                        (winner.getUpdateTime() == null || u.getUpdateTime().isAfter(winner.getUpdateTime()))) {
                    winner = u;
                }
            }
        }
        return winner;
    }

    // =========================================================
    // 4. [兼容] 其他表的单向同步 (保留原逻辑)
    // =========================================================
    @Transactional
    public void syncOthersLegacy() {
        // 读取 MySQL (作为主源)
        List<Question> questions = mysqlQuestionRepo.findAll();
        List<Paper> papers = mysqlPaperRepo.findAll();
        List<PaperQuestion> pqs = mysqlPaperQuestionRepo.findAll();
        List<ExamResult> results = mysqlExamResultRepo.findAll();

        // 【关键修改】必须用 self. 调用，否则 @Transactional(Oracle) 不生效！
        self.syncToOracleLegacy(questions, papers, pqs, results);

        // 【关键修改】必须用 self. 调用
        self.syncToSqlServerLegacy(questions, papers, pqs, results);
    }

    // --- Oracle 单向同步实现 (Legacy) ---
    @Transactional(transactionManager = "transactionManagerOracle")
    public void syncToOracleLegacy(List<Question> questions, List<Paper> papers,
                                   List<PaperQuestion> pqs, List<ExamResult> results) {
        // 先清空
        oracleExamResultRepo.deleteAllInBatch();
        oraclePaperQuestionRepo.deleteAllInBatch();
        oraclePaperRepo.deleteAllInBatch();
        oracleQuestionRepo.deleteAllInBatch();
        // (User 表已经在 bidirectional 中处理了，这里不删)

        // 插入 Question
        for (Question q : questions) {
            String sql = "INSERT INTO question (id, content, type, difficulty, knowledge_point, answer) VALUES (?1, ?2, ?3, ?4, ?5, ?6)";
            oracleEm.createNativeQuery(sql).setParameter(1, q.getId()).setParameter(2, q.getContent())
                    .setParameter(3, q.getType()).setParameter(4, q.getDifficulty())
                    .setParameter(5, q.getKnowledgePoint()).setParameter(6, q.getAnswer()).executeUpdate();
        }
        // 插入 Paper
        for (Paper p : papers) {
            Long teacherId = p.getTeacher() != null ? p.getTeacher().getId() : null;
            String sql = "INSERT INTO paper (id, paper_name, total_score, teacher_id, create_time) VALUES (?1, ?2, ?3, ?4, ?5)";
            oracleEm.createNativeQuery(sql).setParameter(1, p.getId()).setParameter(2, p.getPaperName())
                    .setParameter(3, p.getTotalScore()).setParameter(4, teacherId)
                    .setParameter(5, p.getCreateTime()).executeUpdate();
        }
        // 插入 PaperQuestion
        for (PaperQuestion pq : pqs) {
            Long pid = pq.getPaper() != null ? pq.getPaper().getId() : null;
            Long qid = pq.getQuestion() != null ? pq.getQuestion().getId() : null;
            String sql = "INSERT INTO paper_question (paper_id, question_id, score) VALUES (?1, ?2, ?3)";
            oracleEm.createNativeQuery(sql).setParameter(1, pid).setParameter(2, qid)
                    .setParameter(3, pq.getScore()).executeUpdate();
        }
        // 插入 ExamResult
        for (ExamResult r : results) {
            Long sid = r.getStudent() != null ? r.getStudent().getId() : null;
            Long pid = r.getPaper() != null ? r.getPaper().getId() : null;
            String sql = "INSERT INTO exam_result (id, student_id, paper_id, score, exam_time) VALUES (?1, ?2, ?3, ?4, ?5)";
            oracleEm.createNativeQuery(sql).setParameter(1, r.getId()).setParameter(2, sid)
                    .setParameter(3, pid).setParameter(4, r.getScore())
                    .setParameter(5, r.getCreateTime()).executeUpdate();
        }
    }

    // --- SQL Server 单向同步实现 (Legacy) ---
    // =========================================================
    // 【最终清爽版】SQL Server 同步 (移除进度刷屏，仅保留错误提示)
    // =========================================================
    public void syncToSqlServerLegacy(List<Question> questions, List<Paper> papers,
                                      List<PaperQuestion> pqs, List<ExamResult> results) {

        // 1. 清理旧数据 (只保留错误日志，不打印进度)
        try { try { sqlServerExamResultRepo.deleteAllInBatch(); } catch (Exception e) { System.err.println("SQL Server 清空成绩失败(非致命): " + e.getMessage()); } } catch (Exception ignore) {}
        try { try { sqlServerPaperQuestionRepo.deleteAllInBatch(); } catch (Exception e) { System.err.println("SQL Server 清空组卷失败(非致命): " + e.getMessage()); } } catch (Exception ignore) {}
        try { try { sqlServerPaperRepo.deleteAllInBatch(); } catch (Exception e) { System.err.println("SQL Server 清空试卷失败(非致命): " + e.getMessage()); } } catch (Exception ignore) {}
        try { try { sqlServerQuestionRepo.deleteAllInBatch(); } catch (Exception e) { System.err.println("SQL Server 清空题目失败(非致命): " + e.getMessage()); } } catch (Exception ignore) {}

        // 2. 批量插入
        org.hibernate.Session session = sqlServerEm.unwrap(org.hibernate.Session.class);
        session.doWork(connection -> {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);

                try (java.sql.Statement stmt = connection.createStatement()) {

                    // --- 同步 Question ---
                    if (!questions.isEmpty()) {
                        try {
                            stmt.execute("SET IDENTITY_INSERT dbo.question ON");
                            String sql = "INSERT INTO dbo.question (id, content, type, difficulty, knowledge_point, answer) VALUES (?, ?, ?, ?, ?, ?)";
                            try (java.sql.PreparedStatement ps = connection.prepareStatement(sql)) {
                                for (Question q : questions) {
                                    ps.setLong(1, q.getId());
                                    ps.setString(2, q.getContent());
                                    ps.setString(3, q.getType());
                                    ps.setString(4, q.getDifficulty());
                                    ps.setString(5, q.getKnowledgePoint());
                                    ps.setString(6, q.getAnswer());
                                    ps.addBatch();
                                }
                                ps.executeBatch();
                            }
                        } catch (Exception e) {
                            System.err.println("SQL Server [题目] 同步出错: " + e.getMessage());
                        } finally {
                            try { stmt.execute("SET IDENTITY_INSERT dbo.question OFF"); } catch (Exception ignore) {}
                        }
                    }

                    // --- 同步 Paper ---
                    if (!papers.isEmpty()) {
                        try {
                            stmt.execute("SET IDENTITY_INSERT dbo.paper ON");
                            String sql = "INSERT INTO dbo.paper (id, paper_name, total_score, teacher_id, create_time) VALUES (?, ?, ?, ?, ?)";
                            try (java.sql.PreparedStatement ps = connection.prepareStatement(sql)) {
                                for (Paper p : papers) {
                                    ps.setLong(1, p.getId());
                                    ps.setString(2, p.getPaperName());
                                    ps.setObject(3, p.getTotalScore(), java.sql.Types.INTEGER);
                                    ps.setObject(4, p.getTeacher() != null ? p.getTeacher().getId() : null, java.sql.Types.BIGINT);
                                    ps.setTimestamp(5, p.getCreateTime() != null ? Timestamp.valueOf(p.getCreateTime()) : null);
                                    ps.addBatch();
                                }
                                ps.executeBatch();
                            }
                        } catch (Exception e) {
                            System.err.println("SQL Server [试卷] 同步出错: " + e.getMessage());
                        } finally {
                            try { stmt.execute("SET IDENTITY_INSERT dbo.paper OFF"); } catch (Exception ignore) {}
                        }
                    }

                    // --- 同步 PaperQuestion ---
                    if (!pqs.isEmpty()) {
                        try {
                            String sql = "INSERT INTO dbo.paper_question (paper_id, question_id, score) VALUES (?, ?, ?)";
                            try (java.sql.PreparedStatement ps = connection.prepareStatement(sql)) {
                                for (PaperQuestion pq : pqs) {
                                    ps.setObject(1, pq.getPaper() != null ? pq.getPaper().getId() : null, java.sql.Types.BIGINT);
                                    ps.setObject(2, pq.getQuestion() != null ? pq.getQuestion().getId() : null, java.sql.Types.BIGINT);
                                    ps.setObject(3, pq.getScore(), java.sql.Types.INTEGER);
                                    ps.addBatch();
                                }
                                ps.executeBatch();
                            }
                        } catch (Exception e) {
                            System.err.println("SQL Server [组卷记录] 同步出错: " + e.getMessage());
                        }
                    }

                    // --- 同步 ExamResult ---
                    if (!results.isEmpty()) {
                        try {
                            stmt.execute("SET IDENTITY_INSERT dbo.exam_result ON");
                            String sql = "INSERT INTO dbo.exam_result (id, student_id, paper_id, score, exam_time) VALUES (?, ?, ?, ?, ?)";
                            try (java.sql.PreparedStatement ps = connection.prepareStatement(sql)) {
                                for (ExamResult r : results) {
                                    ps.setLong(1, r.getId());
                                    ps.setObject(2, r.getStudent() != null ? r.getStudent().getId() : null, java.sql.Types.BIGINT);
                                    ps.setObject(3, r.getPaper() != null ? r.getPaper().getId() : null, java.sql.Types.BIGINT);
                                    ps.setBigDecimal(4, r.getScore());
                                    ps.setTimestamp(5, r.getCreateTime() != null ? Timestamp.valueOf(r.getCreateTime()) : null);
                                    ps.addBatch();
                                }
                                ps.executeBatch();
                            }
                        } catch (Exception e) {
                            System.err.println("SQL Server [成绩] 同步出错: " + e.getMessage());
                        } finally {
                            try { stmt.execute("SET IDENTITY_INSERT dbo.exam_result OFF"); } catch (Exception ignore) {}
                        }
                    }

                    // 提交事务
                    connection.commit();

                } catch (Exception e) {
                    System.err.println(">>> [SQL Server] 严重错误，回滚事务: " + e.getMessage());
                    connection.rollback();
                }
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        });
    }

    // =========================================================
    // 5. [辅助] 针对 User 的单条写入/更新 (Native SQL)
    // =========================================================

    @Transactional(transactionManager = "transactionManagerOracle")
    public void syncToOracleNative(User u) {
        // 1. 尝试 UPDATE
        String updateSql = "UPDATE sys_user SET username=?1, password=?2, role=?3, real_name=?4, create_time=?5, update_time=?6 WHERE id=?7";
        int rows = oracleEm.createNativeQuery(updateSql)
                .setParameter(1, u.getUsername())
                .setParameter(2, u.getPassword())
                .setParameter(3, u.getRole())
                .setParameter(4, u.getRealName())
                .setParameter(5, u.getCreateTime())
                .setParameter(6, u.getUpdateTime())
                .setParameter(7, u.getId())
                .executeUpdate();

        // 2. 如果没有更新任何行，说明不存在，执行 INSERT
        if (rows == 0) {
            String insertSql = "INSERT INTO sys_user (id, username, password, role, real_name, create_time, update_time) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)";
            oracleEm.createNativeQuery(insertSql)
                    .setParameter(1, u.getId())
                    .setParameter(2, u.getUsername())
                    .setParameter(3, u.getPassword())
                    .setParameter(4, u.getRole())
                    .setParameter(5, u.getRealName())
                    .setParameter(6, u.getCreateTime())
                    .setParameter(7, u.getUpdateTime())
                    .executeUpdate();
        }
    }

    @Transactional(transactionManager = "transactionManagerSqlServer")
    public void syncToSqlServerNative(User u) {
        org.hibernate.Session session = sqlServerEm.unwrap(org.hibernate.Session.class);
        session.doWork(connection -> {
            try {
                // 1. 尝试 UPDATE
                String updateSql = "UPDATE dbo.sys_user SET username=?, password=?, role=?, real_name=?, create_time=?, update_time=? WHERE id=?";
                try (java.sql.PreparedStatement ps = connection.prepareStatement(updateSql)) {
                    ps.setString(1, u.getUsername());
                    ps.setString(2, u.getPassword());
                    ps.setString(3, u.getRole());
                    ps.setString(4, u.getRealName());
                    ps.setTimestamp(5, u.getCreateTime() != null ? Timestamp.valueOf(u.getCreateTime()) : null);
                    ps.setTimestamp(6, u.getUpdateTime() != null ? Timestamp.valueOf(u.getUpdateTime()) : null);
                    ps.setLong(7, u.getId());

                    int rows = ps.executeUpdate();

                    // 如果更新了至少 1 行，说明数据存在且已同步，直接返回
                    if (rows > 0) {
                        return;
                    }
                }

                // 2. 如果 UPDATE 没命中 (rows == 0)，说明是新数据，执行 INSERT
                try (java.sql.Statement stmt = connection.createStatement()) {
                    // 开启身份插入 (允许手动指定 ID)
                    stmt.execute("SET IDENTITY_INSERT dbo.sys_user ON");

                    String insertSql = "INSERT INTO dbo.sys_user (id, username, password, role, real_name, create_time, update_time) VALUES (?, ?, ?, ?, ?, ?, ?)";
                    try (java.sql.PreparedStatement ps = connection.prepareStatement(insertSql)) {
                        ps.setLong(1, u.getId());
                        ps.setString(2, u.getUsername());
                        ps.setString(3, u.getPassword());
                        ps.setString(4, u.getRole());
                        ps.setString(5, u.getRealName());
                        ps.setTimestamp(6, u.getCreateTime() != null ? Timestamp.valueOf(u.getCreateTime()) : null);
                        ps.setTimestamp(7, u.getUpdateTime() != null ? Timestamp.valueOf(u.getUpdateTime()) : null);
                        ps.executeUpdate();
                    }

                    // 关闭身份插入
                    stmt.execute("SET IDENTITY_INSERT dbo.sys_user OFF");
                }
            } catch (Exception e) {
                e.printStackTrace();
                // 抛出异常以便外层捕获并记录日志
                throw new RuntimeException("SQL Server 用户同步失败: " + e.getMessage());
            }
        });
    }

    // =========================================================
    // 6. [辅助] 邮件报警
    // =========================================================
    private void sendAlertMail(String errorMsg) {
        if (mailSender == null) return;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo("2450956909@qq.com"); // 管理员邮箱
            message.setSubject("【严重报警】数据库同步服务异常");
            message.setText("同步服务检测到错误，请检查：\n" + errorMsg);
            mailSender.send(message);
        } catch (Exception ex) {
            System.err.println("报警邮件发送失败: " + ex.getMessage());
        }
    }


    // =========================================================
    // 7. [补全] 试卷单条修复 (供 ConflictController 调用)
    // =========================================================

    @Transactional(transactionManager = "transactionManagerOracle")
    public void syncSinglePaperToOracle(Long paperId) {
        Paper p = mysqlPaperRepo.findById(paperId).orElse(null);
        if (p == null) return;

        // 删除旧数据
        oracleEm.createNativeQuery("DELETE FROM paper WHERE id = ?1").setParameter(1, paperId).executeUpdate();

        // 插入新数据
        Long teacherId = p.getTeacher() != null ? p.getTeacher().getId() : null;
        String sql = "INSERT INTO paper (id, paper_name, total_score, teacher_id, create_time) VALUES (?1, ?2, ?3, ?4, ?5)";
        oracleEm.createNativeQuery(sql)
                .setParameter(1, p.getId())
                .setParameter(2, p.getPaperName())
                .setParameter(3, p.getTotalScore())
                .setParameter(4, teacherId)
                .setParameter(5, p.getCreateTime())
                .executeUpdate();
    }

    @Transactional(transactionManager = "transactionManagerSqlServer")
    public void syncSinglePaperToSqlServer(Long paperId) {
        Paper p = mysqlPaperRepo.findById(paperId).orElse(null);
        if (p == null) return;

        org.hibernate.Session session = sqlServerEm.unwrap(org.hibernate.Session.class);
        session.doWork(connection -> {
            try (java.sql.Statement stmt = connection.createStatement()) {
                // 1. 删除
                try (java.sql.PreparedStatement psDel = connection.prepareStatement("DELETE FROM dbo.paper WHERE id = ?")) {
                    psDel.setLong(1, paperId);
                    psDel.executeUpdate();
                }
                // 2. 插入
                stmt.execute("SET IDENTITY_INSERT dbo.paper ON");
                String insertSql = "INSERT INTO dbo.paper (id, paper_name, total_score, teacher_id, create_time) VALUES (?, ?, ?, ?, ?)";
                try (java.sql.PreparedStatement ps = connection.prepareStatement(insertSql)) {
                    ps.setLong(1, p.getId());
                    ps.setString(2, p.getPaperName());
                    ps.setObject(3, p.getTotalScore(), java.sql.Types.INTEGER);
                    ps.setObject(4, p.getTeacher() != null ? p.getTeacher().getId() : null, java.sql.Types.BIGINT);
                    ps.setTimestamp(5, p.getCreateTime() != null ? Timestamp.valueOf(p.getCreateTime()) : null);
                    ps.executeUpdate();
                }
                stmt.execute("SET IDENTITY_INSERT dbo.paper OFF");
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("SQL Server 试卷同步失败: " + e.getMessage());
            }
        });
    }
}
