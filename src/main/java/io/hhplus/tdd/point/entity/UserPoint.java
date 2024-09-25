package io.hhplus.tdd.point.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UserPoint(
        @Min(1)
        long id,

        @Min(0)
        @Max(5000)
        long point,
        long updateMillis
) {
    public static UserPoint empty(long id) {
        return new UserPoint(id, 0, System.currentTimeMillis());
    }

    /**
     * 충전가능한지 체크함(최대잔고체크)
     * @param amount
     */
    public boolean canCharge(long amount) {
        return (this.point + amount) <= 5000;
    }

    /**
     * 사용가능한지 체크함(잔고체크)
     * @param amount
     */
    public boolean canUse(long amount){
        return (this.point - amount) < 0;
    }

    /**
     * 만료여부를 체크함(10초
     * @param requestMillis
     */
    public boolean isExpired(long requestMillis){
        return this.updateMillis - requestMillis > 10000;
    }
}
