# 동시성 제어 방식에 대한 보고서

 ## 방식을 알아보기 전에 
동시성 제어가 필요한 상황의 예시를 생각해보자 

 - 쇼핑몰에서 2개밖에 없는 제품에 대해 A가 1개의 구매요청을, B가 2개의 구매요청을 동시에 보냈을 때
 - DB에서 같은 데이터에 대해 동시에 동시에 update 를 진행할 때

-> 여러 작업이 동시에 동일한 데이터에 접근하여 해당 데이터의 일관성에 문제가 생길 경우 동시성 제어에 대해 고려할 필요가 있다! 

현재 과제에서도 비슷한 상황이 존재한다. 

- 5000 포인트를 가지고 있는 유저에게서 5000포인트를 사용하는요청과 5000포인트를 충전하는요청이 동시에 들어왔을때 

## 이러한 상황을 해결해보자

  ### 첫번째 시도) synchronized 
  
  ```java
  public synchronized UserPoint charge(@Valid PointHistoryDto pointHistoryDto) {
        // 1. 유저포인트 객체를 가져온다.
        UserPoint currUserPoint = userPointTable.selectById(pointHistoryDto.getUserId());
        if (!currUserPoint.canCharge(pointHistoryDto.getAmount())) {
            throw new RuntimeException("최대잔고를 초과함");
        }
        // 2.1. 충전
        @Valid
        UserPoint userPoint = userPointTable.insertOrUpdate(currUserPoint.id(), currUserPoint.point() + pointHistoryDto.getAmount());

        // 2.2. 충전히스토리 저장
        pointHistoryTable.insert(pointHistoryDto.getUserId(), pointHistoryDto.getAmount(), pointHistoryDto.getType(), pointHistoryDto.getUpdateMillis());
        return userPoint;
  }
  ```
    
  이렇게 메소드에 예약어를 붙여서 해당 메소드에 하나의 스레드만 접근할 수 있도록 제한하여 데이터 일관성을 지킨다.
  
  but,
  
  문제가 발생한다.
  
  * 유저1/사용1000
  * 유저2/사용2000
  * 유저1/충전2000
  * 유저2/사용3000
  * 유저3/사용3000
  
  이라는 요청이 동시에 들어왔을때 synchronized를 사용하면 유저3/사용3000에 대한 요청은 가장 후순위로 밀리게 된다. 
  
  하지만 동시성 제어에서의 키워드는 "동일한데이터", "동일한 자원"에서의 
  데이터 일관성을 지키는 것이다. 
  
  유저1,유저2,유저3은 각각 다른 자원이기에 우리가 실질적으로 고려해야 할 것은 유저1에 대하여 동시에 같은요청이 들어왔을 경우 이다. 
  
  때문에 synchronized를 사용하여 메소드 자체에 lock을 걸기보다 더 정교한 lock 처리를 진행해야한다.

  
  ### 두번째 시도 ) ReentrantLock 과 ConcurrentHashMap 
  
  ReentrantLock을 통해서 보다 정교하게 lock을 걸 수 있다.
  또한 공정성을 보장하여 스레드가 lock을 만들 때 대기가 길었던 스레드가 우선 lock을 획득할 수 있도록 설정할 수 있다.
  
  ConcurrentHashMap 는 동시성 문제를 해결하기 위해 설계된 해시맵이다.
  여러 스레드가 동시에 put, get 등의 연산을 안전하게 수행할 수 있기 때문에 동시에 데이터를 읽고, 쓸 수 있어 성능이슈가 있을때 사용할 수 있다.
  
```java
private final ConcurrentHashMap<Long, Lock> userLockMap = new ConcurrentHashMap<>();

private Lock getUserLock(Long userId) {
    return userLockMap.computeIfAbsent(userId, id -> new ReentrantLock());
}

public UserPoint charge(@Valid PointHistoryDto pointHistoryDto) {
    Long userId = pointHistoryDto.getUserId();

    Lock lock = getUserLock(userId);
    lock.lock(); // 유저별로 lock 을 걸어줌
    try {
        // 1. 유저포인트 객체를 가져온다.
        UserPoint currUserPoint = userPointTable.selectById(pointHistoryDto.getUserId());
        if (!currUserPoint.canCharge(pointHistoryDto.getAmount())) {
            throw new RuntimeException("최대잔고를 초과함");
        }
        // 2.1. 충전
        @Valid
        UserPoint userPoint = userPointTable.insertOrUpdate(currUserPoint.id(), currUserPoint.point() + pointHistoryDto.getAmount());

        // 2.2. 충전히스토리 저장
        pointHistoryTable.insert(pointHistoryDto.getUserId(), pointHistoryDto.getAmount(), pointHistoryDto.getType(), pointHistoryDto.getUpdateMillis());
        return userPoint;
    } finally {
        lock.unlock();  // 로직 종료 시 Lock 해제
    }
}
```
    
  이렇게 ReentrantLock을 이용하여 UserPoint를 충전,사용 하는 부분에 lock을 걸고 ConcurrentHashMap에 UserPoint객체를 넣어 사용자별로 데이터를 데이터를 관리해 synchronized를 사용했을 때 나타나는 성능이슈를 해결해였다.

