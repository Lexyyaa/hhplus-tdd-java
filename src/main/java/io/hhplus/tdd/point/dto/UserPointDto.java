package io.hhplus.tdd.point.dto;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserPointDto {
    @Min(1)
    private long id;
    private long point;
    private long updateMillis;
    public UserPointDto (long id, long point, long updateMillis){
        this.id = id;
        this.updateMillis = System.currentTimeMillis();
    }
}
