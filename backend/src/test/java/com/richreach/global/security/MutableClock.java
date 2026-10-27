package com.richreach.global.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** 테스트에서 시간을 앞으로 돌려 토큰 만료를 확인하기 위한 시계 */
class MutableClock extends Clock {

    private Instant now;

    MutableClock(Instant now) {
        this.now = now;
    }

    void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneId.systemDefault();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }

}
