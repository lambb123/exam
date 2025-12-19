-- =============================================
-- 1. 存储过程：统计某张试卷的详细情况 (MySQL)
-- 功能：计算平均分、最高分、最低分、及格率，并更新到 paper 表的备注字段(假设有)或返回结果
-- =============================================
DELIMITER //
CREATE PROCEDURE Proc_CalculatePaperStats(IN p_paper_id BIGINT, OUT p_avg_score DECIMAL(10,2), OUT p_pass_rate DECIMAL(10,2))
BEGIN
    DECLARE total_count INT DEFAULT 0;
    DECLARE pass_count INT DEFAULT 0;

    -- 1. 计算总人数和及格人数
SELECT COUNT(*), COUNT(CASE WHEN score >= 60 THEN 1 END)
INTO total_count, pass_count
FROM exam_result
WHERE paper_id = p_paper_id;

-- 2. 计算平均分
SELECT IFNULL(AVG(score), 0) INTO p_avg_score
FROM exam_result
WHERE paper_id = p_paper_id;

-- 3. 计算及格率
IF total_count > 0 THEN
        SET p_pass_rate = (pass_count / total_count) * 100;
ELSE
        SET p_pass_rate = 0;
END IF;

    -- 4. (可选) 将统计结果回写到试卷描述中，模拟复杂业务逻辑
    -- UPDATE paper SET description = CONCAT('平均分:', p_avg_score, ', 及格率:', p_pass_rate, '%') WHERE id = p_paper_id;
END //
DELIMITER ;

-- =============================================
-- 2. 触发器：用户表变更记录 (MySQL)
-- 功能：当 sys_user 表发生 UPDATE 时，自动插入一条日志到 sync_log 表
-- =============================================
DELIMITER //
CREATE TRIGGER Tri_AfterUserUpdate
    AFTER UPDATE ON sys_user
    FOR EACH ROW
BEGIN
    -- 只有当关键信息变更时才记录
    IF OLD.username != NEW.username OR OLD.role != NEW.role THEN
        INSERT INTO sync_log (create_time, start_time, end_time, status, message)
        VALUES (NOW(), NOW(), NOW(), 'TRIGGER', CONCAT('用户[', OLD.id, ']数据变更: ', OLD.username, ' -> ', NEW.username));
END IF;
END //
DELIMITER ;