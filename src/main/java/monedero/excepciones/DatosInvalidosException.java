package monedero.excepciones;

/** Validación de integridad: nombre, correo, teléfono u otros datos mal formados. */
public class DatosInvalidosException extends MonederoException {
    public DatosInvalidosException(String mensaje) {
        super(mensaje);
    }
}
