package com.firstclub.membership.exception;

public class InvalidTierTransitionException extends RuntimeException {
    public InvalidTierTransitionException(String message) {
        super(message);
    }
}
