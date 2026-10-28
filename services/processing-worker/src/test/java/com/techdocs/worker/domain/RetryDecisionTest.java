package com.techdocs.worker.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetryDecisionTest {

    @Test
    void retriesUntilTheConfiguredMaximum() {
        assertEquals(RetryDecision.Action.RETRY, RetryDecision.decide(0, 3));
        assertEquals(RetryDecision.Action.RETRY, RetryDecision.decide(1, 3));
        assertEquals(RetryDecision.Action.FAIL, RetryDecision.decide(2, 3));
    }
}
