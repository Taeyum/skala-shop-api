package com.sk.skala.shopapi.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * {@code CLAUDE.md} 절대 규칙과 {@code .claude/rules/layering.md}를 <b>테스트로 옮긴 것.</b>
 * <p>
 * 이 파일이 필요한 이유는 성실함이 두 번 실패했기 때문이다 —
 * ① `DECISIONS.md` 7절에 "실패하려면 시끄럽게 실패해야 한다"를 써두고 다음 Phase에서
 * fail-late 코드를 짰고, ② 검증 스크립트 헤더에 브레이스 확장 경고를 직접 적어두고
 * 같은 실수를 반복했다. 그때 실제로 막아준 것은 문서가 아니라 <b>인자 수를 검사하는 코드</b>였다.
 * <p>
 * 규칙은 지키자고 다짐하는 것이 아니라 <b>어길 수 없게 만드는 것</b>이다 (DECISIONS.md 15절).
 */
@AnalyzeClasses(
		packages = "com.sk.skala.shopapi",
		importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	// ── 엔티티 ────────────────────────────────────────────────────────────

	@ArchTest
	static final ArchRule 엔티티에_public_setter가_없다 =
			methods()
					.that().areDeclaredInClassesThat().resideInAPackage("..entity..")
					.and().arePublic()
					.should().haveNameNotStartingWith("set")
					.because("상태 변경은 의도가 드러나는 메서드로만 한다 (usePoint, addOrder). "
							+ "setter가 있으면 불변식을 우회할 수 있다");

	@ArchTest
	static final ArchRule 엔티티_필드는_외부에서_직접_바꿀_수_없다 =
			fields()
					.that().areDeclaredInClassesThat().resideInAPackage("..entity..")
					.and().areNotStatic()
					.should().notBePublic()
					.because("public 필드는 setter보다 더 심하게 캡슐화를 깬다");

	// ── 계층 ──────────────────────────────────────────────────────────────

	@ArchTest
	static final ArchRule Controller는_Repository를_직접_호출하지_않는다 =
			noClasses()
					.that().resideInAPackage("..controller..")
					.should().dependOnClassesThat().resideInAPackage("..repository..")
					.because("Controller가 하는 일은 요청 바인딩·검증·Service 호출·응답 포장뿐이다");

	@ArchTest
	static final ArchRule Service는_웹_타입에_의존하지_않는다 =
			noClasses()
					.that().resideInAPackage("..service..")
					.should().dependOnClassesThat().haveNameMatching(
							"jakarta\\.servlet\\..*|org\\.springframework\\.web\\..*")
					.because("웹 의존성이 있으면 순수 단위 테스트가 불가능하고, "
							+ "배치·스케줄러가 같은 로직을 재사용할 수 없으며, "
							+ "인증 방식 변경이 도메인 로직까지 번진다");

	@ArchTest
	static final ArchRule Service는_Response를_반환하지_않는다 =
			noClasses()
					.that().resideInAPackage("..service..")
					.should().dependOnClassesThat().haveFullyQualifiedName(
							"com.sk.skala.shopapi.global.common.Response")
					.because("Response는 HTTP 응답 표현이므로 감싸는 것은 Controller의 몫이다");

	@ArchTest
	static final ArchRule 엔티티는_Controller에_노출되지_않는다 =
			noClasses()
					.that().resideInAPackage("..controller..")
					.should().dependOnClassesThat().resideInAPackage("..entity..")
					.because("엔티티를 요청 바디로 받으면 서버가 정할 필드까지 클라이언트가 채울 수 있고"
							+ "(Mass Assignment), 응답으로 내보내면 비밀번호 같은 내부 필드가 함께 나간다");

	// ── 도메인 경계 ────────────────────────────────────────────────────────

	@ArchTest
	static final ArchRule customer는_다른_도메인의_repository를_모른다 =
			noClasses().that().resideInAPackage("..shopapi.customer..")
					.should().dependOnClassesThat()
					.resideInAnyPackage("..shopapi.product.repository..", "..shopapi.order.repository..")
					.because("MSA 분리 시 Service 호출부만 Client로 교체하면 되지만, "
							+ "Repository를 직접 물면 데이터 접근 코드를 전부 헤집어야 한다");

	@ArchTest
	static final ArchRule product는_다른_도메인의_repository를_모른다 =
			noClasses().that().resideInAPackage("..shopapi.product..")
					.should().dependOnClassesThat()
					.resideInAnyPackage("..shopapi.customer.repository..", "..shopapi.order.repository..");

	@ArchTest
	static final ArchRule order는_다른_도메인의_repository를_모른다 =
			noClasses().that().resideInAPackage("..shopapi.order..")
					.should().dependOnClassesThat()
					.resideInAnyPackage("..shopapi.customer.repository..", "..shopapi.product.repository..");

	@ArchTest
	static final ArchRule customer와_product는_order를_모른다 =
			noClasses()
					.that().resideInAnyPackage("..shopapi.customer.service..", "..shopapi.product.service..",
							"..shopapi.customer.entity..", "..shopapi.product.entity..")
					.should().dependOnClassesThat().resideInAPackage("..shopapi.order..")
					.because("의존은 order → customer, order → product 단방향이다. "
							+ "역방향이 생기면 순환이 된다 — 고객 삭제 시 주문을 지우려다 실제로 마주친 상황이다");

	@ArchTest
	static final ArchRule 도메인_사이에_순환이_없다 =
			slices().matching("com.sk.skala.shopapi.(*)..").should().beFreeOfCycles();

	// ── 주입 ──────────────────────────────────────────────────────────────

	@ArchTest
	static final ArchRule 필드_주입을_쓰지_않는다 =
			fields()
					.that().areDeclaredInClassesThat().resideInAPackage("com.sk.skala.shopapi..")
					.should().notBeAnnotatedWith(
							org.springframework.beans.factory.annotation.Autowired.class)
					.because("필드 주입은 테스트에서 주입 대상을 바꾸기 어렵고, final을 못 붙여 불변성을 잃으며, "
							+ "순환 참조가 기동 시점에 드러나지 않는다");
}
