# Redis maxmemory-policy

> 면접 대비 정리 문서

---

## 왜 필요한가

Redis는 메모리가 꽉 차면 새 데이터를 못 씁니다. 이때 어떤 키를 버릴지 정책이 필요합니다.

---

## 정책 종류

| 정책 | 설명 |
|------|------|
| `noeviction` | 삭제 안 함 — 꽉 차면 에러 반환 (기본값) |
| `allkeys-lru` | 전체 키 중 가장 오래 사용 안 한 키 삭제 |
| `volatile-lru` | TTL 있는 키 중 가장 오래 사용 안 한 키 삭제 |
| `allkeys-lfu` | 전체 키 중 사용 빈도 가장 낮은 키 삭제 |
| `volatile-ttl` | TTL 가장 짧게 남은 키 삭제 |

---

## 실무 선택 기준

**캐시 서버 → `allkeys-lru`**
```
전체 키가 캐시 데이터
→ 어떤 키든 지워도 됨 (DB에서 다시 가져올 수 있음)
→ 가장 오래 사용 안 한 것부터 삭제
```

**세션/락 서버 → `volatile-lru`**
```
TTL 없는 키 = 중요한 데이터 (분산락, 세션 등)
TTL 있는 키 = 캐시 데이터
→ TTL 있는 것만 삭제, 중요 데이터 보호
```

---

## 설정

```yaml
# docker-compose.yml
redis:
  command: redis-server --maxmemory 256mb --maxmemory-policy allkeys-lru
```

---

## 면접 예상 질문

**Q. Redis 메모리가 꽉 차면 어떻게 되나요?**
> 기본값 noeviction이면 에러를 반환합니다. 캐시 서버로 쓸 때는 allkeys-lru로 설정해서 가장 오래 사용 안 한 키부터 자동 삭제되도록 합니다.

**Q. allkeys-lru vs volatile-lru 차이는요?**
> allkeys-lru는 전체 키 대상으로 삭제하고, volatile-lru는 TTL이 설정된 키만 삭제합니다. 캐시만 저장하는 Redis면 allkeys-lru, 캐시와 세션/락을 같이 저장하면 중요 데이터 보호를 위해 volatile-lru가 맞습니다.
