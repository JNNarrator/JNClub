-- JNClub 数据库初始化脚本
-- 包含：目录 + 收藏 + 便签 + 图片审计表

CREATE DATABASE IF NOT EXISTS jnclub DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE jnclub;

-- 目录表（type=1 收藏夹目录  type=2 便签目录）
CREATE TABLE IF NOT EXISTS t_directory (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT DEFAULT NULL COMMENT '父目录ID，NULL为一级目录',
  name VARCHAR(100) NOT NULL COMMENT '目录名称',
  type INT DEFAULT 1 COMMENT '目录类型：1=收藏夹  2=便签',
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

-- ==========================================
-- 迁移脚本：已有数据库增量变更
-- ==========================================
-- ALTER TABLE t_directory ADD COLUMN IF NOT EXISTS type INT DEFAULT 1 COMMENT '目录类型：1=收藏夹  2=便签' AFTER name;
-- ALTER TABLE t_note MODIFY COLUMN title VARCHAR(200) DEFAULT '' COMMENT '标题（可为空，由内容派生）';
-- ALTER TABLE t_note MODIFY COLUMN content MEDIUMTEXT COMMENT 'Markdown原文';
-- ALTER TABLE t_note_asset ADD COLUMN IF NOT EXISTS note_id BIGINT DEFAULT NULL COMMENT '关联便签ID' AFTER mime;
-- ALTER TABLE t_note_asset ADD INDEX IF NOT EXISTS idx_note_id (note_id);
