"""Database access and parameterized SQL for provenance backfill."""
import sys

ALLOWED_DB_HOSTS = {'127.0.0.1', 'localhost', '172.16.38.78', 'winbid-01.test.rds.ehsy.com'}
UPDATE_SQL = """
    UPDATE knowledge_case
    SET source_section_title = %s, source_section_path = %s,
        source_document_name = %s, source_page_number = %s,
        source_section_index = %s
    WHERE id = %s
"""


def guard_db_host(db_host):
    if db_host not in ALLOWED_DB_HOSTS:
        sys.exit(f'拒绝连接：--db-host "{db_host}" 不在允许清单 {sorted(ALLOWED_DB_HOSTS)} 内。')


def load_cases(conn):
    with conn.cursor() as cursor:
        cursor.execute("""
            SELECT id, response_text, scoring_point_title, obs_bid_file_key
            FROM knowledge_case
            WHERE source_section_title IS NULL
              AND response_text IS NOT NULL AND response_text != ''
        """)
        return [{'id': row[0], 'response_text': row[1], 'scoring_point_title': row[2],
                 'obs_bid_file_key': row[3]} for row in cursor.fetchall()]


def connect(args):
    import pymysql
    guard_db_host(args.db_host)
    return pymysql.connect(host=args.db_host, port=args.db_port, user=args.db_user,
                           password=args.db_pass, database=args.db_name, charset='utf8mb4')
