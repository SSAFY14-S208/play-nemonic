# Host Maintenance — EC2 호스트 자동 정리

EC2 호스트에 누적되는 **Docker 이미지·빌드 캐시·컨테이너 로그·로컬 registry 잔여 blob**
을 주기적으로 정리하는 systemd timer 묶음. Jenkins가 매번 새 태그를 push해서 디스크가
빠르게 차는 환경을 안전하게 유지하기 위한 설정.

## 구성 파일

```
deploy/host-maintenance/
├── README.md              # 본 문서
├── install.sh             # 호스트에 idempotent 설치/갱신
├── systemd/
│   ├── docker-prune.service    # 매일 4:00 KST 이미지·빌드캐시 정리
│   ├── docker-prune.timer
│   ├── registry-gc.service     # 일요일 4:30 KST 로컬 registry GC
│   └── registry-gc.timer
└── docker/
    └── daemon.json        # 컨테이너 json log 회전 (50MB × 5개 cap)
```

## 어떤 정책으로 정리하는가

| 항목 | 주기 | 보존 기준 | 영향 |
| --- | --- | --- | --- |
| dangling/사용 안 하는 이미지 | 매일 04:00 | 최근 **7일 이내** 생성된 이미지는 보존 (`--filter until=168h`) | 롤백 여유분 확보 |
| build cache | 매일 04:00 | 최근 7일 이내 | 빌드 속도 약간 감소 가능, 디스크 회수량 큼 |
| stopped 컨테이너 / 미사용 네트워크 | 매일 04:00 | 자동 정리 | 운영 컨테이너 영향 없음 |
| **volumes** | **건드리지 않음** | — | postgres / opensearch / minio 데이터 안전 |
| local registry blob | 매주 일요일 04:30 | mark-and-sweep GC | tag 삭제만으로는 layer가 안 지워져서 필요 |
| 컨테이너 stdout log | 실시간 (json-file 회전) | 컨테이너 1개당 50MB × 5파일 = 250MB 상한 | 신규 컨테이너에만 적용, 기존은 재배포 시 |

> **--volumes는 의도적으로 빼둠**: docker prune이 잘못 detect할 위험이 있어 명시적으로
> 제외. 사용 안 하는 volume이 쌓이면 수동으로 `docker volume ls -f dangling=true` 보고
> 결정.

## 설치 / 갱신

호스트에 SSH 접속 후:

```bash
# 1) infra 레포 최신 상태로 (Jenkins 배포 흐름과 동일하게 /opt/nemonic/infra 기준)
cd /opt/nemonic/infra
sudo git fetch --tags origin
sudo git checkout infra/dev
sudo git pull origin infra/dev

# 2) 설치 스크립트 실행 (멱등 — 여러 번 실행해도 안전)
sudo bash deploy/host-maintenance/install.sh
```

설치 스크립트는:
1. systemd unit 4개(`docker-prune.{service,timer}`, `registry-gc.{service,timer}`)를
   `/etc/systemd/system/`으로 복사
2. `daemon-reload` 후 두 timer enable
3. `/etc/docker/daemon.json`을 백업 후 본 레포 버전으로 교체 (diff 있을 때만)
4. daemon.json이 바뀌었으면 사용자에게 "수동 `systemctl restart docker` 필요" 안내
   (운영 중 자동 재시작은 위험)
5. 등록된 timer를 출력해 검증

## 검증

```bash
# 등록된 timer + 다음 실행 시각
systemctl list-timers docker-prune.timer registry-gc.timer

# 마지막 실행 결과
sudo journalctl -u docker-prune.service -n 50
sudo journalctl -u registry-gc.service -n 50

# 수동으로 한 번 트리거해서 동작 검증
sudo systemctl start docker-prune.service
docker system df   # 전후 비교
```

## 갱신 흐름

본 설정 변경(예: prune 주기 / 보존 기간 / 로그 size 캡)이 필요하면:

1. 본 디렉터리의 파일 수정 + 커밋 + PR → infra/dev 머지
2. 호스트에서 위 "설치 / 갱신" 절차 동일 (script가 멱등)
3. daemon.json 변경 시 사용자가 정해진 점검 시간에 `sudo systemctl restart docker`

## 처음 적용할 때 한 번 직접 해야 할 일

설치 스크립트는 기존 컨테이너의 로그 회전 설정에는 영향을 주지 않습니다 (신규
컨테이너만). 모든 기존 컨테이너에 즉시 적용하려면 한 번 force recreate:

```bash
cd /opt/nemonic/infra
sudo docker compose -f docker-compose.prod.yml up -d --force-recreate
```

이후 매일 새벽 prune이 알아서 동작합니다.

## 더 공격적으로 정리하고 싶을 때

본 설정은 보수적입니다. 디스크가 더 빠르게 찬다면 `docker-prune.service`의
`--filter until=168h`를 `72h`(3일) 정도로 줄이거나, 별도 스크립트로 nemonic 이미지
태그별로 마지막 N개만 남기는 정책을 추가할 수 있습니다.
