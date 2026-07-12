La causa: `ProductRequestDTO.price` no tiene `@Digits(integer=8, fraction=2)`;
`StockMovementRequestDTO.performedBy` y `StockAdjustmentRequestDTO.performedBy` no
tienen `@Size(max=255)`. Y `GlobalExceptionHandler.handleDataIntegrityViolation`
siempre manda el mismo mensaje fijo, sin mirar la causa SQL real.
- **Severidad:** Medium. Para `price`, un usuario normal puede ver el mensaje confuso y
no saber qué corregir. Para `performedBy`, solo alguien que toque la API directamente
(Postman, otro cliente) se lo encuentra, pero sigue siendo una brecha en la validación.
- **Estado:** Abierto. Sugiero agregar `@Digits(integer=8, fraction=2)` en
`ProductRequestDTO.price`, `@Size(max=255)` en los dos `performedBy`, y si se quiere
más defensa, que el `GlobalExceptionHandler` inspeccione el `SQLState` para dar
mensajes más específicos en estos casos.

## Lo que encontré (lo bueno)

Cosas que están blindadas y vale la pena anotar:

- El frontend escapa todo el HTML de forma consistente y no usa `dangerouslySetInnerHTML`
en ningún rincón; cero ejecución de scripts inyectados.
- Las consultas SQL están parametrizadas; los payloads de inyección se tratan como texto
literal, sin afectar la base de datos.
- `@NotBlank` de Jakarta Validation pilla correctamente cadenas de solo espacios.
- Los campos que sí tienen `@Size` (`reason`, `name`, `sku`, `category`, `description`)
devuelven errores claros y precisos cuando se pasan de largo. El fallo está localizado
solo en los dos DTOs sin esas anotaciones.

## Qué dejaría para la próxima sesión

- Auditar el formulario de movimientos de stock (`StockMovementForm.jsx`) por el mismo
patrón de "sin tope superior" en `quantity` y `newQuantity`.
- Probar inyectar caracteres de control o null bytes (`\0`) en los campos de texto.
- Ver qué pasa si se deshabilita JavaScript en el navegador y se depende solo de la
validación del backend (¿sigue siendo suficiente?).

## ¿Qué tanto cubrí?

Un ~50% del área de formularios y validaciones. Bien cubierto: XSS, SQLi, valores
extremos en productos y longitud de campos en stock. Sin cubrir: el formulario de ajuste
de stock más allá de leer su código, caracteres de control/null bytes, y el
comportamiento sin JavaScript.

## Nota final

Los 14 productos de prueba que creé en esta sesión (con prefijos como `XSS-TEST-*` y
`PRICE-EXTREME-*`) los desactivé con un soft-delete (`status='INACTIVE'`) al terminar,
para no dejar basura en el listado activo. Todo limpio.