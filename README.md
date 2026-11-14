# Monedero Virtual con Puntos (Programación 1 – 2026-2)

## Ejecutar
- Interfaz JavaFX (requiere JDK 17+ y Maven):  `mvn javafx:run`
- Pruebas por consola (sin JavaFX):  `mvn compile exec:java -Dexec.mainClass=monedero.pruebas.PruebasManuales`

## Estructura
- `excepciones/`  5 excepciones personalizadas (checked) sobre una base `MonederoException`
- `modelo/`       Monedero (abstracta) → Ahorros/Gastos · Transaccion (abstracta) → Deposito/Retiro/Transferencia · Cliente · enums · interfaces
- `servicio/`     Banco (fachada), ServicioPuntos, DetectorAtipicas, Planificador (merge sort), ServicioReportes, ServicioNotificaciones, Validador
- `ui/`           MonederoApp (JavaFX)
- `pruebas/`      PruebasManuales (48 comprobaciones)
