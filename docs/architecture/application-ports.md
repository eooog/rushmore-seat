# Application Ports

애플리케이션 계층은 `domain`과 같은 레벨의 `application` 패키지에 둔다.

```text
com.eooog.rushseat
├─ domain
├─ application
├─ adapter
└─ infrastructure
```

`application` 하위는 업무 경계 기준으로 나눈다.

```text
application/reservation
├─ provided
├─ required
├─ ReservationModel.kt
└─ ReservationService.kt
```

## Provided Port

`provided`는 application이 외부에 제공하는 use case다.

```text
adapter.in.web
  -> application.reservation.provided.HoldSeatUseCase
```

Controller, scheduler, batch 등 inbound adapter는 provided port만 호출한다.

## Required Port

`required`는 application이 외부 기술에 요구하는 port다.

```text
application.reservation.ReservationService
  -> application.reservation.required.HoldPerformanceSeatPort
  -> adapter.out.persistence
```

DB, Redis, WebSocket 구현은 required port를 구현한다.

## Package Rule

작은 use case 단위에서는 `command`, `result`, `service` 패키지를 별도로 만들지 않는다.

```text
ReservationModel.kt
- use case command
- use case result

ReservationService.kt
- use case implementation

provided/ReservationUseCase.kt
- inbound use case interface

required/ReservationPort.kt
- outbound port interface
```

## Port Rule

Entity별 CRUD port를 기계적으로 만들지 않는다.

```text
비추천:
- LoadSeatPort
- SaveSeatPort
- LoadTilePort

추천:
- HoldPerformanceSeatPort
- ConfirmPerformanceSeatPort
- LoadTileSeatsPort
- LoadTileSummaryPort
```

즉 port는 테이블이 아니라 use case와 성능 경계 기준으로 정의한다.
