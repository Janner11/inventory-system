package com.inventario.unit;

import com.inventario.dto.ProductRequestDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRequestDTOValidationTest {

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
        ProductRequestDTO dto = new ProductRequestDTO(
                "Laptop", "LAP-001", "Laptop 15 pulgadas", "Electronica",
                new BigDecimal("999.99"), 10, 2);

        Set<ConstraintViolation<ProductRequestDTO>> violations = validator.validate(dto);

        assertThat(violations).isEmpty();
    }

    @Test
    void validate_withBlankName_isRejected() {
        ProductRequestDTO dto = new ProductRequestDTO(
                "", "LAP-001", "Laptop 15 pulgadas", "Electronica",
                new BigDecimal("999.99"), 10, 2);

        Set<ConstraintViolation<ProductRequestDTO>> violations = validator.validate(dto);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Test
    void validate_withZeroPrice_isRejected() {
        ProductRequestDTO dto = new ProductRequestDTO(
                "Laptop", "LAP-001", "Laptop 15 pulgadas", "Electronica",
                BigDecimal.ZERO, 10, 2);

        Set<ConstraintViolation<ProductRequestDTO>> violations = validator.validate(dto);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("price"));
    }

    @Test
    void validate_withNegativeQuantity_isRejected() {
        ProductRequestDTO dto = new ProductRequestDTO(
                "Laptop", "LAP-001", "Laptop 15 pulgadas", "Electronica",
                new BigDecimal("999.99"), -1, 2);

        Set<ConstraintViolation<ProductRequestDTO>> violations = validator.validate(dto);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("quantity"));
    }

    @Test
    void validate_withNegativeMinStock_isRejected() {
        ProductRequestDTO dto = new ProductRequestDTO(
                "Laptop", "LAP-001", "Laptop 15 pulgadas", "Electronica",
                new BigDecimal("999.99"), 10, -1);

        Set<ConstraintViolation<ProductRequestDTO>> violations = validator.validate(dto);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("minStock"));
    }
}
