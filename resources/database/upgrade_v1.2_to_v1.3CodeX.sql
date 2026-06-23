-- ragent v1.2 -> v1.3 升级脚本
-- t_knowledge_vector 表：新增 PostgreSQL 全文检索索引，用于关键词召回

CREATE INDEX IF NOT EXISTS idx_kv_content_fts
    ON t_knowledge_vector USING gin(to_tsvector('simple', coalesce(content, '')));
