package com.exam.backend.service;

import com.exam.backend.controller.dto.ConflictDTO;
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

    // === Oracle Repos ===
    @Autowired private OracleUserRepository oracleUserRepo;
    @Autowired private OracleQuestionRepository oracleQuestionRepo;
    @Autowired private OraclePaperRepository oraclePaperRepo;
    @Autowired private OraclePaperQuestionRepository oraclePaperQuestionRepo;
    @Autowired private OracleExamResultRepository oracleExamResultRepo;

    // === SQL Server Repos ===
    @Autowired private SqlServerUserRepository sqlServerUserRepo;
    @Autowired private SqlServerQuestionRepository sqlServerQuestionRepo;
    @Autowired private SqlServerPaperRepository sqlServerPaperRepo;
    @Autowired private SqlServerPaperQuestionRepository sqlServerPaperQuestionRepo;
    @Autowired private SqlServerExamResultRepository sqlServerExamResultRepo;

    // === Entity Managers (用于原生 SQL) ===
    @PersistenceContext(unitName = "oraclePersistenceUnit") private EntityManager oracleEm;
    @PersistenceContext(unitName = "sqlServerPersistenceUnit") private EntityManager sqlServerEm;
    @PersistenceContext private EntityManager mysqlEm;


    // 【新增】记录上次报警邮件发送的时间戳
    private long lastAlertTime = 0;
    // 【新增】设置最小报警间隔 (毫秒)，例如 60秒。防止邮件轰炸。
    private static final long ALERT_INTERVAL = 60 * 1000;


    // =========================================================
    // 1. [高频] 冲突监控看门狗 (只报警，不修复) - 每 5 秒执行
    // =========================================================
    @Scheduled(cron = "0/5 * * * * ?")
    public void monitorConflicts() {
        try {
            // 1. 快速检测冲突 (数据库查询很快，可以高频做)
            List<ConflictDTO> conflicts = self.detectConflicts();

            if (!conflicts.isEmpty()) {
                long currentTime = System.currentTimeMillis();

                // 2. 只有距离上次发送超过 60秒，才再次发送邮件
                if (currentTime - lastAlertTime > ALERT_INTERVAL) {
                    System.err.println(">>> [实时监控] 发现 " + conflicts.size() + " 个数据冲突，正在发送报警邮件...");
                    sendConflictNotification(conflicts);

                    // 更新发送时间
                    lastAlertTime = currentTime;
                } else {
                    // 冲突依然存在，但处于静默期，仅打印日志，不发邮件
                    System.out.println(">>> [实时监控] 冲突仍存在 (冷却中，暂不发送邮件)");
                }
            } else {
                // 如果冲突解决了，重置计时器(可选)，或者不做处理
            }
        } catch (Exception e) {
            System.err.println("监控任务异常: " + e.getMessage());
        }
    }

    // =========================================================
    // 2. [低频] 全量数据同步兜底 (负责修复) - 每 1 分钟执行
    // =========================================================
    // 【修正】Cron: 0 * * * * ? 代表每分钟的第0秒执行 (即每分钟一次)
    @Scheduled(cron = "0 * * * * ?")
    public void scheduledSync() {
        SyncLog log = new SyncLog();
        log.setStartTime(LocalDateTime.now());
        log.setStatus("RUNNING");
        log.setMessage("全量自动修复开始...");
        log = syncLogRepo.save(log);

        try {
            System.out.println(">>>  开始全量数据同步...");

            // 执行所有业务表的双向同步 (这是真正修复数据的地方)
            self.syncUsersBidirectional();
            self.syncQuestionsBidirectional();
            self.syncPapersBidirectional();
            self.syncExamResultsBidirectional();

            log.setEndTime(LocalDateTime.now());
            log.setStatus("SUCCESS");
            log.setMessage("自动修复完成");
            syncLogRepo.save(log);
            System.out.println(">>>  数据同步完成");

        } catch (Exception e) {
            e.printStackTrace();
            log.setEndTime(LocalDateTime.now());
            log.setStatus("FAILED");
            String error = e.getMessage() != null ? e.getMessage() : "Unknown Error";
            log.setMessage(error.length() > 3900 ? error.substring(0, 3900) : error);
            syncLogRepo.save(log);
        }
    }

    // =========================================================
    // 3. [核心] 冲突检测逻辑
    // =========================================================
    public List<ConflictDTO> detectConflicts() {
        List<ConflictDTO> conflicts = new ArrayList<>();
        long tempIdCounter = 1;

        // --- 用户表检测 ---
        List<User> mysqlUsers = mysqlUserRepo.findAll();
        Map<Long, User> oracleUserMap = oracleUserRepo.findAll().stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, User> sqlServerUserMap = sqlServerUserRepo.findAll().stream().collect(Collectors.toMap(User::getId, Function.identity()));

        for (User mUser : mysqlUsers) {
            checkUserConflict(conflicts, tempIdCounter++, "Oracle", mUser, oracleUserMap.get(mUser.getId()));
            checkUserConflict(conflicts, tempIdCounter++, "SQL Server", mUser, sqlServerUserMap.get(mUser.getId()));
        }

        // --- 试卷表检测 ---
        List<Paper> mysqlPapers = mysqlPaperRepo.findAll();
        Map<Long, Paper> oraclePaperMap = oraclePaperRepo.findAll().stream().collect(Collectors.toMap(Paper::getId, Function.identity()));
        Map<Long, Paper> sqlServerPaperMap = sqlServerPaperRepo.findAll().stream().collect(Collectors.toMap(Paper::getId, Function.identity()));

        for (Paper mPaper : mysqlPapers) {
            checkPaperConflict(conflicts, tempIdCounter++, "Oracle", mPaper, oraclePaperMap.get(mPaper.getId()));
            checkPaperConflict(conflicts, tempIdCounter++, "SQL Server", mPaper, sqlServerPaperMap.get(mPaper.getId()));
        }

        return conflicts;
    }

    private void checkUserConflict(List<ConflictDTO> list, long tempId, String targetDbName, User source, User target) {
        if (target == null) {
            list.add(new ConflictDTO(tempId, "sys_user", "MISSING_IN_TARGET",
                    targetDbName + " 备库缺少用户 ID: " + source.getId(), String.valueOf(source.getId())));
        } else {
            boolean isDiff = !Objects.equals(source.getUsername(), target.getUsername()) ||
                    !Objects.equals(source.getRole(), target.getRole());
            if (isDiff) {
                list.add(new ConflictDTO(tempId, "sys_user", "DATA_MISMATCH",
                        "数据不一致 [" + targetDbName + "]。主库: " + source.getUsername() + "(" + source.getRole() + "), 备库: " + target.getUsername() + "(" + target.getRole() + ")",
                        String.valueOf(source.getId())));
            }
        }
    }

    private void checkPaperConflict(List<ConflictDTO> list, long tempId, String targetDbName, Paper source, Paper target) {
        if (target == null) {
            list.add(new ConflictDTO(tempId, "paper", "MISSING_IN_TARGET",
                    targetDbName + " 备库缺少试卷 ID: " + source.getId(), String.valueOf(source.getId())));
        } else {
            boolean isDiff = !Objects.equals(source.getPaperName(), target.getPaperName()) ||
                    !Objects.equals(source.getTotalScore(), target.getTotalScore());
            if (isDiff) {
                list.add(new ConflictDTO(tempId, "paper", "DATA_MISMATCH",
                        "试卷信息不一致 [" + targetDbName + "]。主库: " + source.getPaperName(),
                        String.valueOf(source.getId())));
            }
        }
    }

    // =========================================================
    // 4. 发送邮件方法
    // =========================================================
    private void sendConflictNotification(List<ConflictDTO> conflicts) {
        if (mailSender == null) return;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo("2450956909@qq.com");
            message.setSubject("【数据监控】发现 " + conflicts.size() + " 项数据冲突 (请及时处理)");

            StringBuilder sb = new StringBuilder();
            sb.append("实时监控系统检测到以下数据不一致：\n\n");

            int limit = Math.min(conflicts.size(), 10);
            for (int i = 0; i < limit; i++) {
                ConflictDTO c = conflicts.get(i);
                sb.append(String.format("%d. [%s] %s\n", i + 1, c.getTableName(), c.getDescription()));
            }
            if (conflicts.size() > 10) sb.append("\n... 等更多冲突。");

            sb.append("\n\n请尽快登录后台 [冲突处理] 页面进行修复，或等待定时同步任务自动覆盖。");
            message.setText(sb.toString());

            mailSender.send(message);
            System.out.println(">>> [邮件] 报警邮件已发送");
        } catch (Exception ex) {
            System.err.println("邮件发送失败: " + ex.getMessage());
        }
    }

    // =========================================================
    // 5. 实时同步入口 (保持同步调用)
    // =========================================================
    public void executeImmediateSync() {
        System.out.println(">>> [实时同步] 响应业务操作，立即执行增量同步...");
        try {
            self.syncUsersBidirectional();
            self.syncQuestionsBidirectional();
            self.syncPapersBidirectional();
            self.syncExamResultsBidirectional();
        } catch (Exception e) {
            System.err.println("实时同步执行异常: " + e.getMessage());
        }
    }

    public void syncData() {
        this.executeImmediateSync();
    }

    // =========================================================
    // 6. 辅助: 决定胜者 (谁新谁赢)
    // =========================================================
    private <T> T determineWinner(T m, T o, T s, Function<T, LocalDateTime> timeExtractor) {
        T winner = null;
        LocalDateTime maxTime = null;
        List<T> items = Arrays.asList(m, o, s);
        for (T item : items) {
            if (item == null) continue;
            LocalDateTime t = timeExtractor.apply(item);
            if (winner == null) {
                winner = item;
                maxTime = t;
            } else {
                if (t != null && (maxTime == null || t.isAfter(maxTime))) {
                    winner = item;
                    maxTime = t;
                }
            }
        }
        return winner;
    }

    // =========================================================
    // 7. 核心业务同步
    // =========================================================

    // User
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
            User m = mM.get(id); User o = oM.get(id); User s = sM.get(id);
            User winner = determineWinner(m, o, s, User::getUpdateTime);
            if (winner == null) continue;
            if (m == null || !m.equals(winner)) try { self.syncToMysqlNative(winner); } catch (Exception e) {}
            if (o == null || !o.equals(winner)) try { self.syncToOracleNative(copyUser(winner)); } catch (Exception e) {}
            if (s == null || !s.equals(winner)) try { self.syncToSqlServerNative(copyUser(winner)); } catch (Exception e) {}
        }
    }
    private User copyUser(User source) {
        User target = new User();
        target.setId(source.getId()); target.setUsername(source.getUsername()); target.setPassword(source.getPassword());
        target.setRole(source.getRole()); target.setRealName(source.getRealName());
        target.setCreateTime(source.getCreateTime()); target.setUpdateTime(source.getUpdateTime());
        return target;
    }

    // Question
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
            Question m = mM.get(id); Question o = oM.get(id); Question s = sM.get(id);
            Question winner = determineWinner(m, o, s, Question::getUpdateTime);
            if (winner == null) continue;
            if (m == null || !m.equals(winner)) self.syncToMysqlQuestion(winner);
            if (o == null || !o.equals(winner)) self.syncToOracleQuestion(winner);
            if (s == null || !s.equals(winner)) self.syncToSqlServerQuestion(winner);
        }
    }

    // Paper
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
            Paper m = mM.get(id); Paper o = oM.get(id); Paper s = sM.get(id);
            Paper winner = determineWinner(m, o, s, Paper::getUpdateTime);
            if (winner == null) continue;
            List<PaperQuestion> winnerPQs = fetchPaperQuestions(winner, m, o, s);
            if (m == null || !m.equals(winner)) { self.syncToMysqlPaper(winner); self.syncToMysqlPaperQuestions(winner, winnerPQs); }
            if (o == null || !o.equals(winner)) { self.syncToOraclePaper(winner); self.syncToOraclePaperQuestions(winner, winnerPQs); }
            if (s == null || !s.equals(winner)) { self.syncToSqlServerPaper(winner); self.syncToSqlServerPaperQuestions(winner, winnerPQs); }
        }
    }
    private List<PaperQuestion> fetchPaperQuestions(Paper winner, Paper m, Paper o, Paper s) {
        if (winner == m) return mysqlPaperQuestionRepo.findAll().stream().filter(pq -> pq.getPaper() != null && pq.getPaper().getId().equals(winner.getId())).collect(Collectors.toList());
        if (winner == o) return oraclePaperQuestionRepo.findAll().stream().filter(pq -> pq.getPaper() != null && pq.getPaper().getId().equals(winner.getId())).collect(Collectors.toList());
        if (winner == s) return sqlServerPaperQuestionRepo.findAll().stream().filter(pq -> pq.getPaper() != null && pq.getPaper().getId().equals(winner.getId())).collect(Collectors.toList());
        return new ArrayList<>();
    }

    // Result
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
            ExamResult m = mM.get(id); ExamResult o = oM.get(id); ExamResult s = sM.get(id);
            ExamResult winner = determineWinner(m, o, s, ExamResult::getUpdateTime);
            if (winner == null) continue;
            if (m == null || !m.equals(winner)) self.syncToMysqlResult(winner);
            if (o == null || !o.equals(winner)) self.syncToOracleResult(winner);
            if (s == null || !s.equals(winner)) self.syncToSqlServerResult(winner);
        }
    }

    // =========================================================
    // 8. Native Writes
    // =========================================================
    // Oracle
    @Transactional(transactionManager = "transactionManagerOracle")
    public void syncToOracleNative(User u) {
        String updateSql = "UPDATE sys_user SET username=?1, password=?2, role=?3, real_name=?4, create_time=?5, update_time=?6 WHERE id=?7";
        int rows = oracleEm.createNativeQuery(updateSql).setParameter(1, u.getUsername()).setParameter(2, u.getPassword()).setParameter(3, u.getRole()).setParameter(4, u.getRealName()).setParameter(5, u.getCreateTime()).setParameter(6, u.getUpdateTime()).setParameter(7, u.getId()).executeUpdate();
        if (rows == 0) {
            String insertSql = "INSERT INTO sys_user (id, username, password, role, real_name, create_time, update_time) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)";
            oracleEm.createNativeQuery(insertSql).setParameter(1, u.getId()).setParameter(2, u.getUsername()).setParameter(3, u.getPassword()).setParameter(4, u.getRole()).setParameter(5, u.getRealName()).setParameter(6, u.getCreateTime()).setParameter(7, u.getUpdateTime()).executeUpdate();
        }
    }
    @Transactional(transactionManager = "transactionManagerOracle")
    public void syncToOracleQuestion(Question q) {
        String update = "UPDATE question SET content=?1, type=?2, difficulty=?3, knowledge_point=?4, answer=?5, update_time=?6 WHERE id=?7";
        int rows = oracleEm.createNativeQuery(update).setParameter(1, q.getContent()).setParameter(2, q.getType()).setParameter(3, q.getDifficulty()).setParameter(4, q.getKnowledgePoint()).setParameter(5, q.getAnswer()).setParameter(6, q.getUpdateTime()).setParameter(7, q.getId()).executeUpdate();
        if (rows == 0) {
            String insert = "INSERT INTO question (id, content, type, difficulty, knowledge_point, answer, update_time) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)";
            oracleEm.createNativeQuery(insert).setParameter(1, q.getId()).setParameter(2, q.getContent()).setParameter(3, q.getType()).setParameter(4, q.getDifficulty()).setParameter(5, q.getKnowledgePoint()).setParameter(6, q.getAnswer()).setParameter(7, q.getUpdateTime()).executeUpdate();
        }
    }
    @Transactional(transactionManager = "transactionManagerOracle")
    public void syncToOraclePaper(Paper p) {
        String update = "UPDATE paper SET paper_name=?1, total_score=?2, teacher_id=?3, create_time=?4, update_time=?5 WHERE id=?6";
        int rows = oracleEm.createNativeQuery(update).setParameter(1, p.getPaperName()).setParameter(2, p.getTotalScore()).setParameter(3, p.getTeacher() != null ? p.getTeacher().getId() : null).setParameter(4, p.getCreateTime()).setParameter(5, p.getUpdateTime()).setParameter(6, p.getId()).executeUpdate();
        if (rows == 0) {
            String insert = "INSERT INTO paper (id, paper_name, total_score, teacher_id, create_time, update_time) VALUES (?1, ?2, ?3, ?4, ?5, ?6)";
            oracleEm.createNativeQuery(insert).setParameter(1, p.getId()).setParameter(2, p.getPaperName()).setParameter(3, p.getTotalScore()).setParameter(4, p.getTeacher() != null ? p.getTeacher().getId() : null).setParameter(5, p.getCreateTime()).setParameter(6, p.getUpdateTime()).executeUpdate();
        }
    }
    @Transactional(transactionManager = "transactionManagerOracle")
    public void syncToOraclePaperQuestions(Paper p, List<PaperQuestion> pqs) {
        oracleEm.createNativeQuery("DELETE FROM paper_question WHERE paper_id = ?1").setParameter(1, p.getId()).executeUpdate();
        for (PaperQuestion pq : pqs) {
            oracleEm.createNativeQuery("INSERT INTO paper_question (paper_id, question_id, score) VALUES (?1, ?2, ?3)").setParameter(1, p.getId()).setParameter(2, pq.getQuestion().getId()).setParameter(3, pq.getScore()).executeUpdate();
        }
    }
    @Transactional(transactionManager = "transactionManagerOracle")
    public void syncToOracleResult(ExamResult r) {
        Long sid = r.getStudent() != null ? r.getStudent().getId() : null;
        Long pid = r.getPaper() != null ? r.getPaper().getId() : null;

        // 【修复】增加了 student_answers=?6
        String update = "UPDATE exam_result SET student_id=?1, paper_id=?2, score=?3, exam_time=?4, update_time=?5, student_answers=?6 WHERE id=?7";
        int rows = oracleEm.createNativeQuery(update)
                .setParameter(1, sid).setParameter(2, pid).setParameter(3, r.getScore())
                .setParameter(4, r.getCreateTime()).setParameter(5, r.getUpdateTime())
                .setParameter(6, r.getStudentAnswers()) // 设置答题JSON
                .setParameter(7, r.getId()).executeUpdate();

        if (rows == 0) {
            // 【修复】增加了 student_answers 字段
            String insert = "INSERT INTO exam_result (id, student_id, paper_id, score, exam_time, update_time, student_answers) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)";
            oracleEm.createNativeQuery(insert)
                    .setParameter(1, r.getId()).setParameter(2, sid).setParameter(3, pid)
                    .setParameter(4, r.getScore()).setParameter(5, r.getCreateTime())
                    .setParameter(6, r.getUpdateTime())
                    .setParameter(7, r.getStudentAnswers()) // 设置答题JSON
                    .executeUpdate();
        }
    }

    // SQL Server
    @Transactional(transactionManager = "transactionManagerSqlServer")
    public void syncToSqlServerNative(User u) {
        org.hibernate.Session session = sqlServerEm.unwrap(org.hibernate.Session.class);
        session.doWork(connection -> {
            try {
                String updateSql = "UPDATE dbo.sys_user SET username=?, password=?, role=?, real_name=?, create_time=?, update_time=? WHERE id=?";
                try (java.sql.PreparedStatement ps = connection.prepareStatement(updateSql)) {
                    ps.setString(1, u.getUsername()); ps.setString(2, u.getPassword()); ps.setString(3, u.getRole()); ps.setString(4, u.getRealName());
                    ps.setTimestamp(5, u.getCreateTime() != null ? Timestamp.valueOf(u.getCreateTime()) : null);
                    ps.setTimestamp(6, u.getUpdateTime() != null ? Timestamp.valueOf(u.getUpdateTime()) : null);
                    ps.setLong(7, u.getId());
                    if (ps.executeUpdate() > 0) return;
                }
                try (java.sql.Statement stmt = connection.createStatement()) {
                    stmt.execute("SET IDENTITY_INSERT dbo.sys_user ON");
                    String insertSql = "INSERT INTO dbo.sys_user (id, username, password, role, real_name, create_time, update_time) VALUES (?, ?, ?, ?, ?, ?, ?)";
                    try (java.sql.PreparedStatement ps = connection.prepareStatement(insertSql)) {
                        ps.setLong(1, u.getId()); ps.setString(2, u.getUsername()); ps.setString(3, u.getPassword()); ps.setString(4, u.getRole()); ps.setString(5, u.getRealName());
                        ps.setTimestamp(6, u.getCreateTime() != null ? Timestamp.valueOf(u.getCreateTime()) : null);
                        ps.setTimestamp(7, u.getUpdateTime() != null ? Timestamp.valueOf(u.getUpdateTime()) : null);
                        ps.executeUpdate();
                    }
                    stmt.execute("SET IDENTITY_INSERT dbo.sys_user OFF");
                }
            } catch (Exception e) { throw new RuntimeException(e.getMessage()); }
        });
    }
    @Transactional(transactionManager = "transactionManagerSqlServer")
    public void syncToSqlServerQuestion(Question q) {
        org.hibernate.Session session = sqlServerEm.unwrap(org.hibernate.Session.class);
        session.doWork(connection -> {
            try {
                String update = "UPDATE dbo.question SET content=?, type=?, difficulty=?, knowledge_point=?, answer=?, update_time=? WHERE id=?";
                try (java.sql.PreparedStatement ps = connection.prepareStatement(update)) {
                    ps.setString(1, q.getContent()); ps.setString(2, q.getType()); ps.setString(3, q.getDifficulty()); ps.setString(4, q.getKnowledgePoint()); ps.setString(5, q.getAnswer());
                    ps.setTimestamp(6, q.getUpdateTime() != null ? Timestamp.valueOf(q.getUpdateTime()) : null); ps.setLong(7, q.getId());
                    if (ps.executeUpdate() > 0) return;
                }
                try (java.sql.Statement stmt = connection.createStatement()) {
                    stmt.execute("SET IDENTITY_INSERT dbo.question ON");
                    String insert = "INSERT INTO dbo.question (id, content, type, difficulty, knowledge_point, answer, update_time) VALUES (?, ?, ?, ?, ?, ?, ?)";
                    try (java.sql.PreparedStatement ps = connection.prepareStatement(insert)) {
                        ps.setLong(1, q.getId()); ps.setString(2, q.getContent()); ps.setString(3, q.getType()); ps.setString(4, q.getDifficulty()); ps.setString(5, q.getKnowledgePoint()); ps.setString(6, q.getAnswer());
                        ps.setTimestamp(7, q.getUpdateTime() != null ? Timestamp.valueOf(q.getUpdateTime()) : null);
                        ps.executeUpdate();
                    }
                    stmt.execute("SET IDENTITY_INSERT dbo.question OFF");
                }
            } catch (Exception e) { throw new RuntimeException(e.getMessage()); }
        });
    }
    @Transactional(transactionManager = "transactionManagerSqlServer")
    public void syncToSqlServerPaper(Paper p) {
        org.hibernate.Session session = sqlServerEm.unwrap(org.hibernate.Session.class);
        session.doWork(connection -> {
            try {
                String update = "UPDATE dbo.paper SET paper_name=?, total_score=?, teacher_id=?, create_time=?, update_time=? WHERE id=?";
                try (java.sql.PreparedStatement ps = connection.prepareStatement(update)) {
                    ps.setString(1, p.getPaperName()); ps.setObject(2, p.getTotalScore(), java.sql.Types.INTEGER); ps.setObject(3, p.getTeacher() != null ? p.getTeacher().getId() : null, java.sql.Types.BIGINT);
                    ps.setTimestamp(4, p.getCreateTime() != null ? Timestamp.valueOf(p.getCreateTime()) : null); ps.setTimestamp(5, p.getUpdateTime() != null ? Timestamp.valueOf(p.getUpdateTime()) : null); ps.setLong(6, p.getId());
                    if (ps.executeUpdate() > 0) return;
                }
                try (java.sql.Statement stmt = connection.createStatement()) {
                    stmt.execute("SET IDENTITY_INSERT dbo.paper ON");
                    String insert = "INSERT INTO dbo.paper (id, paper_name, total_score, teacher_id, create_time, update_time) VALUES (?, ?, ?, ?, ?, ?)";
                    try (java.sql.PreparedStatement ps = connection.prepareStatement(insert)) {
                        ps.setLong(1, p.getId()); ps.setString(2, p.getPaperName()); ps.setObject(3, p.getTotalScore(), java.sql.Types.INTEGER); ps.setObject(4, p.getTeacher() != null ? p.getTeacher().getId() : null, java.sql.Types.BIGINT);
                        ps.setTimestamp(5, p.getCreateTime() != null ? Timestamp.valueOf(p.getCreateTime()) : null); ps.setTimestamp(6, p.getUpdateTime() != null ? Timestamp.valueOf(p.getUpdateTime()) : null);
                        ps.executeUpdate();
                    }
                    stmt.execute("SET IDENTITY_INSERT dbo.paper OFF");
                }
            } catch (Exception e) { throw new RuntimeException(e.getMessage()); }
        });
    }
    @Transactional(transactionManager = "transactionManagerSqlServer")
    public void syncToSqlServerPaperQuestions(Paper p, List<PaperQuestion> pqs) {
        org.hibernate.Session session = sqlServerEm.unwrap(org.hibernate.Session.class);
        session.doWork(connection -> {
            try {
                try (java.sql.PreparedStatement psDel = connection.prepareStatement("DELETE FROM dbo.paper_question WHERE paper_id = ?")) {
                    psDel.setLong(1, p.getId()); psDel.executeUpdate();
                }
                String insert = "INSERT INTO dbo.paper_question (paper_id, question_id, score) VALUES (?, ?, ?)";
                try (java.sql.PreparedStatement ps = connection.prepareStatement(insert)) {
                    for (PaperQuestion pq : pqs) {
                        ps.setObject(1, p.getId(), java.sql.Types.BIGINT); ps.setObject(2, pq.getQuestion().getId(), java.sql.Types.BIGINT); ps.setObject(3, pq.getScore(), java.sql.Types.INTEGER); ps.addBatch();
                    }
                    ps.executeBatch();
                }
            } catch (Exception e) { throw new RuntimeException(e.getMessage()); }
        });
    }
    @Transactional(transactionManager = "transactionManagerSqlServer")
    public void syncToSqlServerResult(ExamResult r) {
        org.hibernate.Session session = sqlServerEm.unwrap(org.hibernate.Session.class);
        session.doWork(connection -> {
            try {
                // 【修复】增加了 student_answers=?
                String update = "UPDATE dbo.exam_result SET student_id=?, paper_id=?, score=?, exam_time=?, update_time=?, student_answers=? WHERE id=?";
                try (java.sql.PreparedStatement ps = connection.prepareStatement(update)) {
                    ps.setObject(1, r.getStudent() != null ? r.getStudent().getId() : null, java.sql.Types.BIGINT);
                    ps.setObject(2, r.getPaper() != null ? r.getPaper().getId() : null, java.sql.Types.BIGINT);
                    ps.setBigDecimal(3, r.getScore());
                    ps.setTimestamp(4, r.getCreateTime() != null ? Timestamp.valueOf(r.getCreateTime()) : null);
                    ps.setTimestamp(5, r.getUpdateTime() != null ? Timestamp.valueOf(r.getUpdateTime()) : null);
                    ps.setString(6, r.getStudentAnswers()); // 设置答题JSON
                    ps.setLong(7, r.getId());
                    if (ps.executeUpdate() > 0) return;
                }

                try (java.sql.Statement stmt = connection.createStatement()) {
                    stmt.execute("SET IDENTITY_INSERT dbo.exam_result ON");
                    // 【修复】增加了 student_answers
                    String insert = "INSERT INTO dbo.exam_result (id, student_id, paper_id, score, exam_time, update_time, student_answers) VALUES (?, ?, ?, ?, ?, ?, ?)";
                    try (java.sql.PreparedStatement ps = connection.prepareStatement(insert)) {
                        ps.setLong(1, r.getId());
                        ps.setObject(2, r.getStudent() != null ? r.getStudent().getId() : null, java.sql.Types.BIGINT);
                        ps.setObject(3, r.getPaper() != null ? r.getPaper().getId() : null, java.sql.Types.BIGINT);
                        ps.setBigDecimal(4, r.getScore());
                        ps.setTimestamp(5, r.getCreateTime() != null ? Timestamp.valueOf(r.getCreateTime()) : null);
                        ps.setTimestamp(6, r.getUpdateTime() != null ? Timestamp.valueOf(r.getUpdateTime()) : null);
                        ps.setString(7, r.getStudentAnswers()); // 设置答题JSON
                        ps.executeUpdate();
                    }
                    stmt.execute("SET IDENTITY_INSERT dbo.exam_result OFF");
                }
            } catch (Exception e) {
                throw new RuntimeException("SQL Server ExamResult同步失败: " + e.getMessage());
            }
        });
    }

    // MySQL
    @Transactional
    public void syncToMysqlNative(User u) {
        String updateSql = "UPDATE sys_user SET username=?1, password=?2, role=?3, real_name=?4, create_time=?5, update_time=?6 WHERE id=?7";
        int rows = mysqlEm.createNativeQuery(updateSql).setParameter(1, u.getUsername()).setParameter(2, u.getPassword()).setParameter(3, u.getRole()).setParameter(4, u.getRealName()).setParameter(5, u.getCreateTime()).setParameter(6, u.getUpdateTime()).setParameter(7, u.getId()).executeUpdate();
        if (rows == 0) {
            String insertSql = "INSERT INTO sys_user (id, username, password, role, real_name, create_time, update_time) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)";
            mysqlEm.createNativeQuery(insertSql).setParameter(1, u.getId()).setParameter(2, u.getUsername()).setParameter(3, u.getPassword()).setParameter(4, u.getRole()).setParameter(5, u.getRealName()).setParameter(6, u.getCreateTime()).setParameter(7, u.getUpdateTime()).executeUpdate();
        }
    }
    @Transactional
    public void syncToMysqlQuestion(Question q) {
        String update = "UPDATE question SET content=?1, type=?2, difficulty=?3, knowledge_point=?4, answer=?5, update_time=?6 WHERE id=?7";
        int rows = mysqlEm.createNativeQuery(update).setParameter(1, q.getContent()).setParameter(2, q.getType()).setParameter(3, q.getDifficulty()).setParameter(4, q.getKnowledgePoint()).setParameter(5, q.getAnswer()).setParameter(6, q.getUpdateTime()).setParameter(7, q.getId()).executeUpdate();
        if (rows == 0) {
            String insert = "INSERT INTO question (id, content, type, difficulty, knowledge_point, answer, update_time) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)";
            mysqlEm.createNativeQuery(insert).setParameter(1, q.getId()).setParameter(2, q.getContent()).setParameter(3, q.getType()).setParameter(4, q.getDifficulty()).setParameter(5, q.getKnowledgePoint()).setParameter(6, q.getAnswer()).setParameter(7, q.getUpdateTime()).executeUpdate();
        }
    }
    @Transactional
    public void syncToMysqlPaper(Paper p) {
        String update = "UPDATE paper SET paper_name=?1, total_score=?2, teacher_id=?3, create_time=?4, update_time=?5 WHERE id=?6";
        int rows = mysqlEm.createNativeQuery(update).setParameter(1, p.getPaperName()).setParameter(2, p.getTotalScore()).setParameter(3, p.getTeacher() != null ? p.getTeacher().getId() : null).setParameter(4, p.getCreateTime()).setParameter(5, p.getUpdateTime()).setParameter(6, p.getId()).executeUpdate();
        if (rows == 0) {
            String insert = "INSERT INTO paper (id, paper_name, total_score, teacher_id, create_time, update_time) VALUES (?1, ?2, ?3, ?4, ?5, ?6)";
            mysqlEm.createNativeQuery(insert).setParameter(1, p.getId()).setParameter(2, p.getPaperName()).setParameter(3, p.getTotalScore()).setParameter(4, p.getTeacher() != null ? p.getTeacher().getId() : null).setParameter(5, p.getCreateTime()).setParameter(6, p.getUpdateTime()).executeUpdate();
        }
    }
    @Transactional
    public void syncToMysqlPaperQuestions(Paper p, List<PaperQuestion> pqs) {
        mysqlEm.createNativeQuery("DELETE FROM paper_question WHERE paper_id = ?1").setParameter(1, p.getId()).executeUpdate();
        for (PaperQuestion pq : pqs) {
            mysqlEm.createNativeQuery("INSERT INTO paper_question (paper_id, question_id, score) VALUES (?1, ?2, ?3)").setParameter(1, p.getId()).setParameter(2, pq.getQuestion().getId()).setParameter(3, pq.getScore()).executeUpdate();
        }
    }
    @Transactional
    public void syncToMysqlResult(ExamResult r) {
        Long sid = r.getStudent() != null ? r.getStudent().getId() : null;
        Long pid = r.getPaper() != null ? r.getPaper().getId() : null;

        // 【修复】增加了 student_answers=?6
        String update = "UPDATE exam_result SET student_id=?1, paper_id=?2, score=?3, exam_time=?4, update_time=?5, student_answers=?6 WHERE id=?7";
        int rows = mysqlEm.createNativeQuery(update)
                .setParameter(1, sid).setParameter(2, pid).setParameter(3, r.getScore())
                .setParameter(4, r.getCreateTime()).setParameter(5, r.getUpdateTime())
                .setParameter(6, r.getStudentAnswers()) // 设置答题JSON
                .setParameter(7, r.getId()).executeUpdate();

        if (rows == 0) {
            // 【修复】增加了 student_answers 字段
            String insert = "INSERT INTO exam_result (id, student_id, paper_id, score, exam_time, update_time, student_answers) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)";
            mysqlEm.createNativeQuery(insert)
                    .setParameter(1, r.getId()).setParameter(2, sid).setParameter(3, pid)
                    .setParameter(4, r.getScore()).setParameter(5, r.getCreateTime())
                    .setParameter(6, r.getUpdateTime())
                    .setParameter(7, r.getStudentAnswers()) // 设置答题JSON
                    .executeUpdate();
        }
    }

    // Manual Fix
    @Transactional(transactionManager = "transactionManagerOracle")
    public void syncSinglePaperToOracle(Long paperId) {
        Paper p = mysqlPaperRepo.findById(paperId).orElse(null);
        if (p != null) {
            self.syncToOraclePaper(p);
            List<PaperQuestion> pqs = mysqlPaperQuestionRepo.findAll().stream().filter(pq -> pq.getPaper().getId().equals(paperId)).collect(Collectors.toList());
            self.syncToOraclePaperQuestions(p, pqs);
        }
    }
    @Transactional(transactionManager = "transactionManagerSqlServer")
    public void syncSinglePaperToSqlServer(Long paperId) {
        Paper p = mysqlPaperRepo.findById(paperId).orElse(null);
        if (p != null) {
            self.syncToSqlServerPaper(p);
            List<PaperQuestion> pqs = mysqlPaperQuestionRepo.findAll().stream().filter(pq -> pq.getPaper().getId().equals(paperId)).collect(Collectors.toList());
            self.syncToSqlServerPaperQuestions(p, pqs);
        }
    }

    // Delete Globally
    @Transactional
    public void deletePaperGlobally(Long paperId) {
        List<ExamResult> results = mysqlExamResultRepo.findAll().stream().filter(r -> r.getPaper() != null && r.getPaper().getId().equals(paperId)).collect(Collectors.toList());
        if (!results.isEmpty()) mysqlExamResultRepo.deleteAll(results);
        mysqlPaperQuestionRepo.deleteByPaperId(paperId);
        mysqlPaperRepo.deleteById(paperId);
        self.deletePaperOracle(paperId);
        self.deletePaperSqlServer(paperId);
    }
    @Transactional(transactionManager = "transactionManagerOracle")
    public void deletePaperOracle(Long paperId) {
        oracleEm.createNativeQuery("DELETE FROM exam_result WHERE paper_id = ?1").setParameter(1, paperId).executeUpdate();
        oracleEm.createNativeQuery("DELETE FROM paper_question WHERE paper_id = ?1").setParameter(1, paperId).executeUpdate();
        oracleEm.createNativeQuery("DELETE FROM paper WHERE id = ?1").setParameter(1, paperId).executeUpdate();
    }
    @Transactional(transactionManager = "transactionManagerSqlServer")
    public void deletePaperSqlServer(Long paperId) {
        org.hibernate.Session session = sqlServerEm.unwrap(org.hibernate.Session.class);
        session.doWork(connection -> {
            try (java.sql.Statement stmt = connection.createStatement()) {
                try (java.sql.PreparedStatement ps = connection.prepareStatement("DELETE FROM dbo.exam_result WHERE paper_id = ?")) { ps.setLong(1, paperId); ps.executeUpdate(); }
                try (java.sql.PreparedStatement ps = connection.prepareStatement("DELETE FROM dbo.paper_question WHERE paper_id = ?")) { ps.setLong(1, paperId); ps.executeUpdate(); }
                try (java.sql.PreparedStatement ps = connection.prepareStatement("DELETE FROM dbo.paper WHERE id = ?")) { ps.setLong(1, paperId); ps.executeUpdate(); }
            } catch (Exception e) { throw new RuntimeException(e.getMessage()); }
        });
    }
    @Transactional
    public void deleteUserGlobally(Long userId) {
        mysqlExamResultRepo.deleteByStudentId(userId);
        if (mysqlUserRepo.existsById(userId)) mysqlUserRepo.deleteById(userId);
        self.deleteUserOracle(userId);
        self.deleteUserSqlServer(userId);
    }
    @Transactional(transactionManager = "transactionManagerOracle")
    public void deleteUserOracle(Long userId) {
        oracleEm.createNativeQuery("DELETE FROM exam_result WHERE student_id = ?1").setParameter(1, userId).executeUpdate();
        oracleEm.createNativeQuery("UPDATE paper SET teacher_id = NULL WHERE teacher_id = ?1").setParameter(1, userId).executeUpdate();
        oracleEm.createNativeQuery("DELETE FROM sys_user WHERE id = ?1").setParameter(1, userId).executeUpdate();
    }
    @Transactional(transactionManager = "transactionManagerSqlServer")
    public void deleteUserSqlServer(Long userId) {
        org.hibernate.Session session = sqlServerEm.unwrap(org.hibernate.Session.class);
        session.doWork(connection -> {
            try (java.sql.Statement stmt = connection.createStatement()) {
                try (java.sql.PreparedStatement ps = connection.prepareStatement("DELETE FROM dbo.exam_result WHERE student_id = ?")) { ps.setLong(1, userId); ps.executeUpdate(); }
                try (java.sql.PreparedStatement ps = connection.prepareStatement("UPDATE dbo.paper SET teacher_id = NULL WHERE teacher_id = ?")) { ps.setLong(1, userId); ps.executeUpdate(); }
                try (java.sql.PreparedStatement ps = connection.prepareStatement("DELETE FROM dbo.sys_user WHERE id = ?")) { ps.setLong(1, userId); ps.executeUpdate(); }
            } catch (Exception e) { throw new RuntimeException(e.getMessage()); }
        });
    }
    @Transactional
    public void deleteQuestionGlobally(Long questionId) {
        List<PaperQuestion> pqs = mysqlPaperQuestionRepo.findAll().stream().filter(pq -> pq.getQuestion().getId().equals(questionId)).collect(Collectors.toList());
        if (!pqs.isEmpty()) mysqlPaperQuestionRepo.deleteAll(pqs);
        if (mysqlQuestionRepo.existsById(questionId)) mysqlQuestionRepo.deleteById(questionId);
        self.deleteQuestionOracle(questionId);
        self.deleteQuestionSqlServer(questionId);
    }
    @Transactional(transactionManager = "transactionManagerOracle")
    public void deleteQuestionOracle(Long questionId) {
        oracleEm.createNativeQuery("DELETE FROM paper_question WHERE question_id = ?1").setParameter(1, questionId).executeUpdate();
        oracleEm.createNativeQuery("DELETE FROM question WHERE id = ?1").setParameter(1, questionId).executeUpdate();
    }
    @Transactional(transactionManager = "transactionManagerSqlServer")
    public void deleteQuestionSqlServer(Long questionId) {
        org.hibernate.Session session = sqlServerEm.unwrap(org.hibernate.Session.class);
        session.doWork(connection -> {
            try (java.sql.Statement stmt = connection.createStatement()) {
                try (java.sql.PreparedStatement ps = connection.prepareStatement("DELETE FROM dbo.paper_question WHERE question_id = ?")) { ps.setLong(1, questionId); ps.executeUpdate(); }
                try (java.sql.PreparedStatement ps = connection.prepareStatement("DELETE FROM dbo.question WHERE id = ?")) { ps.setLong(1, questionId); ps.executeUpdate(); }
            } catch (Exception e) { throw new RuntimeException(e.getMessage()); }
        });
    }
}