package io.hhplus.tdd.point.service;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.dto.PointHistoryDto;
import io.hhplus.tdd.point.entity.PointHistory;
import io.hhplus.tdd.point.entity.TransactionType;
import io.hhplus.tdd.point.entity.UserPoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @BeforeEach
    public void setUp() {
        // 기본 UserPoint 설정
        currUserPoint = new UserPoint(10L, 1000L,System.currentTimeMillis());  // ID: 10, 포인트: 1000
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

        // 충전가능 (검증)
        // when(currUserPoint.canCharge(pointHistoryDto.getAmount())).thenReturn(false); // 주석을 하니,, 돌아가는 매직...

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

        // 유저 포인트를 1000으로 설정
        when(currUserPoint.point()).thenReturn(1000L);
        // 잔고 부족 상황 (검증)
        // when(currUserPoint.canUse(pointHistoryDto.getAmount())).thenReturn(false);

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
        UserPoint userPoint = new UserPoint(10L, 1000, pointHistoryDto.getUpdateMillis() + 11000);
        when(userPointTable.selectById(pointHistoryDto.getUserId())).thenReturn(userPoint);

        // 포인트 만료 검증
        // when(userPoint.isExpired(pointHistoryDto.getUpdateMillis())).thenReturn(true);

        // when & then
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            pointService.use(pointHistoryDto);
        });

        assertEquals("만료된 포인트입니다.", thrown.getMessage());
    }

}