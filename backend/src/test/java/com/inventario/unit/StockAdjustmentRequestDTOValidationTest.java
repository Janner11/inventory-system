package com.inventario.unit;

import com.inventario.dto.StockAdjustmentRequestDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StockAdjustmentRequestDTOValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void validate_withValidData_returnsNoViolations() {
        StockAdjustmentRequestDTO dto = new StockAdjustmentRequestDTO(
                UUID.randomUUID(), 10, "Conteo fisico", "Sin observaciones", "tester");

        Set<ConstraintViolation<StockAdjustmentRequestDTO>> violations = validator.validate(dto);

        assertThat(violations).isEmpty();
    }

    @Test
    void validate_withNegativeNewQuantity_isRejected() {
        StockAdjustmentRequestDTO dto = new StockAdjustmentRequestDTO(
                UUID.randomUUID(), -1, "Conteo fisico", "Sin observaciones", "tester");

        Set<ConstraintViolation<StockAdjustmentRequestDTO>> violations = validator.validate(dto);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("newQuantity"));
    }

    @Test
    void validate_withBlankPerformedBy_isRejected() {
        StockAdjustmentRequestDTO dto = new StockAdjustmentRequestDTO(
                UUID.randomUUID(), 10, "Conteo fisico", "Sin observaciones", "");

        Set<ConstraintViolation<StockAdjustmentRequestDTO>> violations = validator.validate(dto);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("performedBy"));
    }

    // BACK-009: mismo gap que StockMovementRequestDTO.performedBy (ver
    // StockMovementRequestDTOValidationTest) - performedBy sin @Size permitia superar
    // VARCHAR(255) y llegar hasta un DataIntegrityViolationException mal reportado como
    // "recurso duplicado".
    @Test
    void validate_withPerformedByExceeding255Characters_isRejected() {
        String longPerformedBy = "x".repeat(256);
        StockAdjustmentRequestDTO dto = new StockAdjustmentRequestDTO(
                UUID.randomUUID(), 10, "Conteo fisico", "Sin observaciones", longPerformedBy);

        Set<ConstraintViolation<StockAdjustmentRequestDTO>> violations = validator.validate(dto);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("performedBy"));
    }

    @Test
    void validate_withPerformedByAt255Characters_isAccepted() {
        String maxPerformedBy = "x".repeat(255);
        StockAdjustmentRequestDTO dto = new StockAdjustmentRequestDTO(
                UUID.randomUUID(), 10, "Conteo fisico", "Sin observaciones", maxPerformedBy);

        Set<ConstraintViolation<StockAdjustmentRequestDTO>> violations = validator.validate(dto);

        assertThat(violations).isEmpty();
    }
}
