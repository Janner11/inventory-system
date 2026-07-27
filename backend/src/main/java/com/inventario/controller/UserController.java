package com.inventario.controller;

import com.inventario.dto.UserRequestDTO;
import com.inventario.dto.UserResponseDTO;
import com.inventario.dto.UserStatusUpdateDTO;
import com.inventario.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Panel de administración de usuarios (SEC-007) — completa el scope {@code user:manage}
 * (sección 6 de CLAUDE.md), que existía en Keycloak desde SEC-001 sin ningún endpoint que
 * lo consumiera. Delega en {@link UserService}, que a su vez opera contra la Keycloak Admin
 * REST API — este controller no toca la base de datos del backend.
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Administración de usuarios (Keycloak Admin REST API)")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_user:manage')")
    @Operation(summary = "Listar los usuarios del realm con su rol asignado")
    @ApiResponse(responseCode = "200", description = "Lista de usuarios")
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sin permiso (scope insuficiente)", content = @Content)
    public List<UserResponseDTO> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('SCOPE_user:manage')")
    @Operation(summary = "Listar los roles asignables a un usuario nuevo")
    @ApiResponse(responseCode = "200", description = "Lista de roles")
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sin permiso (scope insuficiente)", content = @Content)
    public List<String> getAssignableRoles() {
        return UserService.ASSIGNABLE_ROLES;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_user:manage')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear un usuario y asignarle un rol")
    @ApiResponse(responseCode = "201", description = "Usuario creado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content)
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sin permiso (scope insuficiente)", content = @Content)
    @ApiResponse(responseCode = "409", description = "Ya existe un usuario con ese email", content = @Content)
    public UserResponseDTO createUser(@Valid @RequestBody UserRequestDTO request) {
        return userService.createUser(request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('SCOPE_user:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Habilitar o deshabilitar la cuenta de un usuario")
    @ApiResponse(responseCode = "204", description = "Estado actualizado")
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sin permiso (scope insuficiente)", content = @Content)
    @ApiResponse(responseCode = "404", description = "Usuario no encontrado", content = @Content)
    public void setUserEnabled(@PathVariable String id, @Valid @RequestBody UserStatusUpdateDTO request) {
        userService.setUserEnabled(id, request.enabled());
    }
}
