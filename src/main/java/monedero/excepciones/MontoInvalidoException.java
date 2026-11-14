package monedero.excepciones;

/** Regla de negocio: el monto debe ser un número positivo y dentro del límite permitido. */
public class MontoInvalidoException extends MonederoException {
    public MontoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
