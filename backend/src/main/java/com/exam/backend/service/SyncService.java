package com.exam.backend.service;

import com.exam.backend.entity.*;
import com.exam.backend.repository.mysql.*;
import com.exam.backend.repository.oracle.*;
import com.exam.backend.repository.sqlserver.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value; // 引入 @Value
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;

@Service
@EnableScheduling
public class SyncService {

    // 1. 注入自己 (解决事务自调用失效问题)
    @Autowired
    @Lazy
    private SyncService self;

    // 2. 注入邮件发送器
    @Autowired(required = false)
    private JavaMailSender mailSender;


    @Autowired private MysqlSyncLogRepository syncLogRepo;


    // 3. 【核心修复】自动读取 application.yml 里的发件人邮箱
    @Value("${spring.mail.username}")
    private String fromEmail;

    // === MySQL 源 ===
    @Autowired private MysqlUserRepository mysqlUserRepo;
    @Autowired private MysqlQuestionRepository mysqlQuestionRepo;
    @Autowired private MysqlPaperRepository mysqlPaperRepo;
    @Autowired private MysqlPaperQuestionRepository mysqlPaperQuestionRepo;
    @Autowired private MysqlExamResultRepository mysqlExamResultRepo;

    // === 目标库 Repository ===
    @Autowired private OracleUserRepository oracleUserRepo;
    @Autowired private OracleQuestionRepository oracleQuestionRepo;
    @Autowired private OraclePaperRepository oraclePaperRepo;
    @Autowired private OraclePaperQuestionRepository oraclePaperQuestionRepo;
    @Autowired private OracleExamResultRepository oracleExamResultRepo;

    @Autowired private SqlServerUserRepository sqlServerUserRepo;
    @Autowired private SqlServerQuestionRepository sqlServerQuestionRepo;
    @Autowired private SqlServerPaperRepository sqlServerPaperRepo;
    @Autowired private SqlServerPaperQuestionRepository sqlServerPaperQuestionRepo;
    @Autowired private SqlServerExamResultRepository sqlServerExamResultRepo;

    // === EntityManager ===
    @PersistenceContext(unitName = "oraclePersistenceUnit")
    private EntityManager oracleEm;

    @PersistenceContext(unitName = "sqlServerPersistenceUnit")
    private EntityManager sqlServerEm;

    // === 定时任务入口 ===
    @Scheduled(cron = "0/5 * * * * ?")
    public void syncData() {
        System.out.println("【同步任务开始】...");

        // === 1. 开始记录日志 ===
        SyncLog log = new SyncLog();
        log.setStartTime(LocalDateTime.now());
        log.setStatus("RUNNING");
        log.setMessage("同步正在进行中...");
        // 保存并获取 ID，以便后续更新同一条记录
        log = syncLogRepo.save(log);







        try {
            // === 【测试开关】如果你想测试邮件，把下面这行注释解开 ===
             //if (true) throw new RuntimeException("演示：这是一封测试报警邮件！");

            // 1. 读取 MySQL 数据
            List<User> users = mysqlUserRepo.findAll();
            List<Question> questions = mysqlQuestionRepo.findAll();
            List<Paper> papers = mysqlPaperRepo.findAll();
            List<PaperQuestion> pqs = mysqlPaperQuestionRepo.findAll();
            List<ExamResult> results = mysqlExamResultRepo.findAll();

            // 2. 执行同步
            self.syncToOracle(users, questions, papers, pqs, results);
            self.syncToSqlServer(users, questions, papers, pqs, results);




            // === 2. 成功：更新日志 ===
            log.setEndTime(LocalDateTime.now());
            log.setStatus("SUCCESS");
            log.setMessage("同步成功。用户数: " + users.size());
            syncLogRepo.save(log);



            System.out.println("【同步任务结束】Success");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("【同步异常】" + e.getMessage());


            // === 3. 失败：更新日志 ===
            log.setEndTime(LocalDateTime.now());
            log.setStatus("FAILED");

            // 【关键】截取错误信息，防止超过数据库字段长度 (4000) 导致二次报错
            String fullError = e.getMessage() != null ? e.getMessage() : "Unknown Error";
            if (fullError.length() > 3900) {
                fullError = fullError.substring(0, 3900) + "...";
            }
            log.setMessage(fullError);

            syncLogRepo.save(log);






            // 3. 发送报警邮件
            sendAlertMail(e.getMessage());
        }
    }

