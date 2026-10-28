package com.techdocs.worker.domain;

public final class RetryDecision {

    public enum Action { RETRY, FAIL }

    private RetryDecision() {
    }

    public static Action decide(int previousFailures, int maxRetries) {
        if (maxRetries < 1) {
            return Action.FAIL;
        }
        return previousFailures + 1 >= maxRetries ? Action.FAIL : Action.RETRY;
    }
}
