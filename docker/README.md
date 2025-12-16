# CloudGate IAM Local Infra

로컬 개발을 위한 MySQL, Redis, Kafka(KRaft) 환경을 `docker-compose.yml`로 제공합니다.

## 사전 준비
- Docker Desktop 혹은 Docker Engine + Docker Compose v2
- 4GB 이상의 여유 메모리(세 서비스 동시 구동 시 권장)

## 실행 방법
1. `cd docker`
2. `docker compose up -d` 또는 `docker compose -f docker-compose.yml up -d`
   - 첫 실행 시 이미지 다운로드가 필요하므로 네트워크 연결이 요구됩니다.
3. 중지: `docker compose down`
4. 데이터까지 정리: `docker compose down -v`
   - 기존 MySQL 볼륨과 MySQL 8.4 데이터 포맷이 충돌해 `mysql.plugin` 관련 오류가 날 경우 `docker volume rm cloudgateiam_mysql_data` 후 재기동하세요.
   - Kafka를 KRaft로 재기동할 때 이전 볼륨이 남아 CLUSTER_ID 불일치가 발생하면 `docker volume rm cloudgateiam_kafka_data` 후 재기동하세요. CLUSTER_ID는 base64 UUID(`cjBHQTBUTU5YQk1GRjgxQg==`)로 고정되어 있습니다.

## 포트 및 기본 자격 증명
- MySQL: `localhost:${MYSQL_PORT:-3306}` / DB `cloudgate_iam`, 사용자 `cloudgate` / `${MYSQL_PASSWORD:-cloudgate-pass}`
- Redis: `localhost:${REDIS_PORT:-6379}` (이미지: `redis:7`)
- Kafka: `localhost:${KAFKA_PORT:-9092}` (내부 브로커 `kafka:29092`, 컨트롤러 `kafka:29093`, 이미지: `confluentinc/cp-kafka:7.6.1`)

환경 변수는 `.env` 파일(또는 쉘 환경 변수)로 오버라이드할 수 있습니다.

## 운영 노트
- 각 서비스 데이터는 Docker 볼륨(`mysql_data`, `redis_data`, `kafka_data`)에 저장됩니다.
- Apple Silicon에서 이미지 호환성 문제가 있을 경우 `docker-compose.yml`에 `platform: linux/amd64`를 추가하여 재실행하세요.
- Kafka 단일 브로커(KRaft, 컨트롤러/브로커 동일 노드)이므로 운영 환경에서는 별도 컨트롤러·브로커 replica 구성을 고려해야 합니다.