    // === 邮件报警逻辑 ===
    private void sendAlertMail(String errorMsg) {
        if (mailSender == null) {
            System.err.println(">>> 警告：未配置邮件发送器，跳过报警。");
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();

            // 【核心修复】使用配置里的邮箱，保证和认证用户一致
            message.setFrom(fromEmail);

            // 收件人（填你自己另一个邮箱，或者管理员邮箱）
            message.setTo("2450956909@qq.com");

            message.setSubject("【严重报警】数据库同步服务异常");
            message.setText("系统在执行多库同步时发生严重错误，请立即检查！\n\n错误详情：\n" + errorMsg);

            mailSender.send(message);
            System.out.println(">>> 报警邮件已成功发送。");
        } catch (Exception ex) {
            System.err.println(">>> 报警邮件发送失败：" + ex.getMessage());
        }
    }

    // ==========================================
    // Oracle 同步 (原生 SQL)
    // ==========================================
    @Transactional(transactionManager = "transactionManagerOracle")
    public void syncToOracle(List<User> users, List<Question> questions, List<Paper> papers,
                             List<PaperQuestion> pqs, List<ExamResult> results) {
        oracleExamResultRepo.deleteAllInBatch();
        oraclePaperQuestionRepo.deleteAllInBatch();
        oraclePaperRepo.deleteAllInBatch();
        oracleQuestionRepo.deleteAllInBatch();
        oracleUserRepo.deleteAllInBatch();

        for (User u : users) {
            String sql = "INSERT INTO sys_user (id, username, password, role, real_name, create_time) VALUES (?1, ?2, ?3, ?4, ?5, ?6)";
            oracleEm.createNativeQuery(sql).setParameter(1, u.getId()).setParameter(2, u.getUsername())
                    .setParameter(3, u.getPassword()).setParameter(4, u.getRole())
                    .setParameter(5, u.getRealName()).setParameter(6, u.getCreateTime()).executeUpdate();
        }
        for (Question q : questions) {
            String sql = "INSERT INTO question (id, content, type, difficulty, knowledge_point, answer) VALUES (?1, ?2, ?3, ?4, ?5, ?6)";
            oracleEm.createNativeQuery(sql).setParameter(1, q.getId()).setParameter(2, q.getContent())
                    .setParameter(3, q.getType()).setParameter(4, q.getDifficulty())
                    .setParameter(5, q.getKnowledgePoint()).setParameter(6, q.getAnswer()).executeUpdate();
        }
        for (Paper p : papers) {
            Long teacherId = p.getTeacher() != null ? p.getTeacher().getId() : null;
            String sql = "INSERT INTO paper (id, paper_name, total_score, teacher_id, create_time) VALUES (?1, ?2, ?3, ?4, ?5)";
            oracleEm.createNativeQuery(sql).setParameter(1, p.getId()).setParameter(2, p.getPaperName())
                    .setParameter(3, p.getTotalScore()).setParameter(4, teacherId)
                    .setParameter(5, p.getCreateTime()).executeUpdate();
        }
        for (PaperQuestion pq : pqs) {
            Long pid = pq.getPaper() != null ? pq.getPaper().getId() : null;
            Long qid = pq.getQuestion() != null ? pq.getQuestion().getId() : null;
            String sql = "INSERT INTO paper_question (paper_id, question_id, score) VALUES (?1, ?2, ?3)";
            oracleEm.createNativeQuery(sql).setParameter(1, pid).setParameter(2, qid)
                    .setParameter(3, pq.getScore()).executeUpdate();
        }
        for (ExamResult r : results) {
            Long sid = r.getStudent() != null ? r.getStudent().getId() : null;
            Long pid = r.getPaper() != null ? r.getPaper().getId() : null;
            String sql = "INSERT INTO exam_result (id, student_id, paper_id, score, exam_time) VALUES (?1, ?2, ?3, ?4, ?5)";
            oracleEm.createNativeQuery(sql).setParameter(1, r.getId()).setParameter(2, sid)
                    .setParameter(3, pid).setParameter(4, r.getScore())
                    .setParameter(5, r.getExamTime()).executeUpdate();
        }
    }

