-- 仅提供迁移脚本，需由运维在备份及确认目标库后执行；不回填应用计数。
-- 存量用户的 appLimit 保持 NULL，使用 app.quota.default-limit。
ALTER TABLE `user`
    ADD COLUMN appLimit INT NULL DEFAULT NULL COMMENT '应用配额覆盖值，NULL 使用系统默认值'
    AFTER userRole;

ALTER TABLE app
    ADD INDEX idx_userId_isDelete (userId, isDelete);
