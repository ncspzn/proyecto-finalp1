package monedero.excepciones;

/** Regla de negocio: no se puede retirar/transferir más de lo disponible. */
public class SaldoInsuficienteException extends MonederoException {
    private final double disponible;
    private final double requerido;

    public SaldoInsuficienteException(double disponible, double requerido) {
        super(String.format("Saldo insuficiente: disponible $%,.2f, requerido $%,.2f", disponible, requerido));
        this.disponible = disponible;
        this.requerido = requerido;
    }

    public double getDisponible() { return disponible; }
    public double getRequerido() { return requerido; }
}
