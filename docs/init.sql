-- JNClub 数据库初始化脚本
-- 包含：目录 + 收藏 + 便签 + 图片审计表 + 云盘文件表

CREATE DATABASE IF NOT EXISTS jnclub DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE jnclub;

-- 目录表（type=1 收藏夹目录  type=2 便签目录  type=3 云盘目录）
CREATE TABLE IF NOT EXISTS t_directory (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT DEFAULT NULL COMMENT '父目录ID，NULL为一级目录',
  name VARCHAR(100) NOT NULL COMMENT '目录名称',
  icon VARCHAR(50) DEFAULT NULL COMMENT '目录图标（预设 key，未选默认文件夹）',
  type INT DEFAULT 1 COMMENT '目录类型：1=收藏夹  2=便签  3=云盘',
  sort_order INT DEFAULT 0 COMMENT '排序序号',
  user_id VARCHAR(64) NOT NULL COMMENT 'SSO用户标识',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user_id (user_id),
  INDEX idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='目录表';

-- 网页收藏表
CREATE TABLE IF NOT EXISTS t_bookmark (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL COMMENT '标题',
  url VARCHAR(2048) NOT NULL COMMENT '网址',
  icon VARCHAR(2048) DEFAULT NULL COMMENT 'favicon地址',
  directory_id BIGINT NOT NULL COMMENT '所属目录ID',
  user_id VARCHAR(64) NOT NULL COMMENT 'SSO用户标识',
  sort_order INT DEFAULT 0 COMMENT '排序序号',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_directory (directory_id),
  INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网页收藏表';

-- 便签表
CREATE TABLE IF NOT EXISTS t_note (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(200) DEFAULT '' COMMENT '标题（可为空，由内容派生）',
  content MEDIUMTEXT COMMENT 'Markdown原文',
  directory_id BIGINT NOT NULL COMMENT '所属目录ID',
  user_id VARCHAR(64) NOT NULL COMMENT 'SSO用户标识',
  sort_order INT DEFAULT 0 COMMENT '排序序号',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_directory (directory_id),
  INDEX idx_user_id (user_id),
  INDEX idx_folder_order (directory_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='便签表';

-- 图片资源审计表
CREATE TABLE IF NOT EXISTS t_note_asset (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(64) NOT NULL COMMENT 'SSO用户标识',
  original_name VARCHAR(500) COMMENT '原始文件名',
  stored_key VARCHAR(500) COMMENT 'dufs 相对路径',
  url VARCHAR(2048) COMMENT '完整公网 URL',
  size BIGINT DEFAULT 0 COMMENT '文件大小(字节)',
  mime VARCHAR(100) COMMENT 'MIME 类型',
  note_id BIGINT DEFAULT NULL COMMENT '关联便签ID，保存时认领，NULL=上传未保存',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_user_id (user_id),
  INDEX idx_note_id (note_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片资源审计表';

-- 云盘文件表（目录复用 t_directory，type=3）
CREATE TABLE IF NOT EXISTS t_file (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  directory_id BIGINT NOT NULL COMMENT '所属云盘目录ID（复用 t_directory，type=3）',
  user_id VARCHAR(64) NOT NULL COMMENT 'SSO用户标识',
  original_name VARCHAR(500) NOT NULL COMMENT '原始文件名',
  stored_key VARCHAR(500) NOT NULL COMMENT 'dufs 相对路径，如 /jnclub/disk/yyyy/MM/dd/uuid.ext',
  url VARCHAR(2048) NOT NULL COMMENT '公网访问路径，如 /api/files/...',
  size BIGINT DEFAULT 0 COMMENT '文件大小(字节)',
  mime VARCHAR(100) COMMENT 'MIME 类型',
  sort_order INT DEFAULT 0 COMMENT '排序序号（同一目录内拖拽排序）',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_directory (directory_id),
  INDEX idx_user_id (user_id),
  INDEX idx_file_sort (directory_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='云盘文件表';

-- 用户偏好表（通用 KV，JSON 值；模块/视图/目录记忆等复用）
CREATE TABLE IF NOT EXISTS t_user_preference (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(64) NOT NULL COMMENT 'SSO用户标识',
  pref_key VARCHAR(100) NOT NULL COMMENT '偏好键（模块.场景，如 module.activeModule、dir.notes）',
  pref_value TEXT NOT NULL COMMENT '偏好值（JSON 字符串）',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_user_pref (user_id, pref_key),
  INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户偏好表（通用KV）';

-- ==========================================
-- 迁移脚本：已有数据库增量变更
-- ==========================================
-- ⚠️ 注意：以下迁移在 MySQL 8.0 上执行，不支持 `ADD COLUMN IF NOT EXISTS`/`ADD INDEX IF NOT EXISTS`（MariaDB 语法）。
-- 使用前需先确认目标列/索引不存在；或改用存储过程判断，否则会报 ERROR 1060 Duplicate column / ERROR 1061 Duplicate key。
-- ALTER TABLE t_directory ADD COLUMN type INT DEFAULT 1 COMMENT '目录类型：1=收藏夹  2=便签' AFTER name;
-- ALTER TABLE t_note MODIFY COLUMN title VARCHAR(200) DEFAULT '' COMMENT '标题（可为空，由内容派生）';
-- ALTER TABLE t_note MODIFY COLUMN content MEDIUMTEXT COMMENT 'Markdown原文';
-- ALTER TABLE t_note_asset ADD COLUMN note_id BIGINT DEFAULT NULL COMMENT '关联便签ID' AFTER mime;
-- ALTER TABLE t_note_asset ADD INDEX idx_note_id (note_id);
-- ALTER TABLE t_directory ADD COLUMN icon VARCHAR(50) DEFAULT NULL COMMENT '目录图标（预设 key）' AFTER name;
-- CREATE TABLE IF NOT EXISTS t_user_preference (
--   id BIGINT PRIMARY KEY AUTO_INCREMENT,
--   user_id VARCHAR(64) NOT NULL COMMENT 'SSO用户标识',
--   pref_key VARCHAR(100) NOT NULL COMMENT '偏好键（模块.场景）',
--   pref_value TEXT NOT NULL COMMENT '偏好值（JSON 字符串）',
--   create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
--   update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
--   UNIQUE KEY uk_user_pref (user_id, pref_key),
--   INDEX idx_user_id (user_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户偏好表';
-- -- 云盘（小云盘）迁移：新建 t_file 表
-- CREATE TABLE IF NOT EXISTS t_file (
--   id BIGINT PRIMARY KEY AUTO_INCREMENT,
--   directory_id BIGINT NOT NULL COMMENT '所属云盘目录ID（复用 t_directory，type=3）',
--   user_id VARCHAR(64) NOT NULL COMMENT 'SSO用户标识',
--   original_name VARCHAR(500) NOT NULL COMMENT '原始文件名',
--   stored_key VARCHAR(500) NOT NULL COMMENT 'dufs 相对路径',
--   url VARCHAR(2048) NOT NULL COMMENT '公网访问路径',
--   size BIGINT DEFAULT 0 COMMENT '文件大小(字节)',
--   mime VARCHAR(100) COMMENT 'MIME 类型',
--   create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
--   INDEX idx_directory (directory_id),
--   INDEX idx_user_id (user_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='云盘文件表';

-- ==========================================
-- P0 迁移：回收站软删除 + 标签系统（2026-08-10）
-- ⚠️ 生产库已有数据，需手工执行以下语句（MySQL 8.0 不支持 IF NOT EXISTS，执行前确认列/表不存在）：
-- ALTER TABLE t_bookmark ADD COLUMN deleted TINYINT DEFAULT 0 NOT NULL COMMENT '软删除标记：0正常 1回收站' AFTER create_time;
-- ALTER TABLE t_note ADD COLUMN deleted TINYINT DEFAULT 0 NOT NULL COMMENT '软删除标记：0正常 1回收站' AFTER update_time;
-- ALTER TABLE t_file ADD COLUMN deleted TINYINT DEFAULT 0 NOT NULL COMMENT '软删除标记：0正常 1回收站' AFTER create_time;
-- ALTER TABLE t_bookmark ADD INDEX idx_bookmark_deleted (user_id, deleted);
-- ALTER TABLE t_note ADD INDEX idx_note_deleted (user_id, deleted);
-- ALTER TABLE t_file ADD INDEX idx_file_deleted (user_id, deleted);
-- CREATE TABLE IF NOT EXISTS t_tag (
--   id BIGINT PRIMARY KEY AUTO_INCREMENT,
--   user_id VARCHAR(64) NOT NULL COMMENT 'SSO用户标识',
--   name VARCHAR(50) NOT NULL COMMENT '标签名',
--   create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
--   UNIQUE KEY uk_tag_user_name (user_id, name),
--   INDEX idx_tag_user (user_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签表';
-- CREATE TABLE IF NOT EXISTS t_tag_relation (
--   id BIGINT PRIMARY KEY AUTO_INCREMENT,
--   tag_id BIGINT NOT NULL COMMENT '标签ID',
--   ref_type VARCHAR(20) NOT NULL COMMENT '关联类型：bookmark=收藏 note=便签',
--   ref_id BIGINT NOT NULL COMMENT '关联记录ID（t_bookmark.id / t_note.id）',
--   create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
--   INDEX idx_tag (tag_id),
--   INDEX idx_ref (ref_type, ref_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签关联表（多对多）';

-- ==========================================
-- P1 迁移：密码库（2026-08-10）
-- ⚠️ 生产库已有数据，需手工执行（目录复用 t_directory type=5；密码字段 AES 密文存储）：
-- CREATE TABLE IF NOT EXISTS t_vault (
--   id BIGINT PRIMARY KEY AUTO_INCREMENT,
--   directory_id BIGINT NOT NULL COMMENT '所属密码库目录ID（复用 t_directory，type=5）',
--   user_id VARCHAR(64) NOT NULL COMMENT 'SSO用户标识',
--   name VARCHAR(200) NOT NULL COMMENT '条目名称',
--   username VARCHAR(200) DEFAULT '' COMMENT '账号',
--   password TEXT COMMENT '密码（AES 密文）',
--   url VARCHAR(2048) DEFAULT '' COMMENT '站点地址',
--   notes TEXT COMMENT '备注',
--   sort_order INT DEFAULT 0 COMMENT '排序序号（同一目录内拖拽排序）',
--   deleted TINYINT DEFAULT 0 NOT NULL COMMENT '软删除标记：0正常 1回收站',
--   create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
--   update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
--   INDEX idx_directory (directory_id),
--   INDEX idx_user (user_id, deleted)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='密码库表';

-- P2 迁移：密码库主密钥 + 健康检查（2026-08-11）
-- ⚠️ 生产库需手工执行：
-- 1) 密码指纹列（健康检查：同用户重复密码检测，SHA-256 指纹不解密可比）：
-- ALTER TABLE t_vault ADD COLUMN password_fingerprint VARCHAR(64) DEFAULT NULL COMMENT '密码SHA-256指纹' AFTER password;
-- 2) 主密钥元数据表（只存 KDF 参数与校验密文，密钥本身永不落库）：
-- CREATE TABLE IF NOT EXISTS t_vault_meta (
--   user_id VARCHAR(64) PRIMARY KEY COMMENT 'SSO用户标识',
--   salt VARCHAR(64) NOT NULL COMMENT 'PBKDF2 盐（Base64）',
--   iterations INT NOT NULL DEFAULT 100000 COMMENT 'PBKDF2 迭代次数',
--   key_version INT NOT NULL DEFAULT 1 COMMENT '密钥版本',
--   kdf VARCHAR(20) NOT NULL DEFAULT 'PBKDF2-SHA256' COMMENT 'KDF 算法',
--   key_check TEXT NOT NULL COMMENT '派生密钥加密的校验密文（Hex），解锁时校验输入',
--   update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='密码库主密钥元数据';

-- ==========================================
-- P3 迁移：Sa-Token 客户端会话持久化（解决后端重启丢登录态，仿 JN_SSO sa_token_data）
-- ⚠️ 生产库需手工执行：
CREATE TABLE IF NOT EXISTS sa_token_data (
  id       VARCHAR(255) NOT NULL COMMENT 'key（login:token:/login:session: 等前缀）',
  value    LONGTEXT     NOT NULL COMMENT 'AES-GCM 加密后的 Java 序列化值（base64）',
  sa_type  VARCHAR(50)  DEFAULT NULL COMMENT '数据类型：String / Object',
  expire   BIGINT       DEFAULT 0 COMMENT '过期毫秒时间戳，0 表示永不过期',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Sa-Token 客户端会话持久化表';

-- ==========================================
-- P4 迁移：WebDAV 站点管理（2026-09）
-- ⚠️ 一般无需手工执行：后端 WebDavTableInit 启动时幂等自建本表。
-- 生产库手工执行示例：
-- CREATE TABLE IF NOT EXISTS t_webdav_server (
--   id BIGINT PRIMARY KEY AUTO_INCREMENT,
--   user_id VARCHAR(64) NOT NULL COMMENT 'SSO用户标识',
--   name VARCHAR(200) DEFAULT '' COMMENT '站点名称',
--   url VARCHAR(1024) NOT NULL COMMENT 'WebDAV 服务地址',
--   username VARCHAR(300) DEFAULT '' COMMENT '登录账号，可为空(匿名)',
--   password VARCHAR(1024) DEFAULT NULL COMMENT '密码(AES密文)',
--   notes VARCHAR(1000) DEFAULT '' COMMENT '备注',
--   create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
--   update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
--   INDEX idx_user (user_id)
-- ) ENGINE=InnoDB DEFAULT
 CHARSET=utf8mb4 COMMENT='WebDAV 站点管理表';
