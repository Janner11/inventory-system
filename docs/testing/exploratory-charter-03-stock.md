# TEST-008 – Sesión 3: Movimientos de stock y concurrencia

## ¿Qué me propuse hacer?

Esta vez le tocaba el turno a los movimientos de stock: entradas, salidas y ajustes. Quería
forzar condiciones de carrera de verdad — varias peticiones al mismo tiempo sobre el mismo
producto — y también pasar valores límite de cantidad, para ver si aparecía alguna
sobreventa, estados inconsistentes o errores confusos.

## ¿Contra qué estaba probando?

El stack de siempre (`docker-compose.dev.yml` real, Keycloak, backend, Postgres). En la
mira estaban:
- `POST /api/stock/entry`, `/exit`, `/adjust`.
- La lógica del `StockService`.
- Y sobre todo el optimistic locking de `Product` (`@Version`, BACK-002) que ya habíamos
  confirmado que resistía manipulación de versión en el Charter 1, pero faltaba probarlo en
  un escenario de concurrencia real.

## Tiempo que le dediqué

37 minutos (de 20:58 a 21:35). Más corto de lo habitual, porque el experimento clave de
concurrencia salió a la primera y quedó clarísimo, sin necesidad de darle más vueltas.

Esta sesión también fue asistida por IA (Claude), bajo el identificador TEST-008.

---

## Lo que fui probando (paso a paso)

1.  **El caso control:** una salida de 999 unidades contra un producto con solo 10 en stock
    devolvió un `422` con mensaje clarísimo: "Stock insuficiente... disponible 10,
    solicitado 999". Bien.

2.  **El experimento central — concurrencia real de salidas.**
    Creé un producto con `quantity=10` y disparé **5 requests de salida verdaderamente
    concurrentes** (con `&`/`wait` de bash, no secuenciales), cada una pidiendo 3 unidades.
    En total, 15 unidades solicitadas contra 10 disponibles. Si hubiera una condición de
    carrera clásica (leer stock → verificar → escribir sin bloquear), cuatro de las cinco
    peticiones podrían ver "hay suficiente" antes de que ninguna actualizara, y terminar con
    stock negativo.  
    **Pero no**: 3 requests se completaron con éxito (10→7→4→1) y las otras 2 fallaron
    limpiamente con `409` y el mensaje *"El producto fue modificado por otro proceso,
    recargue e intente de nuevo"*. El optimistic locking de JPA (`@Version`) detectó la
    escritura concurrente y la rechazó en lugar de perderla. El stock final quedó en `1`,
    nunca negativo. Esto es justo lo que quería ver, y se detalla en "Lo que encontré (lo
    bueno)".

3.  Probé meter una cantidad negativa y cero en una entrada (`/stock/entry`). Ambos
    devolvieron `400` con mensaje claro (*"quantity: must be greater than 0"*).

4.  Movimiento contra un `productId` que no existe (UUID válido pero no en la base):
    `404` con mensaje específico. Perfecto.

5.  Ajuste con `newQuantity` negativo (`/stock/adjust`): `400` y mensaje claro.

6.  Registré una entrada sobre un producto que ya había sido desactivado (soft-delete
    previo). Recibí un `409` con un mensaje de negocio muy específico: *"…está inactivo y
    no admite movimientos de stock"*. Esto es destacable porque, a diferencia del mensaje
    genérico y engañoso del Bug #1 del Charter 2, acá sí se usa una excepción dedicada
    (`ProductInactiveException`) y no el handler genérico de `DataIntegrityViolationException`.
    Bien hecho.

7.  Caso límite: ajustar el stock al mismo valor actual (un no-op). El backend devuelve
    un `400` con mensaje claro, sin escaparse un `500` por una `IllegalArgumentException`
    sin capturar.

8.  Salida que vacía el producto por completo: justo la cantidad exacta disponible (dejar
    el stock en 0). Funcionó correctamente, sin que un error de comparación (`>` vs `>=`)
    lo tomara como "insuficiente". El límite exacto se maneja bien.

9.  **Desactivar un producto con stock existente.** Probé a hacerle soft-delete a un
    producto que todavía tenía 500 unidades. Se permitió sin ninguna advertencia. Revisé
    el diálogo de confirmación en el frontend (`ProductsTable.jsx`) y es genérico:
    *"¿Eliminar el producto X?"*, sin mencionar la cantidad en stock. Esto no es un fallo
    funcional (no hay regla de negocio que lo impida), pero sí una oportunidad de mejora
    de UX. Ver Bug #1 abajo.

