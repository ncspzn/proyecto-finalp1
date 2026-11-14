package monedero.modelo;

import java.time.LocalDateTime;

/**
 * Movimiento en el historial de puntos. delta positivo = acumulación, negativo = canje o reversión.
 * esCanje distingue los puntos gastados (que no bajan el rango) de las reversiones (que sí).
 */
public record RegistroPuntos(LocalDateTime fecha, int delta, String motivo, boolean esCanje) { }
