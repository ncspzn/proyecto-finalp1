package monedero.servicio;

import monedero.excepciones.DatosInvalidosException;
import monedero.excepciones.MontoInvalidoException;

import java.util.regex.Pattern;

/** Validaciones centralizadas para garantizar la integridad de los datos. */
public final class Validador {
    public static final double MONTO_MAXIMO = 100_000_000.0;

    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final Pattern TELEFONO = Pattern.compile("^\\+?\\d{7,15}$");

    private Validador() { }

    public static void validarMonto(double monto) throws MontoInvalidoException {
        if (Double.isNaN(monto) || Double.isInfinite(monto)) {
            throw new MontoInvalidoException("El monto no es un número válido.");
        }
        if (monto <= 0) {
            throw new MontoInvalidoException("El monto debe ser mayor que cero.");
        }
        if (monto > MONTO_MAXIMO) {
            throw new MontoInvalidoException(String.format("El monto excede el límite de $%,.0f.", MONTO_MAXIMO));
        }
    }

    public static String validarNombre(String nombre) throws DatosInvalidosException {
        if (nombre == null || nombre.isBlank() || nombre.trim().length() < 2) {
            throw new DatosInvalidosException("El nombre debe tener al menos 2 caracteres.");
        }
        return nombre.trim();
    }

    public static String validarEmail(String email) throws DatosInvalidosException {
        if (email == null || !EMAIL.matcher(email.trim()).matches()) {
            throw new DatosInvalidosException("El correo electrónico no tiene un formato válido.");
        }
        return email.trim();
    }

    public static String validarTelefono(String telefono) throws DatosInvalidosException {
        if (telefono == null || !TELEFONO.matcher(telefono.trim()).matches()) {
            throw new DatosInvalidosException("El teléfono debe tener entre 7 y 15 dígitos.");
        }
        return telefono.trim();
    }

    /** Convierte el texto de un campo a monto, aceptando coma o punto decimal. */
    public static double parsearMonto(String texto) throws MontoInvalidoException {
        if (texto == null || texto.isBlank()) {
            throw new MontoInvalidoException("Ingresa un monto.");
        }
        try {
            double monto = Double.parseDouble(texto.trim().replace(',', '.'));
            validarMonto(monto);
            return monto;
        } catch (NumberFormatException e) {
            throw new MontoInvalidoException("El monto debe ser numérico.");
        }
    }
}