10. Por último, revisé si `StockMovementForm.jsx` tenía el mismo patrón "sin tope
    superior" que encontramos en el precio durante el Charter 2. En los movimientos de
    stock, los campos `quantity`/`newQuantity` se mapean contra columnas `INTEGER` en la
    base, que coinciden exactamente con el tipo Java `Integer`. Así que no hay riesgo de
    truncamiento como con `NUMERIC(10,2)`. Acá no hay bug equivalente.

---

## Lo que encontré (lo malo)

### [LOW] Desactivar un producto con stock existente no muestra ninguna advertencia

- **Cómo lo reproduces:**
  1. Crea o utiliza un producto que tenga `quantity > 0` (por ejemplo, 500 unidades).
  2. En la UI, ve a Productos y pulsa "Eliminar" sobre ese producto.
- **Lo que podría esperarse:** que apareciera un aviso adicional, algo como *"Este
  producto tiene 500 unidades en stock. ¿Estás seguro de que deseas desactivarlo?"*. Sería
  una protección sencilla contra desactivaciones accidentales de productos que todavía
  están en uso activo.
- **Lo que pasa en realidad:** el diálogo de confirmación es siempre el mismo
  (*"¿Eliminar el producto X?"*), sin importar si el stock es 0 o 500. La API lo permite
  sin ninguna validación extra.
- **Severidad:** Low. No es un defecto funcional (ADR-001 no exige que el stock sea 0
  antes de desactivar, y el historial de movimientos se conserva vía soft-delete). Es una
  oportunidad de mejora de UX para evitar errores del operador.
- **Estado:** Abierto. Sugerencia: si el negocio lo ve útil, modificar
  `ProductsTable.jsx` para que, cuando `quantity > 0`, el mensaje de confirmación incluya
  ese dato y pida una doble verificación.

---

## Lo que encontré (lo bueno)

- **Lo más importante de las 3 sesiones**: con concurrencia real (5 requests simultáneas de
  salida que excedían el stock disponible), el sistema **jamás permitió sobreventa**. El
  optimistic locking de JPA rechazó limpiamente las escrituras en conflicto con un `409` y
  un mensaje claro, en lugar de perderlas o dejar el stock en un estado inconsistente o
  negativo. Esto confirma en un escenario vivo (no solo unitario) lo que ya se había
  insinuado en el test de estrés con k6 (TEST-006).
- Cantidad negativa/cero en entrada, `newQuantity` negativo en ajuste, movimiento sobre
  producto inexistente, ajuste no-op: todos responden con el código HTTP correcto (400/404)
  y un mensaje específico y útil. Sin fugas de `500` por excepciones sin capturar.
- A diferencia del mensaje genérico y engañoso del Charter 2, los movimientos sobre un
  producto inactivo se rechazan con un mensaje de negocio dedicado y clarísimo.
- El límite exacto (salida que deja el stock justo en 0) se maneja sin errores de
  comparación estricta vs. no-estricta.

---

## Qué dejaría para la próxima sesión

- Probar concurrencia mixta: ENTRY, EXIT y ADJUSTMENT simultáneos sobre el mismo producto,
  no solo EXIT contra EXIT, para confirmar que el optimistic locking aguanta cuando se
  mezclan distintos tipos de movimiento.
- Simular que dos usuarios abren el formulario de edición del MISMO producto al mismo
  tiempo en el frontend. ¿El segundo que guarda ve un error claro o su cambio se pierde
  sin aviso?
- Medir cuántos reintentos automáticos hace el frontend (si es que los hay) cuando recibe
  un `409` por conflicto de concurrencia en el formulario de stock, y si el usuario se
  entera de que su petición original fue rechazada.

## ¿Qué tanto cubrí?

Alrededor del 55% del área de flujos de stock. Quedó cubierto a fondo: concurrencia real
en salidas, límites de cantidad, productos inactivos e inexistentes. Me quedé sin probar la
concurrencia mixta y el comportamiento del frontend frente a conflictos de concurrencia
entre dos sesiones de navegador reales.

## Nota final

Los tres productos de prueba que creé durante la sesión (`CHARTER3-CONCURRENCY-001`,
`CHARTER3-INACTIVE-001`, `CHARTER3-DELETE-WITH-STOCK-001`) quedaron desactivados (dos por
el propio flujo de prueba y uno por limpieza manual al terminar). Así que no molestan en
el listado de productos activos.