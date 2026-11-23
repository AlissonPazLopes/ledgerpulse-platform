package dev.ledgerpulse.webhook.web;

public class InvalidDeliveryStateException extends RuntimeException {

    public InvalidDeliveryStateException(String message) {
        super(message);
    }
}
