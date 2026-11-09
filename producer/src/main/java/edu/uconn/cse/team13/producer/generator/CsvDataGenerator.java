package edu.uconn.cse.team13.producer.generator;

import edu.uconn.cse.team13.producer.model.AccountPaymentRecord;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Generates and formats CSV files conforming to the Section 6.1 schema:
 * {@code account_number,email,payment_due_date,payment_amount}.
 *
 * Keeps two distinct modes per Section 6.1:
 * 1. Test and demonstration mode: creates synthetic data.
 * 2. Production mode: accepts real payment records and formats them into the standard CSV layout.
 */
public class CsvDataGenerator {

    public static final String CSV_HEADER = "account_number,email,payment_due_date,payment_amount";

    /**
     * Test and demonstration mode: Generates synthetic CSV data with unique accounts,
     * synthetic emails, valid due dates, and exact decimal amounts.
     * Streams rows sequentially to keep memory usage flat.
     *
     * @param targetFile destination file path
     * @param recordCount number of records to generate
     * @return the number of records written
     */
    public long generateSyntheticCsv(Path targetFile, int recordCount) throws IOException {
        Objects.requireNonNull(targetFile, "targetFile must not be null");
        if (recordCount < 0) {
            throw new IllegalArgumentException("recordCount must not be negative");
        }

        if (targetFile.getParent() != null) {
            Files.createDirectories(targetFile.getParent());
        }

        LocalDate baseDate = LocalDate.now();
        try (BufferedWriter writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8)) {
            writer.write(CSV_HEADER);
            writer.newLine();

            for (int i = 1; i <= recordCount; i++) {
                String accountNumber = String.format("ACC-%08d", i);
                String email = String.format("synthetic.user%08d@example.com", i);
                LocalDate dueDate = baseDate.plusDays(i % 30);
                BigDecimal amount = BigDecimal.valueOf(1000 + (long) (i * 37) % 50000)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY);

                writer.write(accountNumber);
                writer.write(',');
                writer.write(email);
                writer.write(',');
                writer.write(dueDate.toString());
                writer.write(',');
                writer.write(amount.toPlainString());
                writer.newLine();
            }
        }
        return recordCount;
    }

    /**
     * Production mode: Formats existing payment records into the standard Section 6.1 CSV layout.
     *
     * @param targetFile destination file path
     * @param records iterable of payment records
     * @return the number of records written
     */
    public long formatProductionCsv(Path targetFile, Iterable<AccountPaymentRecord> records) throws IOException {
        Objects.requireNonNull(targetFile, "targetFile must not be null");
        Objects.requireNonNull(records, "records must not be null");

        if (targetFile.getParent() != null) {
            Files.createDirectories(targetFile.getParent());
        }

        long count = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8)) {
            writer.write(CSV_HEADER);
            writer.newLine();

            for (AccountPaymentRecord record : records) {
                writer.write(record.accountNumber());
                writer.write(',');
                writer.write(record.email());
                writer.write(',');
                writer.write(record.paymentDueDate().toString());
                writer.write(',');
                writer.write(record.paymentAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString());
                writer.newLine();
                count++;
            }
        }
        return count;
    }
}

