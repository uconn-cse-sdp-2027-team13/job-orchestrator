package edu.uconn.cse.team13.producer.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Representation of a single payment record conforming to Section 6.1 schema:
 * account_number, email, payment_due_date, payment_amount.
 *
 * @param accountNumber unique identifier for the account
 * @param email synthetic email address
 * @param paymentDueDate payment due date in ISO-8601 (YYYY-MM-DD)
 * @param paymentAmount payment amount formatted as decimal(12,2), never floating point
 */
public record AccountPaymentRecord(
        String accountNumber,
        String email,
        LocalDate paymentDueDate,
        BigDecimal paymentAmount) {

    public AccountPaymentRecord {
        Objects.requireNonNull(accountNumber, "accountNumber must not be null");
        if (accountNumber.isBlank()) {
            throw new IllegalArgumentException("accountNumber must not be blank");
        }
        Objects.requireNonNull(email, "email must not be null");
        if (email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        Objects.requireNonNull(paymentDueDate, "paymentDueDate must not be null");
        Objects.requireNonNull(paymentAmount, "paymentAmount must not be null");
        if (paymentAmount.scale() > 2) {
            throw new IllegalArgumentException(
                    "paymentAmount scale must not exceed 2 decimal places: " + paymentAmount);
        }
    }
}

