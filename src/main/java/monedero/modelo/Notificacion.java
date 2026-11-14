package monedero.modelo;

import java.time.LocalDateTime;

public record Notificacion(LocalDateTime fecha, CanalNotificacion canal, String mensaje) { }
