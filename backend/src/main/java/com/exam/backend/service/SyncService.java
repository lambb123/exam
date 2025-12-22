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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@EnableScheduling
public class SyncService {

    @Autowired @Lazy private SyncService self; // 解决事务自调用失效问题

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
        log.setMessage("全量双向同步检查开始...");
        log = syncLogRepo.save(log);

        try {
            System.out.println(">>> [定时检查] 开始全量数据一致性扫描...");

            // 依次执行所有业务表的双向同步
            self.syncUsersBidirectional();
            self.syncQuestionsBidirectional();
            self.syncPapersBidirectional();     // 包含 PaperQuestion 级联同步
            self.syncExamResultsBidirectional();

            // 更新数据库日志状态
            log.setEndTime(LocalDateTime.now());
            log.setStatus("SUCCESS");
            log.setMessage("同步检查完成");
            syncLogRepo.save(log);

            // 控制台打印完成提示
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
            // 发送报警邮件 (可选，防止阻塞暂时注释)
            sendAlertMail(e.getMessage());
        }
    }

    // =========================================================
    // 2. [入口] 实时同步 (AOP 调用)
    // =========================================================
    @Async
    public void executeImmediateSync() {
        System.out.println(">>> [实时同步] 响应业务操作，立即执行增量同步...");
        try {
            // 实时同步所有核心业务数据
            self.syncUsersBidirectional();
            self.syncQuestionsBidirectional();
            self.syncPapersBidirectional();
            self.syncExamResultsBidirectional();
        } catch (Exception e) {
            System.err.println("实时同步执行异常: " + e.getMessage());
            // 异常不阻断主流程，留给定时任务修复
        }
    }

    // 兼容旧代码调用
    @Async
    public void syncData() {
        // 直接复用新的实时同步逻辑
        this.executeImmediateSync();
    }

    // =========================================================
    // 3. 通用辅助方法
    // =========================================================
    /**
     * "谁新谁赢" 核心算法：比较3个对象，返回 updateTime 最晚的那个
     */
    private <T> T determineWinner(T m, T o, T s, Function<T, LocalDateTime> timeExtractor) {
        T winner = null;
        LocalDateTime maxTime = null;

        List<T> items = Arrays.asList(m, o, s);
        for (T item : items) {
            if (item == null) continue;
            LocalDateTime t = timeExtractor.apply(item);

            // 如果 winner 还没定，或者当前 item 时间比 maxTime 晚，则更新 winner
            if (winner == null) {
                winner = item;
                maxTime = t;
            } else {
                // 注意处理 time 为 null 的情况 (视为最旧)
                if (t != null && (maxTime == null || t.isAfter(maxTime))) {
                    winner = item;
                    maxTime = t;
                }
            }
        }
        return winner;
    }

    // =========================================================
    // 4. [核心] User 表双向同步 (已修复唯一键冲突)
    // =========================================================
    @Transactional
    public void syncUsersBidirectional() {
        List<User> mList = mysqlUserRepo.findAll();
        List<User> oList = oracleUserRepo.findAll();
        List<User> sList = sqlServerUserRepo.findAll();

        Set<Long> ids = new HashSet<>();
        mList.forEach(u -> ids.add(u.getId()));
        oList.forEach(u -> ids.add(u.getId()));
        sList.forEach(u -> ids.add(u.getId()));

        Map<Long, User> mM = mList.stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, User> oM = oList.stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, User> sM = sList.stream().collect(Collectors.toMap(User::getId, Function.identity()));

        for (Long id : ids) {
            User m = mM.get(id);
            User o = oM.get(id);
            User s = sM.get(id);

            // 1. 决出胜者 (数据最新的那个)
            User winner = determineWinner(m, o, s, User::getUpdateTime);
            if (winner == null) continue;

            // ---------------------------------------------------------
            // Sync to MySQL
            // ---------------------------------------------------------
            if (m == null || !m.equals(winner)) {
                try {


                    // 【修改后】 使用原生 SQL，强制 ID 一致
                    self.syncToMysqlNative(winner);

                } catch (Exception e) {
                    System.err.println("MySQL User同步忽略错误: " + e.getMessage());
                }
            }

            // ---------------------------------------------------------
            // Sync to Oracle
            // ---------------------------------------------------------
            if (o == null || !o.equals(winner)) {
                try {
                    User target = copyUser(winner); // 复制对象
                    // 查重
                    Optional<User> exist = oracleUserRepo.findByUsername(target.getUsername());
                    if (exist.isPresent() && !exist.get().getId().equals(target.getId())) {
                        target.setId(exist.get().getId());
                    }
                    // 调用原生 Native 方法同步
                    self.syncToOracleNative(target);
                } catch (Exception e) {
                    System.err.println("Oracle User同步忽略错误: " + e.getMessage());
                }
            }

            // ---------------------------------------------------------
            // Sync to SQL Server
            // ---------------------------------------------------------
            if (s == null || !s.equals(winner)) {
                try {
                    User target = copyUser(winner); // 复制对象
                    // 查重
                    Optional<User> exist = sqlServerUserRepo.findByUsername(target.getUsername());
                    if (exist.isPresent() && !exist.get().getId().equals(target.getId())) {
                        target.setId(exist.get().getId());
                    }
                    // 调用原生 Native 方法同步
                    self.syncToSqlServerNative(target);
                } catch (Exception e) {
                    System.err.println("SQL Server User同步忽略错误: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 辅助方法：手动浅拷贝 User 对象
     * 防止在修改 ID 时影响到 winner 原对象，导致后续逻辑错乱
     */
    private User copyUser(User source) {
        User target = new User();
        target.setId(source.getId());
        target.setUsername(source.getUsername());
        target.setPassword(source.getPassword());
        target.setRole(source.getRole());
        target.setRealName(source.getRealName());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        return target;
    }

    // =========================================================
    // 5. [核心] Question 表双向同步
    // =========================================================
    @Transactional
    public void syncQuestionsBidirectional() {
        List<Question> mList = mysqlQuestionRepo.findAll();
        List<Question> oList = oracleQuestionRepo.findAll();
        List<Question> sList = sqlServerQuestionRepo.findAll();

        Set<Long> ids = new HashSet<>();
        mList.forEach(x -> ids.add(x.getId()));
        oList.forEach(x -> ids.add(x.getId()));
        sList.forEach(x -> ids.add(x.getId()));

        Map<Long, Question> mM = mList.stream().collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<Long, Question> oM = oList.stream().collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<Long, Question> sM = sList.stream().collect(Collectors.toMap(Question::getId, Function.identity()));

        for (Long id : ids) {
            Question m = mM.get(id);
            Question o = oM.get(id);
            Question s = sM.get(id);

            Question winner = determineWinner(m, o, s, Question::getUpdateTime);
            if (winner == null) continue;

            if (m == null || !m.equals(winner)) {
                self.syncToMysqlQuestion(winner);
            }
            if (o == null || !o.equals(winner)) {
                self.syncToOracleQuestion(winner);
            }
            if (s == null || !s.equals(winner)) {
                self.syncToSqlServerQuestion(winner);
            }
        }
    }

    // =========================================================
    // 6. [核心] Paper 表双向同步 (含 PaperQuestion 级联)
    // =========================================================
    @Transactional
    public void syncPapersBidirectional() {
        List<Paper> mList = mysqlPaperRepo.findAll();
        List<Paper> oList = oraclePaperRepo.findAll();
        List<Paper> sList = sqlServerPaperRepo.findAll();

        Set<Long> ids = new HashSet<>();
        mList.forEach(x -> ids.add(x.getId()));
        oList.forEach(x -> ids.add(x.getId()));
        sList.forEach(x -> ids.add(x.getId()));

        Map<Long, Paper> mM = mList.stream().collect(Collectors.toMap(Paper::getId, Function.identity()));
        Map<Long, Paper> oM = oList.stream().collect(Collectors.toMap(Paper::getId, Function.identity()));
        Map<Long, Paper> sM = sList.stream().collect(Collectors.toMap(Paper::getId, Function.identity()));

        for (Long id : ids) {
            Paper m = mM.get(id);
            Paper o = oM.get(id);
            Paper s = sM.get(id);

            Paper winner = determineWinner(m, o, s, Paper::getUpdateTime);
            if (winner == null) continue;

            // 获取 Winner 的组卷详情 (级联同步的关键)
            List<PaperQuestion> winnerPQs = fetchPaperQuestions(winner, m, o, s);

            if (m == null || !m.equals(winner)) {
                mysqlPaperRepo.save(winner);
                syncPaperQuestionsToMysql(winner, winnerPQs);
            }
            if (o == null || !o.equals(winner)) {
                self.syncToOraclePaper(winner);
                self.syncToOraclePaperQuestions(winner, winnerPQs);
            }
            if (s == null || !s.equals(winner)) {
                self.syncToSqlServerPaper(winner);
                self.syncToSqlServerPaperQuestions(winner, winnerPQs);
            }
        }
    }

    /**
     * 辅助：从获胜方数据库查出该试卷的题目列表
     */
    private List<PaperQuestion> fetchPaperQuestions(Paper winner, Paper m, Paper o, Paper s) {
        // 判断胜者来自哪个库，就去哪个库查子表
        if (winner == m) {
            return mysqlPaperQuestionRepo.findAll().stream()
                    .filter(pq -> pq.getPaper() != null && pq.getPaper().getId().equals(winner.getId()))
                    .collect(Collectors.toList());
        }
        if (winner == o) {
            return oraclePaperQuestionRepo.findAll().stream()
                    .filter(pq -> pq.getPaper() != null && pq.getPaper().getId().equals(winner.getId()))
                    .collect(Collectors.toList());
        }
        if (winner == s) {
            return sqlServerPaperQuestionRepo.findAll().stream()
                    .filter(pq -> pq.getPaper() != null && pq.getPaper().getId().equals(winner.getId()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    // =========================================================
    // 7. [核心] ExamResult 成绩双向同步
    // =========================================================
    @Transactional
    public void syncExamResultsBidirectional() {
        List<ExamResult> mList = mysqlExamResultRepo.findAll();
        List<ExamResult> oList = oracleExamResultRepo.findAll();
        List<ExamResult> sList = sqlServerExamResultRepo.findAll();

        Set<Long> ids = new HashSet<>();
        mList.forEach(x -> ids.add(x.getId()));
        oList.forEach(x -> ids.add(x.getId()));
        sList.forEach(x -> ids.add(x.getId()));

        Map<Long, ExamResult> mM = mList.stream().collect(Collectors.toMap(ExamResult::getId, Function.identity()));
        Map<Long, ExamResult> oM = oList.stream().collect(Collectors.toMap(ExamResult::getId, Function.identity()));
        Map<Long, ExamResult> sM = sList.stream().collect(Collectors.toMap(ExamResult::getId, Function.identity()));

        for (Long id : ids) {
            ExamResult m = mM.get(id);
            ExamResult o = oM.get(id);
            ExamResult s = sM.get(id);

            ExamResult winner = determineWinner(m, o, s, ExamResult::getUpdateTime);
            if (winner == null) continue;

            if (m == null || !m.equals(winner)) {
                self.syncToMysqlResult(winner);
            }
            if (o == null || !o.equals(winner)) {
                self.syncToOracleResult(winner);
            }
            if (s == null || !s.equals(winner)) {
                self.syncToSqlServerResult(winner);
            }
        }
    }

    // =========================================================
    // 8. Native Writes (Oracle) - Upsert 实现
    // =========================================================

    @Transactional(transactionManager = "transactionManagerOracle")
    public void syncToOracleNative(User u) {
        String updateSql = "UPDATE sys_user SET username=?1, password=?2, role=?3, real_name=?4, create_time=?5, update_time=?6 WHERE id=?7";
        int rows = oracleEm.createNativeQuery(updateSql)
                .setParameter(1, u.getUsername()).setParameter(2, u.getPassword())
                .setParameter(3, u.getRole()).setParameter(4, u.getRealName())
                .setParameter(5, u.getCreateTime()).setParameter(6, u.getUpdateTime())
                .setParameter(7, u.getId()).executeUpdate();

        if (rows == 0) {
            String insertSql = "INSERT INTO sys_user (id, username, password, role, real_name, create_time, update_time) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)";
            oracleEm.createNativeQuery(insertSql)
                    .setParameter(1, u.getId()).setParameter(2, u.getUsername())
                    .setParameter(3, u.getPassword()).setParameter(4, u.getRole())
                    .setParameter(5, u.getRealName()).setParameter(6, u.getCreateTime())
                    .setParameter(7, u.getUpdateTime()).executeUpdate();
        }
    }

    @Transactional(transactionManager = "transactionManagerOracle")
    public void syncToOracleQuestion(Question q) {
        String update = "UPDATE question SET content=?1, type=?2, difficulty=?3, knowledge_point=?4, answer=?5, update_time=?6 WHERE id=?7";
        int rows = oracleEm.createNativeQuery(update)
                .setParameter(1, q.getContent()).setParameter(2, q.getType())
                .setParameter(3, q.getDifficulty()).setParameter(4, q.getKnowledgePoint())
                .setParameter(5, q.getAnswer()).setParameter(6, q.getUpdateTime())
                .setParameter(7, q.getId()).executeUpdate();
        if (rows == 0) {
            String insert = "INSERT INTO question (id, content, type, difficulty, knowledge_point, answer, update_time) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)";
            oracleEm.createNativeQuery(insert)
                    .setParameter(1, q.getId()).setParameter(2, q.getContent())
                    .setParameter(3, q.getType()).setParameter(4, q.getDifficulty())
                    .setParameter(5, q.getKnowledgePoint()).setParameter(6, q.getAnswer())
                    .setParameter(7, q.getUpdateTime()).executeUpdate();
        }
    }

    @Transactional(transactionManager = "transactionManagerOracle")
    public void syncToOraclePaper(Paper p) {
        String update = "UPDATE paper SET paper_name=?1, total_score=?2, teacher_id=?3, create_time=?4, update_time=?5 WHERE id=?6";
        int rows = oracleEm.createNativeQuery(update)
                .setParameter(1, p.getPaperName()).setParameter(2, p.getTotalScore())
                .setParameter(3, p.getTeacher() != null ? p.getTeacher().getId() : null)
                .setParameter(4, p.getCreateTime()).setParameter(5, p.getUpdateTime())
                .setParameter(6, p.getId()).executeUpdate();
        if (rows == 0) {
            String insert = "INSERT INTO paper (id, paper_name, total_score, teacher_id, create_time, update_time) VALUES (?1, ?2, ?3, ?4, ?5, ?6)";
            oracleEm.createNativeQuery(insert)
                    .setParameter(1, p.getId()).setParameter(2, p.getPaperName())
                    .setParameter(3, p.getTotalScore())
                    .setParameter(4, p.getTeacher() != null ? p.getTeacher().getId() : null)
                    .setParameter(5, p.getCreateTime()).setParameter(6, p.getUpdateTime()).executeUpdate();
        }
    }

    @Transactional(transactionManager = "transactionManagerOracle")
    public void syncToOraclePaperQuestions(Paper p, List<PaperQuestion> pqs) {
        // 级联子表：直接删除后重新插入
        oracleEm.createNativeQuery("DELETE FROM paper_question WHERE paper_id = ?1")
                .setParameter(1, p.getId()).executeUpdate();
        for (PaperQuestion pq : pqs) {
            oracleEm.createNativeQuery("INSERT INTO paper_question (paper_id, question_id, score) VALUES (?1, ?2, ?3)")
                    .setParameter(1, p.getId())
                    .setParameter(2, pq.getQuestion().getId())
                    .setParameter(3, pq.getScore()).executeUpdate();
        }
    }

    @Transactional(transactionManager = "transactionManagerOracle")
    public void syncToOracleResult(ExamResult r) {
        Long sid = r.getStudent() != null ? r.getStudent().getId() : null;
        Long pid = r.getPaper() != null ? r.getPaper().getId() : null;

        String update = "UPDATE exam_result SET student_id=?1, paper_id=?2, score=?3, exam_time=?4, update_time=?5 WHERE id=?6";
        int rows = oracleEm.createNativeQuery(update)
                .setParameter(1, sid).setParameter(2, pid).setParameter(3, r.getScore())
                .setParameter(4, r.getCreateTime()).setParameter(5, r.getUpdateTime())
                .setParameter(6, r.getId()).executeUpdate();
        if (rows == 0) {
            String insert = "INSERT INTO exam_result (id, student_id, paper_id, score, exam_time, update_time) VALUES (?1, ?2, ?3, ?4, ?5, ?6)";
            oracleEm.createNativeQuery(insert)
                    .setParameter(1, r.getId()).setParameter(2, sid).setParameter(3, pid)
                    .setParameter(4, r.getScore()).setParameter(5, r.getCreateTime())
                    .setParameter(6, r.getUpdateTime()).executeUpdate();
        }
    }


    // =========================================================
    // 9. Native Writes (SQL Server) - Upsert + IDENTITY_INSERT 实现
    // =========================================================

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
                    if (ps.executeUpdate() > 0) return; // 更新成功，返回
                }

                // 2. INSERT
                try (java.sql.Statement stmt = connection.createStatement()) {
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
                    stmt.execute("SET IDENTITY_INSERT dbo.sys_user OFF");
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("SQL Server User同步失败: " + e.getMessage());
            }
        });
    }

    @Transactional(transactionManager = "transactionManagerSqlServer")
    public void syncToSqlServerQuestion(Question q) {
        org.hibernate.Session session = sqlServerEm.unwrap(org.hibernate.Session.class);
        session.doWork(connection -> {
            try {
                String update = "UPDATE dbo.question SET content=?, type=?, difficulty=?, knowledge_point=?, answer=?, update_time=? WHERE id=?";
                try (java.sql.PreparedStatement ps = connection.prepareStatement(update)) {
                    ps.setString(1, q.getContent());
                    ps.setString(2, q.getType());
                    ps.setString(3, q.getDifficulty());
                    ps.setString(4, q.getKnowledgePoint());
                    ps.setString(5, q.getAnswer());
                    ps.setTimestamp(6, q.getUpdateTime() != null ? Timestamp.valueOf(q.getUpdateTime()) : null);
                    ps.setLong(7, q.getId());
                    if (ps.executeUpdate() > 0) return;
                }
                try (java.sql.Statement stmt = connection.createStatement()) {
                    stmt.execute("SET IDENTITY_INSERT dbo.question ON");
                    String insert = "INSERT INTO dbo.question (id, content, type, difficulty, knowledge_point, answer, update_time) VALUES (?, ?, ?, ?, ?, ?, ?)";
                    try (java.sql.PreparedStatement ps = connection.prepareStatement(insert)) {
                        ps.setLong(1, q.getId());
                        ps.setString(2, q.getContent());
                        ps.setString(3, q.getType());
                        ps.setString(4, q.getDifficulty());
                        ps.setString(5, q.getKnowledgePoint());
                        ps.setString(6, q.getAnswer());
                        ps.setTimestamp(7, q.getUpdateTime() != null ? Timestamp.valueOf(q.getUpdateTime()) : null);
                        ps.executeUpdate();
                    }
                    stmt.execute("SET IDENTITY_INSERT dbo.question OFF");
                }
            } catch (Exception e) {
                throw new RuntimeException("SQL Server Question同步失败: " + e.getMessage());
            }
        });
    }

    @Transactional(transactionManager = "transactionManagerSqlServer")
    public void syncToSqlServerPaper(Paper p) {
        org.hibernate.Session session = sqlServerEm.unwrap(org.hibernate.Session.class);
        session.doWork(connection -> {
            try {
                String update = "UPDATE dbo.paper SET paper_name=?, total_score=?, teacher_id=?, create_time=?, update_time=? WHERE id=?";
                try (java.sql.PreparedStatement ps = connection.prepareStatement(update)) {
                    ps.setString(1, p.getPaperName());
                    ps.setObject(2, p.getTotalScore(), java.sql.Types.INTEGER);
                    ps.setObject(3, p.getTeacher() != null ? p.getTeacher().getId() : null, java.sql.Types.BIGINT);
                    ps.setTimestamp(4, p.getCreateTime() != null ? Timestamp.valueOf(p.getCreateTime()) : null);
                    ps.setTimestamp(5, p.getUpdateTime() != null ? Timestamp.valueOf(p.getUpdateTime()) : null);
                    ps.setLong(6, p.getId());
                    if (ps.executeUpdate() > 0) return;
                }
                try (java.sql.Statement stmt = connection.createStatement()) {
                    stmt.execute("SET IDENTITY_INSERT dbo.paper ON");
                    String insert = "INSERT INTO dbo.paper (id, paper_name, total_score, teacher_id, create_time, update_time) VALUES (?, ?, ?, ?, ?, ?)";
                    try (java.sql.PreparedStatement ps = connection.prepareStatement(insert)) {
                        ps.setLong(1, p.getId());
                        ps.setString(2, p.getPaperName());
                        ps.setObject(3, p.getTotalScore(), java.sql.Types.INTEGER);
                        ps.setObject(4, p.getTeacher() != null ? p.getTeacher().getId() : null, java.sql.Types.BIGINT);
                        ps.setTimestamp(5, p.getCreateTime() != null ? Timestamp.valueOf(p.getCreateTime()) : null);
                        ps.setTimestamp(6, p.getUpdateTime() != null ? Timestamp.valueOf(p.getUpdateTime()) : null);
                        ps.executeUpdate();
                    }
                    stmt.execute("SET IDENTITY_INSERT dbo.paper OFF");
                }
            } catch (Exception e) {
                throw new RuntimeException("SQL Server Paper同步失败: " + e.getMessage());
            }
        });
    }

    @Transactional(transactionManager = "transactionManagerSqlServer")
    public void syncToSqlServerPaperQuestions(Paper p, List<PaperQuestion> pqs) {
        org.hibernate.Session session = sqlServerEm.unwrap(org.hibernate.Session.class);
        session.doWork(connection -> {
            try {
                // 先删
                try (java.sql.PreparedStatement psDel = connection.prepareStatement("DELETE FROM dbo.paper_question WHERE paper_id = ?")) {
                    psDel.setLong(1, p.getId());
                    psDel.executeUpdate();
                }
                // 后插 (关联表无ID，无需 IDENTITY_INSERT)
                String insert = "INSERT INTO dbo.paper_question (paper_id, question_id, score) VALUES (?, ?, ?)";
                try (java.sql.PreparedStatement ps = connection.prepareStatement(insert)) {
                    for (PaperQuestion pq : pqs) {
                        ps.setObject(1, p.getId(), java.sql.Types.BIGINT);
                        ps.setObject(2, pq.getQuestion().getId(), java.sql.Types.BIGINT);
                        ps.setObject(3, pq.getScore(), java.sql.Types.INTEGER);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            } catch (Exception e) {
                throw new RuntimeException("SQL Server PaperQuestion同步失败: " + e.getMessage());
            }
        });
    }

    @Transactional(transactionManager = "transactionManagerSqlServer")
    public void syncToSqlServerResult(ExamResult r) {
        org.hibernate.Session session = sqlServerEm.unwrap(org.hibernate.Session.class);
        session.doWork(connection -> {
            try {
                String update = "UPDATE dbo.exam_result SET student_id=?, paper_id=?, score=?, exam_time=?, update_time=? WHERE id=?";
                try (java.sql.PreparedStatement ps = connection.prepareStatement(update)) {
                    ps.setObject(1, r.getStudent() != null ? r.getStudent().getId() : null, java.sql.Types.BIGINT);
                    ps.setObject(2, r.getPaper() != null ? r.getPaper().getId() : null, java.sql.Types.BIGINT);
                    ps.setBigDecimal(3, r.getScore());
                    ps.setTimestamp(4, r.getCreateTime() != null ? Timestamp.valueOf(r.getCreateTime()) : null);
                    ps.setTimestamp(5, r.getUpdateTime() != null ? Timestamp.valueOf(r.getUpdateTime()) : null);
                    ps.setLong(6, r.getId());
                    if (ps.executeUpdate() > 0) return;
                }
                try (java.sql.Statement stmt = connection.createStatement()) {
                    stmt.execute("SET IDENTITY_INSERT dbo.exam_result ON");
                    String insert = "INSERT INTO dbo.exam_result (id, student_id, paper_id, score, exam_time, update_time) VALUES (?, ?, ?, ?, ?, ?)";
                    try (java.sql.PreparedStatement ps = connection.prepareStatement(insert)) {
                        ps.setLong(1, r.getId());
                        ps.setObject(2, r.getStudent() != null ? r.getStudent().getId() : null, java.sql.Types.BIGINT);
                        ps.setObject(3, r.getPaper() != null ? r.getPaper().getId() : null, java.sql.Types.BIGINT);
                        ps.setBigDecimal(4, r.getScore());
                        ps.setTimestamp(5, r.getCreateTime() != null ? Timestamp.valueOf(r.getCreateTime()) : null);
                        ps.setTimestamp(6, r.getUpdateTime() != null ? Timestamp.valueOf(r.getUpdateTime()) : null);
                        ps.executeUpdate();
                    }
                    stmt.execute("SET IDENTITY_INSERT dbo.exam_result OFF");
                }
            } catch (Exception e) {
                throw new RuntimeException("SQL Server ExamResult同步失败: " + e.getMessage());
            }
        });
    }


    // =========================================================
    // [新增] Native Writes (MySQL) - 强制保留 ID
    // =========================================================

    @Transactional
    public void syncToMysqlNative(User u) {
        // MySQL 允许直接插入 ID，无需像 SQL Server 那样 SET IDENTITY_INSERT
        String updateSql = "UPDATE sys_user SET username=?1, password=?2, role=?3, real_name=?4, create_time=?5, update_time=?6 WHERE id=?7";
        int rows = oracleEm.createNativeQuery(updateSql) //借用 oracleEm 或 entityManager 都可以执行通用 SQL
                .setParameter(1, u.getUsername()).setParameter(2, u.getPassword())
                .setParameter(3, u.getRole()).setParameter(4, u.getRealName())
                .setParameter(5, u.getCreateTime()).setParameter(6, u.getUpdateTime())
                .setParameter(7, u.getId()).executeUpdate();

        if (rows == 0) {
            String insertSql = "INSERT INTO sys_user (id, username, password, role, real_name, create_time, update_time) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)";
            oracleEm.createNativeQuery(insertSql) // 只要是 JPA EntityManager 都能跑原生 SQL
                    .setParameter(1, u.getId()).setParameter(2, u.getUsername())
                    .setParameter(3, u.getPassword()).setParameter(4, u.getRole())
                    .setParameter(5, u.getRealName()).setParameter(6, u.getCreateTime())
                    .setParameter(7, u.getUpdateTime()).executeUpdate();
        }
    }

    // [新增] 题目的 MySQL 原生同步
    @Transactional
    public void syncToMysqlQuestion(Question q) {
        String update = "UPDATE question SET content=?1, type=?2, difficulty=?3, knowledge_point=?4, answer=?5, update_time=?6 WHERE id=?7";
        int rows = oracleEm.createNativeQuery(update)
                .setParameter(1, q.getContent()).setParameter(2, q.getType())
                .setParameter(3, q.getDifficulty()).setParameter(4, q.getKnowledgePoint())
                .setParameter(5, q.getAnswer()).setParameter(6, q.getUpdateTime())
                .setParameter(7, q.getId()).executeUpdate();
        if (rows == 0) {
            String insert = "INSERT INTO question (id, content, type, difficulty, knowledge_point, answer, update_time) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)";
            oracleEm.createNativeQuery(insert)
                    .setParameter(1, q.getId()).setParameter(2, q.getContent())
                    .setParameter(3, q.getType()).setParameter(4, q.getDifficulty())
                    .setParameter(5, q.getKnowledgePoint()).setParameter(6, q.getAnswer())
                    .setParameter(7, q.getUpdateTime()).executeUpdate();
        }
    }

    // [新增] 试卷的 MySQL 原生同步
    @Transactional
    public void syncToMysqlPaper(Paper p) {
        String update = "UPDATE paper SET paper_name=?1, total_score=?2, teacher_id=?3, create_time=?4, update_time=?5 WHERE id=?6";
        int rows = oracleEm.createNativeQuery(update)
                .setParameter(1, p.getPaperName()).setParameter(2, p.getTotalScore())
                .setParameter(3, p.getTeacher() != null ? p.getTeacher().getId() : null)
                .setParameter(4, p.getCreateTime()).setParameter(5, p.getUpdateTime())
                .setParameter(6, p.getId()).executeUpdate();
        if (rows == 0) {
            String insert = "INSERT INTO paper (id, paper_name, total_score, teacher_id, create_time, update_time) VALUES (?1, ?2, ?3, ?4, ?5, ?6)";
            oracleEm.createNativeQuery(insert)
                    .setParameter(1, p.getId()).setParameter(2, p.getPaperName())
                    .setParameter(3, p.getTotalScore())
                    .setParameter(4, p.getTeacher() != null ? p.getTeacher().getId() : null)
                    .setParameter(5, p.getCreateTime()).setParameter(6, p.getUpdateTime()).executeUpdate();
        }
    }

    // [新增] 成绩的 MySQL 原生同步
    @Transactional
    public void syncToMysqlResult(ExamResult r) {
        Long sid = r.getStudent() != null ? r.getStudent().getId() : null;
        Long pid = r.getPaper() != null ? r.getPaper().getId() : null;
        String update = "UPDATE exam_result SET student_id=?1, paper_id=?2, score=?3, exam_time=?4, update_time=?5 WHERE id=?6";
        int rows = oracleEm.createNativeQuery(update)
                .setParameter(1, sid).setParameter(2, pid).setParameter(3, r.getScore())
                .setParameter(4, r.getCreateTime()).setParameter(5, r.getUpdateTime())
                .setParameter(6, r.getId()).executeUpdate();
        if (rows == 0) {
            String insert = "INSERT INTO exam_result (id, student_id, paper_id, score, exam_time, update_time) VALUES (?1, ?2, ?3, ?4, ?5, ?6)";
            oracleEm.createNativeQuery(insert)
                    .setParameter(1, r.getId()).setParameter(2, sid).setParameter(3, pid)
                    .setParameter(4, r.getScore()).setParameter(5, r.getCreateTime())
                    .setParameter(6, r.getUpdateTime()).executeUpdate();
        }
    }

    // =========================================================
    // 10. MySQL 辅助: 同步 PaperQuestion
    // =========================================================
    private void syncPaperQuestionsToMysql(Paper p, List<PaperQuestion> pqs) {
        // 先删除旧的
        mysqlPaperQuestionRepo.deleteByPaperId(p.getId());

        // 重新构建并保存 (必须新建对象或detach，防止主键冲突或JPA缓存问题)
        List<PaperQuestion> newPqs = new ArrayList<>();
        for (PaperQuestion sourcePq : pqs) {
            PaperQuestion newPq = new PaperQuestion();
            newPq.setPaper(p); // 关联到 MySQL 的 Paper 实体
            newPq.setQuestion(sourcePq.getQuestion()); // 假设 Question ID 一致
            newPq.setScore(sourcePq.getScore());
            newPqs.add(newPq);
        }
        mysqlPaperQuestionRepo.saveAll(newPqs);
    }

    // =========================================================
    // 11. [兼容] 供 ConflictController 调用的单条修复 (Legacy)
    // =========================================================

    @Transactional(transactionManager = "transactionManagerOracle")
    public void syncSinglePaperToOracle(Long paperId) {
        Paper p = mysqlPaperRepo.findById(paperId).orElse(null);
        if (p != null) {
            self.syncToOraclePaper(p); // 复用新逻辑
            // 顺便把题目也同步过去
            List<PaperQuestion> pqs = mysqlPaperQuestionRepo.findAll().stream()
                    .filter(pq -> pq.getPaper().getId().equals(paperId)).collect(Collectors.toList());
            self.syncToOraclePaperQuestions(p, pqs);
        }
    }

    @Transactional(transactionManager = "transactionManagerSqlServer")
    public void syncSinglePaperToSqlServer(Long paperId) {
        Paper p = mysqlPaperRepo.findById(paperId).orElse(null);
        if (p != null) {
            self.syncToSqlServerPaper(p); // 复用新逻辑
            List<PaperQuestion> pqs = mysqlPaperQuestionRepo.findAll().stream()
                    .filter(pq -> pq.getPaper().getId().equals(paperId)).collect(Collectors.toList());
            self.syncToSqlServerPaperQuestions(p, pqs);
        }
    }

    // =========================================================
    // 12. 邮件报警
    // =========================================================
    private void sendAlertMail(String errorMsg) {
        if (mailSender == null) return;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo("2450956909@qq.com");
            message.setSubject("【严重报警】数据库同步服务异常");
            message.setText("同步服务检测到错误，请检查：\n" + errorMsg);
            mailSender.send(message);
        } catch (Exception ex) {
            System.err.println("报警邮件发送失败: " + ex.getMessage());
        }
    }

    // =========================================================
    // 13. [修正] 全局删除试卷 (拆分事务，解决报错)
    // =========================================================
    @Transactional // 1. 开启 MySQL 默认事务
    public void deletePaperGlobally(Long paperId) {
        System.out.println(">>> [全局删除] 正在删除试卷 ID: " + paperId);

        // --- 1. MySQL 删除 ---
        // 1.1 删成绩 (先在内存里查出来删，防止外键问题)
        List<ExamResult> results = mysqlExamResultRepo.findAll().stream()
                .filter(r -> r.getPaper() != null && r.getPaper().getId().equals(paperId))
                .collect(Collectors.toList());
        if (!results.isEmpty()) {
            mysqlExamResultRepo.deleteAll(results);
        }

        // 1.2 删组卷记录 (调用 Repository 自定义方法)
        mysqlPaperQuestionRepo.deleteByPaperId(paperId);

        // 1.3 删试卷本体
        mysqlPaperRepo.deleteById(paperId);

        // --- 2. Oracle 删除 (调用独立事务方法) ---
        // 必须用 self. 调用，否则事务注解不生效
        self.deletePaperOracle(paperId);

        // --- 3. SQL Server 删除 (调用独立事务方法) ---
        self.deletePaperSqlServer(paperId);
    }

    // [新增] Oracle 专用删除事务
    @Transactional(transactionManager = "transactionManagerOracle")
    public void deletePaperOracle(Long paperId) {
        oracleEm.createNativeQuery("DELETE FROM exam_result WHERE paper_id = ?1").setParameter(1, paperId).executeUpdate();
        oracleEm.createNativeQuery("DELETE FROM paper_question WHERE paper_id = ?1").setParameter(1, paperId).executeUpdate();
        oracleEm.createNativeQuery("DELETE FROM paper WHERE id = ?1").setParameter(1, paperId).executeUpdate();
    }

    // [新增] SQL Server 专用删除事务
    @Transactional(transactionManager = "transactionManagerSqlServer")
    public void deletePaperSqlServer(Long paperId) {
        org.hibernate.Session session = sqlServerEm.unwrap(org.hibernate.Session.class);
        session.doWork(connection -> {
            try (java.sql.Statement stmt = connection.createStatement()) {
                // 3.1 删成绩
                try (java.sql.PreparedStatement ps = connection.prepareStatement("DELETE FROM dbo.exam_result WHERE paper_id = ?")) {
                    ps.setLong(1, paperId);
                    ps.executeUpdate();
                }
                // 3.2 删组卷
                try (java.sql.PreparedStatement ps = connection.prepareStatement("DELETE FROM dbo.paper_question WHERE paper_id = ?")) {
                    ps.setLong(1, paperId);
                    ps.executeUpdate();
                }
                // 3.3 删试卷
                try (java.sql.PreparedStatement ps = connection.prepareStatement("DELETE FROM dbo.paper WHERE id = ?")) {
                    ps.setLong(1, paperId);
                    ps.executeUpdate();
                }
            } catch (Exception e) {
                throw new RuntimeException("SQL Server 删除试卷失败: " + e.getMessage());
            }
        });
    }

    // =========================================================
    // 14. [新增] 全局删除用户 (同时清理 MySQL, Oracle, SQL Server)
    // =========================================================
    @Transactional
    public void deleteUserGlobally(Long userId) {
        System.out.println(">>> [全局删除] 正在删除用户 ID: " + userId);

        // --- 1. MySQL 删除 (JPA) ---
        // 1.1 删成绩 (如果是学生，先删成绩防止外键冲突)
        mysqlExamResultRepo.deleteByStudentId(userId); // 需要确认 Repo 有这个方法，如果没有，看下面注释
        // 1.2 删试卷 (如果是老师，建议把试卷的 teacher_id 置空，或者直接删除试卷。这里为了彻底清理，选择删除关联试卷)
        // 注意：删试卷比较麻烦，因为试卷还有题目关联。
        // 简单策略：先只删用户，如果数据库有级联删除(Cascade)最好；如果没有，这里只演示删用户。
        // 如果碰到外键报错，需要先手动清理 paper 表。这里假设只清理成绩。

        // 1.3 删用户本体
        if (mysqlUserRepo.existsById(userId)) {
            mysqlUserRepo.deleteById(userId);
        }

        // --- 2. Oracle 删除 (必须用 self. 调用以触发新事务) ---
        self.deleteUserOracle(userId);

        // --- 3. SQL Server 删除 (必须用 self. 调用以触发新事务) ---
        self.deleteUserSqlServer(userId);
    }

    // [新增] Oracle 专用删除用户事务
    @Transactional(transactionManager = "transactionManagerOracle")
    public void deleteUserOracle(Long userId) {
        // 1. 删关联成绩
        oracleEm.createNativeQuery("DELETE FROM exam_result WHERE student_id = ?1").setParameter(1, userId).executeUpdate();
        // 2. 删关联试卷 (可选：将 teacher_id 置空，防止删不掉)
        oracleEm.createNativeQuery("UPDATE paper SET teacher_id = NULL WHERE teacher_id = ?1").setParameter(1, userId).executeUpdate();
        // 3. 删用户
        oracleEm.createNativeQuery("DELETE FROM sys_user WHERE id = ?1").setParameter(1, userId).executeUpdate();
    }

    // [新增] SQL Server 专用删除用户事务
    @Transactional(transactionManager = "transactionManagerSqlServer")
    public void deleteUserSqlServer(Long userId) {
        org.hibernate.Session session = sqlServerEm.unwrap(org.hibernate.Session.class);
        session.doWork(connection -> {
            try (java.sql.Statement stmt = connection.createStatement()) {
                // 1. 删关联成绩
                try (java.sql.PreparedStatement ps = connection.prepareStatement("DELETE FROM dbo.exam_result WHERE student_id = ?")) {
                    ps.setLong(1, userId);
                    ps.executeUpdate();
                }
                // 2. 关联试卷置空
                try (java.sql.PreparedStatement ps = connection.prepareStatement("UPDATE dbo.paper SET teacher_id = NULL WHERE teacher_id = ?")) {
                    ps.setLong(1, userId);
                    ps.executeUpdate();
                }
                // 3. 删用户
                try (java.sql.PreparedStatement ps = connection.prepareStatement("DELETE FROM dbo.sys_user WHERE id = ?")) {
                    ps.setLong(1, userId);
                    ps.executeUpdate();
                }
            } catch (Exception e) {
                throw new RuntimeException("SQL Server 删除用户失败: " + e.getMessage());
            }
        });
    }

    // =========================================================
    // 15. [新增] 全局删除试题 (同时清理 MySQL, Oracle, SQL Server)
    // =========================================================
    @Transactional
    public void deleteQuestionGlobally(Long questionId) {
        System.out.println(">>> [全局删除] 正在删除试题 ID: " + questionId);

        // --- 1. MySQL 删除 ---
        // 1.1 先删关联的组卷记录 (paper_question)
        // 注意：MysqlPaperQuestionRepository 可能没有 deleteByQuestionId，
        // 我们先查出来再删，或者你可以去 Repository 加一个方法。这里用通用查删法：
        List<PaperQuestion> pqs = mysqlPaperQuestionRepo.findAll().stream()
                .filter(pq -> pq.getQuestion().getId().equals(questionId))
                .collect(Collectors.toList());
        if (!pqs.isEmpty()) {
            mysqlPaperQuestionRepo.deleteAll(pqs);
        }

        // 1.2 删题目本体
        if (mysqlQuestionRepo.existsById(questionId)) {
            mysqlQuestionRepo.deleteById(questionId);
        }

        // --- 2. Oracle 删除 ---
        self.deleteQuestionOracle(questionId);

        // --- 3. SQL Server 删除 ---
        self.deleteQuestionSqlServer(questionId);
    }

    // [新增] Oracle 专用删除试题事务
    @Transactional(transactionManager = "transactionManagerOracle")
    public void deleteQuestionOracle(Long questionId) {
        // 1. 删关联
        oracleEm.createNativeQuery("DELETE FROM paper_question WHERE question_id = ?1").setParameter(1, questionId).executeUpdate();
        // 2. 删本体
        oracleEm.createNativeQuery("DELETE FROM question WHERE id = ?1").setParameter(1, questionId).executeUpdate();
    }

    // [新增] SQL Server 专用删除试题事务
    @Transactional(transactionManager = "transactionManagerSqlServer")
    public void deleteQuestionSqlServer(Long questionId) {
        org.hibernate.Session session = sqlServerEm.unwrap(org.hibernate.Session.class);
        session.doWork(connection -> {
            try (java.sql.Statement stmt = connection.createStatement()) {
                // 1. 删关联
                try (java.sql.PreparedStatement ps = connection.prepareStatement("DELETE FROM dbo.paper_question WHERE question_id = ?")) {
                    ps.setLong(1, questionId);
                    ps.executeUpdate();
                }
                // 2. 删本体
                try (java.sql.PreparedStatement ps = connection.prepareStatement("DELETE FROM dbo.question WHERE id = ?")) {
                    ps.setLong(1, questionId);
                    ps.executeUpdate();
                }
            } catch (Exception e) {
                throw new RuntimeException("SQL Server 删除试题失败: " + e.getMessage());
            }
        });
    }
}
