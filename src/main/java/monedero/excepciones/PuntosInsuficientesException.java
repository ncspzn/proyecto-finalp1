package monedero.excepciones;

/** Regla de negocio: el cliente no tiene puntos suficientes para canjear un beneficio. */
public class PuntosInsuficientesException extends MonederoException {
    public PuntosInsuficientesException(int disponibles, int requeridos) {
        super("Puntos insuficientes: tienes " + disponibles + " y el beneficio cuesta " + requeridos + ".");
    }
}
