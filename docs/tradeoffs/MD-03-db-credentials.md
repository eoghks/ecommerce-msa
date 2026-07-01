# MD-03 · DB 자격증명 application.yml 기본값 노출

**대상:** `auth-service` — `application.yml`

## 현재 구현

```yaml
datasource:
  username: ${DB_USERNAME:eoghks}       # 기본값에 실제 계정명
  password: ${DB_PASSWORD:eoghks_local} # 기본값에 실제 비밀번호
```

## 문제점

환경변수 미설정 시 기본값으로 평문 자격증명 사용.
형상관리(GitHub)에 포함되면 자격증명 노출.

## 운영 수준 해결책

```yaml
# 기본값 제거 — 환경변수 필수화
datasource:
  username: ${DB_USERNAME}
  password: ${DB_PASSWORD}
```
```bash
# .env (gitignore에 포함)
DB_USERNAME=eoghks
DB_PASSWORD=eoghks_local
```
또는 Docker Compose `env_file`, K8s Secret, AWS Parameter Store 사용.

## 포트폴리오 선택 이유

로컬 개발 편의성. 현재 계정은 로컬 전용 계정이라 실제 보안 위협 없음.
`.env` 파일 추가 시 Docker Compose와 Spring Boot 모두 설정 변경 필요해 복잡도 증가.

## 면접 답변 포인트

> "로컬 개발 환경 편의를 위해 기본값을 설정했습니다. 운영 배포 시에는 기본값을 제거하고
> 환경변수를 필수화하거나, AWS Secrets Manager나 Spring Cloud Config를 통해
> 자격증명을 외부 관리합니다."
