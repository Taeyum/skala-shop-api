#!/usr/bin/env python3
"""
변이 테스트 하네스 — 운영 코드를 일부러 깨뜨려 **테스트가 실제로 실패하는지** 확인한다.

    "실패할 수 없는 테스트는 없는 테스트보다 나쁘다."

Phase 4~6에서 이 방식으로 잡아낸 것들:
  · SessionHandler 커버리지 93%인데 위조 토큰 수용 회귀가 테스트 104건을 통과했다
  · 500 응답의 예외 메시지 유출, ParameterException 400→500 매핑이 무방비였다
  · @WebMvcTest 슬라이스에 대한 내 주석이 사실과 반대였다

사용법
    python3 docs/verify/mutate.py <세트> [세트...]
    python3 docs/verify/mutate.py domain layer arch exception
    python3 docs/verify/mutate.py --list

★ 안전망 — 이 도구는 운영 코드를 변형한다
  Phase 4에서 이 스크립트의 이전 판이 타임아웃 SIGKILL로 종료되며
  SessionHandler·CustomerService에 **보안을 약화시키는 변이를 남겼다.**
  finally 만으로는 부족하다(SIGKILL은 잡히지 않는다). 그래서:
    1) finally 로 원본 복원
    2) SIGTERM 핸들러에서도 복원
    3) 마지막에 `git checkout -- src/main/java` 로 프로세스 밖 안전망
    4) --batch 로 쪼개 오래 걸리지 않게 (기본 4건씩)
  실행 전 작업 트리가 clean 한지 확인한다 — dirty 하면 거부한다.
"""
import glob
import os
import signal
import subprocess
import sys
import xml.etree.ElementTree as ET

# 스크립트 위치에서 저장소 루트를 구한다 — 절대 경로를 박지 않는다
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, 'src/main/java/com/sk/skala/shopapi')

