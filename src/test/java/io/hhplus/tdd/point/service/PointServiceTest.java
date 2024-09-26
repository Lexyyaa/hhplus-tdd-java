package io.hhplus.tdd.point.service;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.dto.PointHistoryDto;
import io.hhplus.tdd.point.entity.TransactionType;
import io.hhplus.tdd.point.entity.UserPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private UserPointTable userPointTable;

    @Mock
    private PointHistoryTable pointHistoryTable;

    @InjectMocks
    private PointService pointService;

    private UserPoint currUserPoint;
    private Lock lock;
    private ConcurrentHashMap<Long, UserPoint> userPointMap;

    @BeforeEach
    public void setUp() {
        // 기본 UserPoint 설정
        currUserPoint = new UserPoint(10L, 1000L,System.currentTimeMillis());  // ID: 10, 포인트: 1000
        lock = new ReentrantLock();
        userPointMap = new ConcurrentHashMap<>();
    }
    @Test
    @DisplayName("[포인트충전/성공] 포인트 충전 성공")
    public void 포인트_충전_성공() throws Exception {
        // given
        PointHistoryDto pointHistoryDto = new PointHistoryDto(10L, 3000, TransactionType.CHARGE);

        // 기존 유저의 포인트는 1000
        UserPoint currUserPoint = new UserPoint(10L, 1000, System.currentTimeMillis());
        when(userPointTable.selectById(pointHistoryDto.getUserId())).thenReturn(currUserPoint);

        // 충전 후 포인트는 4000
        UserPoint updatedUserPoint = new UserPoint(10L, 4000, System.currentTimeMillis());
        when(userPointTable.insertOrUpdate(currUserPoint.id(), currUserPoint.point() + pointHistoryDto.getAmount())).thenReturn(updatedUserPoint);

        // when
        UserPoint resultUserPoint = pointService.charge(pointHistoryDto);

        // then
        assertEquals(resultUserPoint.id(), 10L);
        assertEquals(resultUserPoint.point(), 4000);
    }

    @Test
    @DisplayName("[포인트충전/실패] 최대잔고 초과")
    public void 포인트_충전_최대잔고_초과() {
        // given
        PointHistoryDto pointHistoryDto = new PointHistoryDto(10L, 5000L, TransactionType.CHARGE); // 5000 충전요청
        when(userPointTable.selectById(10L)).thenReturn(currUserPoint);

        // when & then
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            pointService.charge(pointHistoryDto);
        });

        assertEquals("최대잔고를 초과함", thrown.getMessage());
    }

    @Test
    @DisplayName("[포인트사용/성공] 포인트 사용 성공")
    public void 포인트_사용_성공() throws Exception {
        // given
        PointHistoryDto pointHistoryDto = new PointHistoryDto(10L, 100, TransactionType.USE);  // 최소 사용 단위는 100

        // 유저 포인트는 1000
        UserPoint currUserPoint = new UserPoint(10L, 1000, System.currentTimeMillis());
        when(userPointTable.selectById(pointHistoryDto.getUserId())).thenReturn(currUserPoint);

        // 예상 결과: 1000에서 100을 사용하고 남은 포인트는 900
        UserPoint updatedUserPoint = new UserPoint(10L, 900, System.currentTimeMillis());
        when(userPointTable.insertOrUpdate(currUserPoint.id(), currUserPoint.point() - pointHistoryDto.getAmount())).thenReturn(updatedUserPoint);

        // when
        UserPoint resultUserPoint = pointService.use(pointHistoryDto);

        // then
        assertEquals(resultUserPoint.id(), 10L);
        assertEquals(resultUserPoint.point(), 900);
    }

    @Test
    @DisplayName("[포인트사용/실패] 잔고 부족")
    public void 포인트_사용_잔고부족() {
        // given
        PointHistoryDto pointHistoryDto = new PointHistoryDto(10L, 2000, TransactionType.USE);  // 2000 포인트 사용 요청

        UserPoint currUserPoint = new UserPoint(10L,1000 ,System.currentTimeMillis());
        when(userPointTable.selectById(pointHistoryDto.getUserId())).thenReturn(currUserPoint);

        // when & then
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            pointService.use(pointHistoryDto);
        });

        assertEquals("포인트가 부족합니다.", thrown.getMessage());
    }

    @Test
    @DisplayName("[포인트사용/실패] 포인트 만료")
    public void 포인트_사용_만료() {
        // given
        PointHistoryDto pointHistoryDto = new PointHistoryDto(10L, 100, TransactionType.USE);  // 100 포인트 사용 요청

        // 유저 포인트는 1000
        UserPoint userPoint = new UserPoint(10L, 1000, pointHistoryDto.getUpdateMillis() - 11000);
        when(userPointTable.selectById(pointHistoryDto.getUserId())).thenReturn(userPoint);

        // when & then
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            pointService.use(pointHistoryDto);
        });

        assertEquals("만료된 포인트입니다.", thrown.getMessage());
    }

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
}