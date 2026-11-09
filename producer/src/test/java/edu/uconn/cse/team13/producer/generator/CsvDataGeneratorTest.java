package edu.uconn.cse.team13.producer.generator;

import edu.uconn.cse.team13.producer.model.AccountPaymentRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvDataGeneratorTest {

    private final CsvDataGenerator generator = new CsvDataGenerator();

    @Test
    void generatesSyntheticCsvWithMatchingSchema(@TempDir Path tempDir) throws IOException {
        Path csvFile = tempDir.resolve("test_payments.csv");
        int count = 25;

        long written = generator.generateSyntheticCsv(csvFile, count);
        assertEquals(count, written);
        assertTrue(Files.exists(csvFile));

        List<String> lines = Files.readAllLines(csvFile);
        assertEquals(count + 1, lines.size());
        assertEquals(CsvDataGenerator.CSV_HEADER, lines.get(0));

        Set<String> accountNumbers = new HashSet<>();
        for (int i = 1; i <= count; i++) {
            String line = lines.get(i);
            String[] parts = line.split(",");
            assertEquals(4, parts.length, "Line must have 4 columns: " + line);

            String accountNumber = parts[0];
            String email = parts[1];
            String dateStr = parts[2];
            String amountStr = parts[3];

            assertTrue(accountNumber.startsWith("ACC-"), "Account number must start with ACC-: " + accountNumber);
            assertTrue(accountNumbers.add(accountNumber), "Account number must be unique within file: " + accountNumber);

            assertTrue(email.contains("@example.com"), "Email must be synthetic: " + email);

            // Validates ISO-8601 date format
            LocalDate parsedDate = LocalDate.parse(dateStr);
            assertTrue(parsedDate.isAfter(LocalDate.now().minusDays(1)));

            // Validates decimal(12,2)
            BigDecimal amount = new BigDecimal(amountStr);
            assertEquals(2, amount.scale());
            assertTrue(amount.compareTo(BigDecimal.ZERO) > 0);
        }
    }

    @Test
    void generatesHeaderOnlyWhenZeroRecords(@TempDir Path tempDir) throws IOException {
        Path csvFile = tempDir.resolve("empty.csv");
        long written = generator.generateSyntheticCsv(csvFile, 0);

        assertEquals(0, written);
        List<String> lines = Files.readAllLines(csvFile);
        assertEquals(1, lines.size());
        assertEquals(CsvDataGenerator.CSV_HEADER, lines.get(0));
    }

    @Test
    void rejectsNegativeRecordCount(@TempDir Path tempDir) {
        Path csvFile = tempDir.resolve("invalid.csv");
        assertThrows(IllegalArgumentException.class, () -> generator.generateSyntheticCsv(csvFile, -1));
    }

    @Test
    void formatProductionCsvOutputsValidLayout(@TempDir Path tempDir) throws IOException {
        Path csvFile = tempDir.resolve("production.csv");
        List<AccountPaymentRecord> records = List.of(
                new AccountPaymentRecord("ACCT-100", "real.user1@example.com", LocalDate.of(2026, 12, 1), new BigDecimal("150.75")),
                new AccountPaymentRecord("ACCT-200", "real.user2@example.com", LocalDate.of(2026, 12, 15), new BigDecimal("99.00"))
        );

        long written = generator.formatProductionCsv(csvFile, records);
        assertEquals(2, written);

        List<String> lines = Files.readAllLines(csvFile);
        assertEquals(3, lines.size());
        assertEquals(CsvDataGenerator.CSV_HEADER, lines.get(0));
        assertEquals("ACCT-100,real.user1@example.com,2026-12-01,150.75", lines.get(1));
        assertEquals("ACCT-200,real.user2@example.com,2026-12-15,99.00", lines.get(2));
    }
}

