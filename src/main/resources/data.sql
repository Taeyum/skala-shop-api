-- SPEC.md 5절 E2E 시나리오가 요구하는 시드 상품 3종
--
-- ON CONFLICT DO NOTHING은 **지금은 아무 일도 하지 않는다.**
-- 현재 ddl-auto=create가 매 기동마다 테이블을 drop·create하므로 시드는 항상 빈 테이블에 들어간다
-- (2회 기동 후에도 3행 유지, id 1~3으로 재시작하는 것을 확인).
--
-- 넣어둔 이유는 ddl-auto를 validate로 전환할 경우를 대비해서다. 그때는 스키마가 유지되는데
-- sql.init.mode=always는 그대로라 재기동마다 같은 INSERT가 다시 돌아 중복 삽입된다.
-- 전제: product_name에 UNIQUE 제약이 있어야 충돌이 감지된다 (SPEC.md 4절 DATA_DUPLICATED)
--
-- created_at·updated_at을 직접 넣는다. JPA Auditing은 **엔티티를 거칠 때만** 동작하고
-- 순수 SQL INSERT는 그 경로를 타지 않는다. NOT NULL 제약이라 빠지면 기동이 실패한다
-- (실제로 Phase 5에서 겪었다 — ScriptStatementFailedException으로 앱이 뜨지 못했다).
INSERT INTO products (product_name, product_price, created_at, updated_at)
VALUES ('무선마우스', 15000.00, NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO products (product_name, product_price, created_at, updated_at)
VALUES ('블루투스키보드', 29000.00, NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO products (product_name, product_price, created_at, updated_at)
VALUES ('USB허브', 39000.00, NOW(), NOW()) ON CONFLICT DO NOTHING;