    // ==========================================
    // SQL Server 同步 (JDBC doWork 模式)
    // ==========================================
    @Transactional(transactionManager = "transactionManagerSqlServer")
    public void syncToSqlServer(List<User> users, List<Question> questions, List<Paper> papers,
                                List<PaperQuestion> pqs, List<ExamResult> results) {
        sqlServerExamResultRepo.deleteAllInBatch();
        sqlServerPaperQuestionRepo.deleteAllInBatch();
        sqlServerPaperRepo.deleteAllInBatch();
        sqlServerQuestionRepo.deleteAllInBatch();
        sqlServerUserRepo.deleteAllInBatch();

        Session session = sqlServerEm.unwrap(Session.class);
        session.doWork(connection -> {
            if (!users.isEmpty()) {
                try (Statement stmt = connection.createStatement()) { stmt.execute("SET IDENTITY_INSERT dbo.sys_user ON"); }
                try (PreparedStatement ps = connection.prepareStatement("INSERT INTO dbo.sys_user (id, username, password, role, real_name, create_time) VALUES (?, ?, ?, ?, ?, ?)")) {
                    for (User u : users) {
                        ps.setLong(1, u.getId());
                        ps.setString(2, u.getUsername());
                        ps.setString(3, u.getPassword());
                        ps.setString(4, u.getRole());
                        ps.setString(5, u.getRealName());
                        ps.setTimestamp(6, u.getCreateTime() != null ? Timestamp.valueOf(u.getCreateTime()) : null);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                try (Statement stmt = connection.createStatement()) { stmt.execute("SET IDENTITY_INSERT dbo.sys_user OFF"); }
            }
            if (!questions.isEmpty()) {
                try (Statement stmt = connection.createStatement()) { stmt.execute("SET IDENTITY_INSERT dbo.question ON"); }
                try (PreparedStatement ps = connection.prepareStatement("INSERT INTO dbo.question (id, content, type, difficulty, knowledge_point, answer) VALUES (?, ?, ?, ?, ?, ?)")) {
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
                try (Statement stmt = connection.createStatement()) { stmt.execute("SET IDENTITY_INSERT dbo.question OFF"); }
            }
            if (!papers.isEmpty()) {
                try (Statement stmt = connection.createStatement()) { stmt.execute("SET IDENTITY_INSERT dbo.paper ON"); }
                try (PreparedStatement ps = connection.prepareStatement("INSERT INTO dbo.paper (id, paper_name, total_score, teacher_id, create_time) VALUES (?, ?, ?, ?, ?)")) {
                    for (Paper p : papers) {
                        ps.setLong(1, p.getId());
                        ps.setString(2, p.getPaperName());
                        ps.setObject(3, p.getTotalScore(), Types.INTEGER);
                        ps.setObject(4, p.getTeacher() != null ? p.getTeacher().getId() : null, Types.BIGINT);
                        ps.setTimestamp(5, p.getCreateTime() != null ? Timestamp.valueOf(p.getCreateTime()) : null);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                try (Statement stmt = connection.createStatement()) { stmt.execute("SET IDENTITY_INSERT dbo.paper OFF"); }
            }
            if (!pqs.isEmpty()) {
                try (PreparedStatement ps = connection.prepareStatement("INSERT INTO dbo.paper_question (paper_id, question_id, score) VALUES (?, ?, ?)")) {
                    for (PaperQuestion pq : pqs) {
                        ps.setObject(1, pq.getPaper() != null ? pq.getPaper().getId() : null, Types.BIGINT);
                        ps.setObject(2, pq.getQuestion() != null ? pq.getQuestion().getId() : null, Types.BIGINT);
                        ps.setObject(3, pq.getScore(), Types.INTEGER);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            if (!results.isEmpty()) {
                try (Statement stmt = connection.createStatement()) { stmt.execute("SET IDENTITY_INSERT dbo.exam_result ON"); }
                try (PreparedStatement ps = connection.prepareStatement("INSERT INTO dbo.exam_result (id, student_id, paper_id, score, exam_time) VALUES (?, ?, ?, ?, ?)")) {
                    for (ExamResult r : results) {
                        ps.setLong(1, r.getId());
                        ps.setObject(2, r.getStudent() != null ? r.getStudent().getId() : null, Types.BIGINT);
                        ps.setObject(3, r.getPaper() != null ? r.getPaper().getId() : null, Types.BIGINT);
                        ps.setBigDecimal(4, r.getScore());
                        ps.setTimestamp(5, r.getExamTime() != null ? Timestamp.valueOf(r.getExamTime()) : null);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                try (Statement stmt = connection.createStatement()) { stmt.execute("SET IDENTITY_INSERT dbo.exam_result OFF"); }
            }
        });
    }





    // ==========================================
    // 【新增】冲突处理：单条数据修复 (Oracle)
    // ==========================================

    /**
     * 修复 Oracle 单个用户数据
     */
    @Transactional(transactionManager = "transactionManagerOracle")
    public void syncSingleUserToOracle(Long userId) {
        // 1. 从主库获取最新数据
        User u = mysqlUserRepo.findById(userId).orElse(null);
        if (u == null) {
            // 如果主库也没了，理论上应该删除备库数据，这里简单处理为返回
            return;
        }

        // 2. 删除旧数据（防止主键冲突）
        oracleEm.createNativeQuery("DELETE FROM sys_user WHERE id = ?1").setParameter(1, userId).executeUpdate();

        // 3. 插入新数据
        String sql = "INSERT INTO sys_user (id, username, password, role, real_name, create_time) VALUES (?1, ?2, ?3, ?4, ?5, ?6)";
        oracleEm.createNativeQuery(sql)
                .setParameter(1, u.getId())
                .setParameter(2, u.getUsername())
                .setParameter(3, u.getPassword())
                .setParameter(4, u.getRole())
                .setParameter(5, u.getRealName())
                .setParameter(6, u.getCreateTime())
                .executeUpdate();
    }

    /**
     * 修复 Oracle 单个试卷数据
     */
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


    // ==========================================
    // 【新增】冲突处理：单条数据修复 (SQL Server)
    // 注意：SQL Server 插入带 ID 的记录需要开启 IDENTITY_INSERT
    // ==========================================

    /**
     * 修复 SQL Server 单个用户数据
     */
    @Transactional(transactionManager = "transactionManagerSqlServer")
    public void syncSingleUserToSqlServer(Long userId) {
        User u = mysqlUserRepo.findById(userId).orElse(null);
        if (u == null) return;

        Session session = sqlServerEm.unwrap(Session.class);
        session.doWork(connection -> {
            try (Statement stmt = connection.createStatement()) {
                // 1. 先删除旧数据
                try (PreparedStatement psDel = connection.prepareStatement("DELETE FROM dbo.sys_user WHERE id = ?")) {
                    psDel.setLong(1, userId);
                    psDel.executeUpdate();
                }

                // 2. 开启身份插入（允许手动指定 ID）
                stmt.execute("SET IDENTITY_INSERT dbo.sys_user ON");

                // 3. 插入新数据
                String insertSql = "INSERT INTO dbo.sys_user (id, username, password, role, real_name, create_time) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                    ps.setLong(1, u.getId());
                    ps.setString(2, u.getUsername());
                    ps.setString(3, u.getPassword());
                    ps.setString(4, u.getRole());
                    ps.setString(5, u.getRealName());
                    ps.setTimestamp(6, u.getCreateTime() != null ? Timestamp.valueOf(u.getCreateTime()) : null);
                    ps.executeUpdate();
                }

                // 4. 关闭身份插入
                stmt.execute("SET IDENTITY_INSERT dbo.sys_user OFF");
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("SQL Server 单条用户同步失败: " + e.getMessage());
            }
        });
    }

    /**
     * 修复 SQL Server 单个试卷数据
     */
    @Transactional(transactionManager = "transactionManagerSqlServer")
    public void syncSinglePaperToSqlServer(Long paperId) {
        Paper p = mysqlPaperRepo.findById(paperId).orElse(null);
        if (p == null) return;

        Session session = sqlServerEm.unwrap(Session.class);
        session.doWork(connection -> {
            try (Statement stmt = connection.createStatement()) {
                // 1. 删除旧数据
                try (PreparedStatement psDel = connection.prepareStatement("DELETE FROM dbo.paper WHERE id = ?")) {
                    psDel.setLong(1, paperId);
                    psDel.executeUpdate();
                }

                // 2. 开启身份插入
                stmt.execute("SET IDENTITY_INSERT dbo.paper ON");

                // 3. 插入新数据
                String insertSql = "INSERT INTO dbo.paper (id, paper_name, total_score, teacher_id, create_time) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                    ps.setLong(1, p.getId());
                    ps.setString(2, p.getPaperName());
                    ps.setObject(3, p.getTotalScore(), Types.INTEGER);
                    ps.setObject(4, p.getTeacher() != null ? p.getTeacher().getId() : null, Types.BIGINT);
                    ps.setTimestamp(5, p.getCreateTime() != null ? Timestamp.valueOf(p.getCreateTime()) : null);
                    ps.executeUpdate();
                }

                // 4. 关闭身份插入
                stmt.execute("SET IDENTITY_INSERT dbo.paper OFF");
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("SQL Server 单条试卷同步失败: " + e.getMessage());
            }
        });
    }
}
