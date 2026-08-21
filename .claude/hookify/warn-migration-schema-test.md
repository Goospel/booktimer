---
name: warn-migration-schema-test
enabled: true
event: file
action: warn
path-pattern: db[/\\]migration[/\\]V\d+__.*\.sql$
pattern: (?i)(create\s+table|alter\s+table|add\s+constraint|create\s+(unique\s+)?index|drop\s+table)
---
⚠️ 마이그레이션으로 **테이블·제약**을 건드리고 있다. 두 가지를 지금 확인하라 — 둘 다 나중엔 안 보인다.

**① 검증은 `FlywayMigrationTest`에 둔다** (T-168 · T-169, 같은 뿌리 2회차)

메인 스위트는 `spring.flyway.enabled=false` + `ddl-auto=create-drop`이라 H2 스키마를 **엔티티 매핑에서** 만든다.
이 레포는 유니크·CHECK를 엔티티 `@Table`이 아니라 **마이그레이션에만** 둔다(DB가 단일 출처 — `uk_users_login_id` 등).
그래서 마이그레이션에만 있는 테이블·제약은 그 스위트에 **아예 존재하지 않고, 없는 제약은 위반될 수도 없다**:

- `@DataJpaTest`·일반 `@SpringBootTest`에 둔 제약 위반 테스트 → 예외가 안 나 **영영 초록이 안 된다**(T-169)
- JPA 미매핑 테이블의 FK 결함 → 전 스위트 그린인 채 **운영에서 터진다**(T-168, 운영 27명 중 2명 탈퇴 불가)

판정 기준은 하나다 — **그게 엔티티 매핑에 있나, 마이그레이션에만 있나.** 후자면 `FlywayMigrationTest`.
그리고 손으로 고른 목록을 단언하지 말고 **스키마에서 파생되는** 단언으로 써라(`INFORMATION_SCHEMA` 질의 → 양방향 대조).

**② 버전 번호는 `origin/main` 기준으로 잡는다** (T-170)

`git fetch origin` 후 원격의 마지막 번호 +1이어야 한다. **로컬 기준 +1은 병렬 작업에서 반드시 어긋난다** —
각자 워크트리에선 자기 번호가 유일해 머지 전엔 어느 쪽에서도 안 보인다.
겹치면 그 스크립트만 실패하는 게 아니라 `Found more than one migration with version N`으로
**스키마 초기화가 통째로 거부**돼, 무관해 보이는 클래스가 전건 `Failed to load ApplicationContext`로 죽는다.

```bash
git fetch origin && git ls-tree --name-only origin/main src/main/resources/db/migration/ | sort -V | tail -3
```