# 변이 정의: (설명, 파일(SRC 기준 상대경로), 찾을 것, 바꿀 것)
#   'arch' 세트는 3-튜플 (설명, 파일, 클래스 본문 끝에 삽입할 코드) 형태다
MUTATIONS = {
    'domain': [

 ("Customer.usePoint 경계 < 0 → <= 0",
  "customer/entity/Customer.java",
  "if (customerPoint.compareTo(amount) < 0) {", "if (customerPoint.compareTo(amount) <= 0) {"),
 ("Customer.refundPoint 음수 검사 제거",
  "customer/entity/Customer.java",
  "\tpublic void refundPoint(BigDecimal amount) {\n\t\trequirePositive(amount);",
  "\tpublic void refundPoint(BigDecimal amount) {"),
 ("Customer.changePoint 음수 검사 제거",
  "customer/entity/Customer.java",
  "\t\tif (customerPoint.compareTo(BigDecimal.ZERO) < 0) {\n\t\t\tthrow new ParameterException(\"customerPoint\");\n\t\t}\n", ""),
 ("OrderItem.cancel 반올림 DOWN → HALF_UP",
  "order/entity/OrderItem.java",
  "RoundingMode.DOWN", "RoundingMode.HALF_UP"),
 ("OrderItem.cancel 남은 총액을 차감 대신 재계산",
  "order/entity/OrderItem.java",
  "this.orderedAmount = this.orderedAmount.subtract(refund);",
  "this.orderedAmount = remain == 0 ? BigDecimal.ZERO : this.orderedAmount.multiply(BigDecimal.valueOf(remain)).divide(BigDecimal.valueOf(remain + quantity), 2, RoundingMode.DOWN);"),
 ("OrderItem.cancel 보유 초과 검사 제거",
  "order/entity/OrderItem.java",
  "\t\tif (this.quantity < quantity) {\n\t\t\tthrow new ResponseException(Error.INSUFFICIENT_QUANTITY);\n\t\t}\n", ""),
 ("OrderItem.addOrder 총액 누적 누락 (addQuantity처럼 동작)",
  "order/entity/OrderItem.java",
  "\t\tthis.orderedAmount = this.orderedAmount.add(amount);", ""),
 ("Product.assign compareTo → equals",
  "product/entity/Product.java",
  "|| productPrice.compareTo(BigDecimal.ZERO) <= 0) {", "|| productPrice.equals(BigDecimal.ZERO)) {"),
 ("StringUtil.isAnyEmpty isBlank → isEmpty",
  "global/tools/StringUtil.java",
  "value.isBlank()", "value.isEmpty()"),
    ],
    'layer': [

 ("로그인 실패를 404/401로 분리 (사용자 열거 부활)","customer/service/CustomerService.java",
  'throw new ResponseException(Error.NOT_AUTHENTICATED, "no such customerId");',
  'throw new ResponseException(Error.DATA_NOT_FOUND, "no such customerId");'),
 ("deleteCustomer에서 소유권 확인을 존재 확인 뒤로","customer/service/CustomerService.java",
  '\t\trequireOwner(loginCustomerId, request.getCustomerId());\n\t\tCustomer customer = findCustomer(request.getCustomerId());\n\t\ttry {',
  '\t\tCustomer customer = findCustomer(request.getCustomerId());\n\t\trequireOwner(loginCustomerId, request.getCustomerId());\n\t\ttry {'),
 ("주문에서 잔액 차감을 저장 뒤로","order/service/OrderService.java",
  '\t\tcustomer.usePoint(total);\n', ''),
 ("전량 취소 시 행 삭제하지 않음","order/service/OrderService.java",
  'if (item.isEmpty()) {\n\t\t\torderItemRepository.delete(item);\n\t\t} else {\n\t\t\torderItemRepository.save(item);\n\t\t}',
  'orderItemRepository.save(item);'),
 ("@EntityGraph 제거 (N+1 부활)","order/repository/OrderItemRepository.java",
  '\t@EntityGraph(attributePaths = {"product"})\n', ''),
 ("@Version 제거","customer/entity/Customer.java", '\t@Version\n\tprivate Long version;\n', '\tprivate Long version;\n'),
 ("낙관적 락 409 핸들러 제거","global/exception/GlobalExceptionHandler.java",
  '\t@ExceptionHandler(OptimisticLockingFailureException.class)', '\t@ExceptionHandler(java.io.IOError.class)'),
 ("깨진 JSON 400 핸들러 제거","global/exception/GlobalExceptionHandler.java",
  '\t@ExceptionHandler(HttpMessageNotReadableException.class)', '\t@ExceptionHandler(java.io.IOError.class)'),
 ("가입 요청의 @Valid 제거","customer/controller/CustomerController.java",
  'public Response<CustomerResponse> createCustomer(@Valid @RequestBody CustomerRequest request)',
  'public Response<CustomerResponse> createCustomer(@RequestBody CustomerRequest request)'),
 ("주문 요청의 @Valid 제거","customer/controller/CustomerController.java",
  'public Response<Void> placeOrder(@LoginCustomer String customerId,\n\t\t\t@Valid @RequestBody OrderRequest order)',
  'public Response<Void> placeOrder(@LoginCustomer String customerId,\n\t\t\t@RequestBody OrderRequest order)'),
 ("쿠키 HttpOnly 끄기","global/auth/SessionHandler.java", '.httpOnly(true)', '.httpOnly(false)'),
 ("에러 상태 매핑을 전부 200으로","global/exception/Error.java",
  'NOT_OWNER(HttpStatus.FORBIDDEN,', 'NOT_OWNER(HttpStatus.OK,'),
    ],
    'arch': [

 ("엔티티에 public setter 추가","customer/entity/Customer.java",
  "\tpublic void setCustomerPoint(java.math.BigDecimal p) { this.customerPoint = p; }\n"),
 ("엔티티에 public 필드 추가","product/entity/Product.java",
  "\tpublic String leaked;\n"),
 ("Controller가 Repository를 직접 참조","product/controller/ProductController.java",
  "\tprivate com.sk.skala.shopapi.product.repository.ProductRepository leak;\n"),
 ("Service가 HttpServletRequest에 의존","product/service/ProductService.java",
  "\tprivate jakarta.servlet.http.HttpServletRequest leak;\n"),
 ("Service가 Response를 반환","product/service/ProductService.java",
  "\tpublic com.sk.skala.shopapi.global.common.Response<Void> leak() { return com.sk.skala.shopapi.global.common.Response.success(); }\n"),
 ("Controller가 엔티티를 노출","product/controller/ProductController.java",
  "\tpublic com.sk.skala.shopapi.product.entity.Product leak() { return null; }\n"),
 ("customer가 order.repository를 직접 참조","customer/service/CustomerService.java",
  "\tprivate com.sk.skala.shopapi.order.repository.OrderItemRepository leak;\n"),
 ("customer.service가 order를 안다 (역방향)","customer/service/CustomerService.java",
  "\tprivate com.sk.skala.shopapi.order.service.OrderService leak;\n"),
 ("customer.controller에 order 의존 복원 (순환)","customer/controller/CustomerController.java",
  "\tprivate com.sk.skala.shopapi.order.service.OrderService leak;\n"),
 ("필드 주입 사용","product/service/ProductService.java",
  "\t@org.springframework.beans.factory.annotation.Autowired private String leak;\n"),
    ],
    'exception': [

 ("[핸들러] ResponseException 상태를 항상 200으로","global/exception/GlobalExceptionHandler.java",
  "return ResponseEntity.status(e.getError().getStatus())","return ResponseEntity.status(org.springframework.http.HttpStatus.OK)"),
 ("[핸들러] Bean Validation 메시지 형식 변경","global/exception/GlobalExceptionHandler.java",
  'Response.fail("invalid parameter: " + fields)','Response.fail("bad request")'),
 ("[핸들러] 깨진 JSON 메시지 변경","global/exception/GlobalExceptionHandler.java",
  'Response.fail("malformed request body")','Response.fail("oops")'),
 ("[핸들러] 낙관적 락 409 → 500","global/exception/GlobalExceptionHandler.java",
  "return ResponseEntity.status(Error.CONCURRENT_MODIFICATION.getStatus())",
  "return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)"),
 ("[핸들러] 500 응답에 예외 메시지 유출","global/exception/GlobalExceptionHandler.java",
  'Response.fail("internal server error (traceId: " + traceId + ")")',
  'Response.fail("internal server error: " + e.getMessage())'),
 ("[리졸버] resolveArgument가 인증 대신 null 반환","global/auth/LoginCustomerArgumentResolver.java",
  "return sessionHandler.getCustomerId();","return null;"),
 ("[리졸버] supportsParameter의 String 타입 검사 제거","global/auth/LoginCustomerArgumentResolver.java",
  "return parameter.hasParameterAnnotation(LoginCustomer.class)\n\t\t\t\t&& String.class.equals(parameter.getParameterType());",
  "return parameter.hasParameterAnnotation(LoginCustomer.class);"),
 ("[인증] 쿠키 없음을 NOT_OWNER(403)로","global/auth/SessionHandler.java",
  'throw new ResponseException(Error.NOT_AUTHENTICATED, "no access token");',
  'throw new ResponseException(Error.NOT_OWNER, "no access token");'),
 ("[고객] DATA_IN_USE → DATA_NOT_FOUND","customer/service/CustomerService.java",
  'throw new ResponseException(Error.DATA_IN_USE, "customer still holds ordered products");',
  'throw new ResponseException(Error.DATA_NOT_FOUND, "customer still holds ordered products");'),
 ("[고객] NOT_OWNER(403) → NOT_AUTHENTICATED(401)","customer/service/CustomerService.java",
  "throw new ResponseException(Error.NOT_OWNER,","throw new ResponseException(Error.NOT_AUTHENTICATED,"),
 ("[상품] DATA_IN_USE → DATA_NOT_FOUND","product/service/ProductService.java",
  'throw new ResponseException(Error.DATA_IN_USE, "product is ordered by customers");',
  'throw new ResponseException(Error.DATA_NOT_FOUND, "product is ordered by customers");'),
 ("[상품] 중복 상품명 DATA_DUPLICATED → 무시하고 저장","product/service/ProductService.java",
  'throw new ResponseException(Error.DATA_DUPLICATED, "Product name already exists");',
  '{ }'),
 ("[도메인] INSUFFICIENT_FUNDS → DATA_NOT_FOUND","customer/entity/Customer.java",
  "throw new ResponseException(Error.INSUFFICIENT_FUNDS);","throw new ResponseException(Error.DATA_NOT_FOUND);"),
 ("[도메인] INSUFFICIENT_QUANTITY → DATA_NOT_FOUND","order/entity/OrderItem.java",
  "throw new ResponseException(Error.INSUFFICIENT_QUANTITY);","throw new ResponseException(Error.DATA_NOT_FOUND);"),
 ("[도메인] ParameterException → ResponseException(500 경로)","product/entity/Product.java",
  'throw new ParameterException("productName, productPrice");',
  'throw new IllegalStateException("productName, productPrice");'),
    ],
}

