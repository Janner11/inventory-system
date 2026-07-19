package com.inventario.unit;

import com.inventario.dto.StockMovementRequestDTO;
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

class StockMovementRequestDTOValidationTest {

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
        StockMovementRequestDTO dto = new StockMovementRequestDTO(
                UUID.randomUUID(), 5, "Reposicion", "Sin observaciones", "tester");

        Set<ConstraintViolation<StockMovementRequestDTO>> violations = validator.validate(dto);

        assertThat(violations).isEmpty();
    }

    @Test
    void validate_withZeroQuantity_isRejected() {
        StockMovementRequestDTO dto = new StockMovementRequestDTO(
                UUID.randomUUID(), 0, "Reposicion", "Sin observaciones", "tester");

        Set<ConstraintViolation<StockMovementRequestDTO>> violations = validator.validate(dto);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("quantity"));
    }

    @Test
    void validate_withBlankPerformedBy_isRejected() {
        StockMovementRequestDTO dto = new StockMovementRequestDTO(
                UUID.randomUUID(), 5, "Reposicion", "Sin observaciones", "");

        Set<ConstraintViolation<StockMovementRequestDTO>> violations = validator.validate(dto);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("performedBy"));
    }

    // BACK-009: performedBy no tenia @Size - un valor mas largo que VARCHAR(255) (columna
    // real de stock_movements, ver V4__create_stock_movements_table.sql) llegaba hasta la
    // base de datos y fallaba con un DataIntegrityViolationException generico, reportado
    // como "recurso duplicado" pese a no tener nada que ver con un duplicado. Solo
    // alcanzable llamando la API directamente (no es un campo editable en la UI), pero
    // sigue siendo una brecha real de validacion.
    @Test
    void validate_withPerformedByExceeding255Characters_isRejected() {
        String longPerformedBy = "x".repeat(256);
        StockMovementRequestDTO dto = new StockMovementRequestDTO(
                UUID.randomUUID(), 5, "Reposicion", "Sin observaciones", longPerformedBy);

        Set<ConstraintViolation<StockMovementRequestDTO>> violations = validator.validate(dto);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("performedBy"));
    }

    @Test
    void validate_withPerformedByAt255Characters_isAccepted() {
        String maxPerformedBy = "x".repeat(255);
        StockMovementRequestDTO dto = new StockMovementRequestDTO(
                UUID.randomUUID(), 5, "Reposicion", "Sin observaciones", maxPerformedBy);

        Set<ConstraintViolation<StockMovementRequestDTO>> violations = validator.validate(dto);

        assertThat(violations).isEmpty();
    }
}