## 동시성제어 테스트

  해당 테스트는 다음의 과정으로 이루어 진다.
  
  1. 초기데이터 설정
  2. 요청 정의
  3. Mock 설정
  4. 스레드 풀 설정(ExecutorService)
  5. task 정의 및 비동기 실행(Callable)
  6. task를 스레드 풀에 submit
  7. 모든작업시작 및 작업완료대기 (CountDownLatch)
  8. 결과 검증
  9. 리소스 정리(shutdown())

  의 과정으로 이루어 진다. 다른 테스트와 달리 4,5,6,7,9 과정이 추가된다.
  
```java
@Test
@DisplayName("[포인트 사용 및 충전/성공] 동시성 테스트 ")
public void 동시성_테스트() throws InterruptedException, ExecutionException {
    // given
    Long userId = 10L;

    // 동시에 접근하지 못하도록 ConcurrentHashMap으로 Map 선언
    Map<Long, UserPoint> userPointMap = new ConcurrentHashMap<>();
    UserPoint userPoint = new UserPoint(10L, 0L, System.currentTimeMillis());
    userPointMap.put(userId, userPoint);

    // 포인트 사용 및 충전 상황설정
    PointHistoryDto charge4000 = new PointHistoryDto(userId, 4000, TransactionType.CHARGE);
    PointHistoryDto use2000 = new PointHistoryDto(userId, 2000, TransactionType.USE);
    PointHistoryDto use1000 = new PointHistoryDto(userId, 1000, TransactionType.USE);
    PointHistoryDto charge500 = new PointHistoryDto(userId, 500, TransactionType.CHARGE);
    PointHistoryDto use1500 = new PointHistoryDto(userId, 1500, TransactionType.USE);
    PointHistoryDto charge2000 = new PointHistoryDto(userId, 2000, TransactionType.CHARGE);

    when(userPointTable.selectById(userId)).thenAnswer(invocation -> userPointMap.get(userId));
    when(userPointTable.insertOrUpdate(anyLong(), anyLong())).thenAnswer(invocation -> {
        Long id = invocation.getArgument(0);
        Long newPoint = invocation.getArgument(1);
        userPointMap.put(id, new UserPoint(id, newPoint, System.currentTimeMillis()));
        return userPointMap.get(id);
    });

    ExecutorService executorService = Executors.newFixedThreadPool(10);
    CountDownLatch latch = new CountDownLatch(1);

    // 비동기 실행 task 정의(각 task의 대기시간을 1초로 제한)
    Callable<Void> charge4000Task = () -> {
        latch.await(1, TimeUnit.SECONDS);
        pointService.charge(charge4000);
        return null;
    };

    Callable<Void> use2000Task = () -> {
        latch.await(1, TimeUnit.SECONDS);
        pointService.use(use2000);
        return null;
    };

    Callable<Void> use1000Task = () -> {
        latch.await(1, TimeUnit.SECONDS);
        pointService.use(use1000);
        return null;
    };

    Callable<Void> charge500Task = () -> {
        latch.await(1, TimeUnit.SECONDS);
        pointService.charge(charge500);
        return null;
    };

    Callable<Void> use1500Task = () -> {
        latch.await(1, TimeUnit.SECONDS);
        pointService.use(use1500);
        return null;
    };

    Callable<Void> charge2000Task = () -> {
        latch.await(1, TimeUnit.SECONDS);
        pointService.charge(charge2000);
        return null;
    };

    // 각 작업을 스레드로 실행
    Future<Void>[] futures = new Future[6];
    futures[0] = executorService.submit(charge4000Task);
    futures[1] = executorService.submit(use2000Task);
    futures[2] = executorService.submit(use1000Task);
    futures[3] = executorService.submit(charge500Task);
    futures[4] = executorService.submit(use1500Task);
    futures[5] = executorService.submit(charge2000Task);

    // 모든 작업 시작
    latch.countDown();

    // 모든 작업이 완료될 때까지 기다림
    for (Future<Void> future : futures) {
        future.get();
    }

    // then: 최종 포인트 확인
    UserPoint finalUserPoint = userPointMap.get(userId);

    // 4000 - 2000 - 1000 + 500 - 1500 + 2000 = 2000
    assertEquals(2000L, finalUserPoint.point());

    // 리소스 정리
    executorService.shutdown();
    if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
        executorService.shutdownNow(); // 모든 작업이 종료되지 않으면 강제로 종료
    }
}
```
