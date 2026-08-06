-- SPEC.md 5절 E2E 시나리오가 요구하는 시드 상품 3종
--
-- ON CONFLICT DO NOTHING — ddl-auto를 validate로 바꾸면 스키마가 유지되므로
-- sql.init.mode=always와 만나 재기동마다 중복 삽입된다. 그때를 대비한 멱등 처리.
-- (Phase 0에서 product_name에 UNIQUE 제약이 붙는 것을 전제로 한다 — SPEC.md 4절 DATA_DUPLICATED)
INSERT INTO products (product_name, product_price) VALUES ('무선마우스', 15000) ON CONFLICT DO NOTHING;
INSERT INTO products (product_name, product_price) VALUES ('블루투스키보드', 29000) ON CONFLICT DO NOTHING;
INSERT INTO products (product_name, product_price) VALUES ('USB허브', 39000) ON CONFLICT DO NOTHING;