# 세트별로 돌릴 테스트 (전체를 매번 돌리면 한 번에 70초가 넘는다)
TESTS = {
    'domain':    ['*CustomerTest', '*OrderItemTest', '*ProductTest', '*StringUtilTest'],
    'layer':     ['*ServiceTest', '*RepositoryTest', '*ControllerTest',
                  '*ShopScenarioTest', '*OrderQueryCountTest'],
    'arch':      ['*ArchitectureTest'],
    'exception': ['*ControllerTest', '*GlobalExceptionHandlerTest',
                  '*LoginCustomerArgumentResolverTest', '*SessionHandlerTest'],
}


def restore():
    subprocess.run(['git', 'checkout', '--', 'src/main/java'], cwd=ROOT, capture_output=True)


signal.signal(signal.SIGTERM, lambda *a: (restore(), sys.exit(1)))


def run_tests(patterns):
    args = ['./gradlew', 'test', '--console=plain', '-q']
    for p in patterns:
        args += ['--tests', p]
    result = subprocess.run(args, cwd=ROOT, capture_output=True, text=True)
    if 'error:' in result.stdout + result.stderr:
        return ['<컴파일 실패>']
    failures = []
    for path in glob.glob(os.path.join(ROOT, 'build/test-results/test/*.xml')):
        root = ET.parse(path).getroot()
        for case in root.iter('testcase'):
            if case.find('failure') is not None or case.find('error') is not None:
                failures.append(root.get('name').split('.')[-1] + '.' + case.get('name')[:30])
    return failures


