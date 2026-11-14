package monedero.excepciones;

/** Excepción base (checked) de todas las reglas de negocio del monedero. */
public class MonederoException extends Exception {
    public MonederoException(String mensaje) {
        super(mensaje);
    }
}
