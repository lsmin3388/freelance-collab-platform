# 프리랜서 프로젝트 협업 플랫폼 (3팀 프로토타입)

소프트웨어설계 3팀의 System Modeling and Design Document를 Spring으로 구현한 프로토타입이다.
범위는 외주를 맡기고 서로 고쳐 가며 확정하는 핵심 시나리오 4개이다.

| 시나리오 | 흐름 | 프로젝트 상태 |
|---|---|---|
| S1 제안 선정 | 의뢰자가 접수된 제안 중 하나를 고른다 | 모집 중 → 조건 합의 중 |
| S2 작업 조건 합의 | 양쪽이 합의안을 번갈아 고치고, 같은 개정을 둘 다 확인하면 확정된다. 결렬하면 다시 모집한다 | 조건 합의 중 → 작업 중 (결렬: → 모집 중) |
| S3 결과물 제출 | 선정 프리랜서가 합의한 형식으로 회차를 낸다 | 작업 중·수정 중 → 검토 중 |
| S4 검토·피드백·완료 | 의뢰자가 수정 요청(피드백 필수)과 승인을 반복하다 완료하고 정산한다 | 검토 중 → 수정 중 / 승인됨 → 완료 |

## 실행

Java 21이 필요하다.

```bash
./gradlew bootRun          # http://localhost:8080
./gradlew test             # 35개 시험
```

시연 계정(비밀번호 모두 `1234`)과 제안 3건이 접수된 프로젝트 1개가 자동으로 만들어진다.

| 이메일 | 이름 | 역할 |
|---|---|---|
| client@demo.kr | 김의뢰 | 의뢰자 (프로젝트 소유) |
| design@demo.kr | 박디자 | 프리랜서 |
| dev@demo.kr | 최개발 | 프리랜서 |
| both@demo.kr | 이겸임 | 의뢰자·프리랜서 |

시연 순서: 김의뢰로 제안 목록에서 박디자를 선정한다. 브라우저 두 개(또는 시크릿 창)로 김의뢰와 박디자가 합의안을 고치고 확인한다. 이어서 박디자가 png·pdf를 제출하고, 김의뢰가 수정 요청 → 재제출 → 승인 → 완료 처리한다.

Mock 실패를 흉내 내려면 `--mock.notification.always-fail=true` 또는 `--mock.payment.always-fail=true`로 실행한다. 통지나 정산이 실패해도 선정·완료는 유지되고 안내 문구만 나온다.

## 구조 (설계 문서 Phase 2 계층과 같음)

| 계층 | 패키지 | 클래스 |
|---|---|---|
| Presentation | `presentation` | `ProposalListView`, `AgreementView`, `DeliverableView`, `ReviewView` 등 (`@Controller` + Thymeleaf) |
| Domain.Service | `domain.service` | `MatchingService`, `AgreementService`, `DeliveryService`, `AccessGuard` 등 |
| Domain.Model | `domain.model` | `Project`, `Proposal`, `Agreement`, `AgreementVersion`, `Deliverable`, `Feedback`, `Settlement`, `HistoryEntry` 등 |
| Data.Repository | `data.repository` | `ProjectRepository`, `UserRepository`, `FileStorage` |
| Data.External (Mock) | `data.external` | `NotificationService`(+`NotificationLog`), `PaymentService`, `AuthService` |

- 상태를 바꾸는 요청은 모두 같은 순서로 처리한다. 권한 확인(`AccessGuard`) → 행 잠금 조회(`ProjectRepository.findByIdForUpdate`) → `Project` 연산 → 저장까지가 하나의 트랜잭션이고, 커밋 뒤에 Mock을 호출한다.
- 업무 규칙은 모두 `domain.model`에 있다. 규칙을 어기면 `DomainException(ErrorCode)`이 나고 아무것도 바뀌지 않는다. 화면은 그 사유를 보여 준다.
- 설계 문서의 상세 시퀀스(그림 3-2 ~ 3-7)에 나오는 메시지는 모두 이 코드의 메서드 이름과 같다. 설계 문서 원본의 `check_code.py`로 확인한다.

## 시험

| 시험 | 범위 |
|---|---|
| `ProjectScenarioTest` (27) | 시나리오별 Model 규칙과 예외 흐름. 거부되면 상태가 그대로인지도 함께 확인 |
| `CoreFlowIntegrationTest` (5) | Service부터 DB·Mock까지 전체 흐름, 결렬 후 재선정, 통지·정산 실패, 권한, 양측 동시 확인 |
| `WebFlowTest` (3) | 화면 요청으로 선정부터 완료까지, 로그인·403 처리, 모든 화면 렌더링 |
