package monedero.modelo;

/** Todo lo que puede recibir notificaciones del sistema. */
public interface Notificable {
    void recibirNotificacion(Notificacion notificacion);
    CanalNotificacion getCanalPreferido();
}
