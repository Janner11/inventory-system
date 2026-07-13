# Plantilla de Exploratory Charter (TEST-008)

Basada en el formato de "Session-Based Test Management" (SBTM) de James y Jonathan Bach,
que es básicamente el estándar para documentar testing exploratorio sin morir en el
intento.

Aquí te dejo la estructura que yo uso. Siéntete libre de tunearla, pero así me ha
funcionado.

Charter

<En una sola frase: ¿qué vas a explorar, con qué herramientas y qué tipo de problemas
buscas? Ejemplo: "Explorar los endpoints de stock con curl y Playwright para ver si
encuentro sobreventa o errores confusos".>
Área de la aplicación

<Endpoints, pantallas, flujos... lo que quepa dentro de la misión. No te pases de
ambicioso, recuerda que es una sesión de tiempo limitado.>
Duración

Inicio: <hora> — Fin: <hora> (~<N> min)

<Anota el tiempo real, no el planificado. Si te pasaste, está bien, solo déjalo por
escrito. A veces un hallazgo gordo merece la pena.>
Tester

<Tu nombre o el identificador de la sesión. Si te ayudó una IA, dilo también; es ser
transparente sobre cómo hiciste el trabajo.>
Entorno

<El stack concreto que usaste (docker-compose, versión de Keycloak, datos de prueba, etc.).
Así cualquiera puede reproducir lo que hiciste sin volverse loco.>
Notas de la sesión (cronológico)

<Esto es un diario de a bordo. Lo que probaste, paso a paso, sin limpiarlo después.
Aquí va todo: lo que salió bien, lo que salió mal y lo que te dejó pensando. Cuanto más
crudo, mejor. Escribo en primera persona y en el orden en que fui haciendo las pruebas.>
Bugs encontrados
[SEVERIDAD] Título corto y descriptivo

    Pasos para reproducir:

        <Paso 1>

        <Paso 2>

    Resultado esperado: <lo que yo creía que iba a pasar>

    Resultado real: <lo que realmente pasó>

    Evidencia: <comando, respuesta del servidor, screenshot si hace falta>

    Severidad: Critical / High / Medium / Low

    Estado: Abierto / Corregido en <commit o PR>

Lo que encontré (lo bueno)

<No todo es encontrar fallos. Si algo aguantó un ataque o un caso extremo con buena nota,
lo pongo aquí. Confirmar que algo funciona bien también es testing.>
Ideas de seguimiento (para la próxima sesión)

<Cosas que se te ocurrieron sobre la marcha pero que no te dio tiempo a probar, o que
prefieres dejar para otra sesión. Sirve de input para el próximo charter.>
Cobertura estimada

<Un porcentaje aproximado de lo que cubriste del área. Sirve para dimensionar el esfuerzo,
no para ser exacto.>


## Cómo la he usado yo (TEST-008)

En el proyecto le metí mano a tres charters con esta misma plantilla:
`docs/testing/exploratory-charter-01-auth.md`, `-02-forms.md` y `-03-stock.md`. Todos
siguen este esqueleto. Luego junté todo en un informe resumen
(`docs/testing/exploratory-testing-report.md`) con la lista de bugs y su severidad.

Siéntete libre de copiarla, modificarla y hacerla tuya. Lo importante es que cuando
alguien lea tu sesión sepa exactamente qué hiciste y por qué, sin tener que adivinarlo.