def apply(mutation, source):
    """변이를 적용한 소스를 돌려준다. 적용할 수 없으면 None"""
    if len(mutation) == 3:                      # inject 모드 (arch)
        _, _, injected = mutation
        cut = source.rindex('}')
        return source[:cut] + injected + source[cut:]
    _, _, old, new = mutation                   # replace 모드
    return source.replace(old, new, 1) if old in source else None


def main(names):
    dirty = subprocess.run(['git', 'status', '--porcelain', 'src/main/java'],
                           cwd=ROOT, capture_output=True, text=True).stdout.strip()
    if dirty:
        print('작업 트리가 clean 하지 않다. 변이와 실제 수정이 섞이면 원복이 위험하다:')
        print(dirty)
        return 1

    caught = uncaught = 0
    try:
        for name in names:
            print(f"\n═══ {name} ({len(MUTATIONS[name])}건) ═══")
            print(f"{'변이':<46}{'잡힘':>8}  잡은 테스트")
            for mutation in MUTATIONS[name]:
                label, rel = mutation[0], mutation[1]
                path = os.path.join(SRC, rel)
                original = open(path).read()
                mutated = apply(mutation, original)
                if mutated is None:
                    print(f"{label:<46}{'패턴없음':>8}  (코드가 바뀌었다 — 변이 정의를 갱신할 것)")
                    continue
                open(path, 'w').write(mutated)
                try:
                    failures = run_tests(TESTS[name])
                finally:
                    open(path, 'w').write(original)
                unique = sorted(set(failures))
                if unique:
                    caught += 1
                else:
                    uncaught += 1
                mark = '✅' if unique else '❌ 통과'
                extra = f' 외 {len(unique) - 2}' if len(unique) > 2 else ''
                print(f"{label:<46}{mark:>8}  {', '.join(unique[:2])}{extra}")
                sys.stdout.flush()
    finally:
        restore()

    print(f"\n잡힘 {caught} / 못 잡음 {uncaught}")
    if uncaught:
        print('★ 못 잡은 변이가 가장 값진 기록이다. 그 경로에 검증이 없다는 뜻이다.')
    return 1 if uncaught else 0


if __name__ == '__main__':
    argv = sys.argv[1:]
    if not argv or argv[0] == '--list':
        for key, items in MUTATIONS.items():
            print(f"  {key:<10} {len(items):>2}건")
        sys.exit(0)
    unknown = [a for a in argv if a not in MUTATIONS]
    if unknown:
        print(f"모르는 세트: {unknown}. 가능한 값: {list(MUTATIONS)}")
        sys.exit(2)
    sys.exit(main(argv))
