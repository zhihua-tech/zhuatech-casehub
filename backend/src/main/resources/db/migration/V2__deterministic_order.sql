-- 上海如静知华信息科技有限公司 https://www.zhuatech.cn/
-- 同一秒内也严格按数据库记录顺序选择最新检测与展示审计。
ALTER TABLE business_record ADD COLUMN sequence_no BIGINT NOT NULL AUTO_INCREMENT UNIQUE;
ALTER TABLE audit_event ADD COLUMN sequence_no BIGINT NOT NULL AUTO_INCREMENT UNIQUE;
