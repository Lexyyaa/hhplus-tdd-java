package io.hhplus.tdd.point.service;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.dto.PointHistoryDto;
import io.hhplus.tdd.point.entity.PointHistory;
import io.hhplus.tdd.point.entity.UserPoint;
import io.hhplus.tdd.point.controller.PointController;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Validated
@RequiredArgsConstructor
public class PointService {

    private static final Logger log = LoggerFactory.getLogger(PointController.class);

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;
    // 유저별 Lock을 관리하기 위한 ConcurrentHashMap
    private final ConcurrentHashMap<Long, Lock> userLockMap = new ConcurrentHashMap<>();

    private Lock getUserLock(Long userId) {
        return userLockMap.computeIfAbsent(userId, id -> new ReentrantLock());
    }

    /**
     * TODO - 특정 유저의 포인트를 조회하는 기능을 작성해주세요.
     */
    public UserPoint point(Long id) {
        return userPointTable.selectById(id);
    }

    /**
     * TODO - 특정 유저의 포인트 충전/이용 내역을 조회하는 기능을 작성해주세요.
     */
    public List<PointHistory> history(Long id) {
        return pointHistoryTable.selectAllByUserId(id);
    }

    /**
     * TODO - 특정 유저의 포인트를 충전하는 기능을 작성해주세요.
     *  최소충전금액 100
     *  최대잔고 5000
     */
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

    /**
     * TODO - 특정 유저의 포인트를 사용하는 기능을 작성해주세요.
     *  최소사용단위 100
     *  잔고부족시 포인트사용불가
     *  충전 후 10초가 지나면 사용불가
     */
    public UserPoint use(@Valid PointHistoryDto pointHistoryDto) {
        Long userId = pointHistoryDto.getUserId();

        Lock lock = getUserLock(userId);
        lock.lock(); // 유저별로 lock 을 걸어줌
        try {
            // 1. 유저포인트객체를 가져온다.
            UserPoint currUserPoint = userPointTable.selectById(pointHistoryDto.getUserId());

            // 2. 사용가능여부 검증
            if (currUserPoint.canUse(pointHistoryDto.getAmount())) {
                throw new RuntimeException("포인트가 부족합니다.");
            }
            if (currUserPoint.isExpired(pointHistoryDto.getUpdateMillis())){
                throw new RuntimeException("만료된 포인트입니다.");
            }

            // 2.1 검증 후 포인트 사용
            @Valid
            UserPoint uPoint = userPointTable.insertOrUpdate(currUserPoint.id(), currUserPoint.point() - pointHistoryDto.getAmount());

            // 2.2. 사용히스토리 저장
            pointHistoryTable.insert(pointHistoryDto.getUserId(), pointHistoryDto.getAmount(), pointHistoryDto.getType(), pointHistoryDto.getUpdateMillis());

            return uPoint;
        } finally {
            lock.unlock();  // 로직 종료 시 Lock 해제
        }
    }
}
