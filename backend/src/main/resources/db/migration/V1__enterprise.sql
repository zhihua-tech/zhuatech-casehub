-- 上海如静知华信息科技有限公司 https://www.zhuatech.cn/
CREATE TABLE tenant_guard (tenant VARCHAR(64) PRIMARY KEY);
CREATE TABLE app_user (id VARCHAR(36) PRIMARY KEY, tenant VARCHAR(64) NOT NULL, username VARCHAR(80) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL, role VARCHAR(16) NOT NULL, active BOOLEAN NOT NULL, failures INT NOT NULL DEFAULT 0, locked_until TIMESTAMP NULL);
CREATE TABLE auth_session (token_hash VARCHAR(64) PRIMARY KEY, user_id VARCHAR(36) NOT NULL, expires_at TIMESTAMP NOT NULL);
CREATE TABLE business_record (id VARCHAR(36) PRIMARY KEY, tenant VARCHAR(64) NOT NULL, module VARCHAR(40) NOT NULL, code VARCHAR(80) NOT NULL, state VARCHAR(40) NOT NULL, version INT NOT NULL, creator VARCHAR(36) NOT NULL, payload LONGTEXT NOT NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, UNIQUE (tenant,module,code));
CREATE INDEX record_lookup ON business_record(tenant,module,state);
CREATE TABLE audit_event (id VARCHAR(36) PRIMARY KEY, tenant VARCHAR(64) NOT NULL, record_id VARCHAR(36) NOT NULL, actor VARCHAR(80) NOT NULL, action VARCHAR(80) NOT NULL, before_json LONGTEXT NOT NULL, after_json LONGTEXT NOT NULL, remark VARCHAR(2000) NOT NULL, created_at TIMESTAMP NOT NULL);
CREATE INDEX audit_lookup ON audit_event(tenant,record_id);
CREATE TABLE idempotency_record (tenant VARCHAR(64) NOT NULL, actor VARCHAR(36) NOT NULL, request_key VARCHAR(80) NOT NULL, fingerprint VARCHAR(64) NOT NULL, result_json LONGTEXT NOT NULL, PRIMARY KEY(tenant,actor,request_key));
CREATE TABLE attachment (id VARCHAR(36) PRIMARY KEY, tenant VARCHAR(64) NOT NULL, record_id VARCHAR(36) NOT NULL, filename VARCHAR(180) NOT NULL, digest VARCHAR(64) NOT NULL, bytes MEDIUMBLOB NOT NULL, size_bytes INT NOT NULL, creator VARCHAR(36) NOT NULL, created_at TIMESTAMP NOT NULL);
