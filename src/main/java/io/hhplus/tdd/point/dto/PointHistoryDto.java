package io.hhplus.tdd.point.dto;

import io.hhplus.tdd.point.entity.TransactionType;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PointHistoryDto {
    @Min(1)
    private long userId;
    @Min(100)
    private long amount;
    private TransactionType type;
    private long updateMillis;

    public PointHistoryDto (long id, long amount, TransactionType type){
        this.userId = id;
        this.amount = amount;
        this.type = type;
        this.updateMillis = System.currentTimeMillis();
    }

}
