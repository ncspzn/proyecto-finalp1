package monedero.excepciones;

/** Regla de negocio: el cliente, monedero o transacción buscado no existe. */
public class CuentaNoEncontradaException extends MonederoException {
    public CuentaNoEncontradaException(String recurso, String id) {
        super("No se encontró " + recurso + " con id: " + id);
    }
}
