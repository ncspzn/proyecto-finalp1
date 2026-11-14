package monedero.modelo;

import monedero.excepciones.MonederoException;
import monedero.excepciones.MontoInvalidoException;
import monedero.servicio.Validador;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Clase abstracta base de todas las transacciones.
 * - Implementa Puntuable y Reversible (interfaces).
 * - revertir() es un método plantilla: valida y marca el estado; la forma de deshacer
 *   la define cada subclase en deshacer() (polimorfismo).
 */
public abstract class Transaccion implements Puntuable, Reversible {
    private static final AtomicInteger SECUENCIA = new AtomicInteger(1);

    private final int id;
    private final LocalDateTime fecha;
    private final double monto;
    private final double comision;
    private final Monedero monedero;
    private final String categoria;

    private int puntosGenerados;
    private boolean revertida;
    private boolean sospechosa;

    protected Transaccion(Monedero monedero, double monto, double comision, String categoria, LocalDateTime fecha)
            throws MonederoException {
        Validador.validarMonto(monto);
        exigirComisionValida(comision);
        this.id = SECUENCIA.getAndIncrement();
        this.monedero = monedero;
        this.monto = monto;
        this.comision = comision;
        this.categoria = (categoria == null || categoria.isBlank()) ? "General" : categoria.trim();
        this.fecha = fecha;
    }

    public abstract TipoTransaccion getTipo();

    /** Puntos que genera esta transacción por cada 100 unidades. */
    protected abstract int puntosPorCada100();

    /** Aplica la transacción sobre los monederos. */
    public abstract void ejecutar() throws MonederoException;

    /** Deshace los efectos de la transacción sobre los monederos. */
    protected abstract void deshacer() throws MonederoException;

    /** ¿Cuenta como gasto del cliente en los reportes? */
    public boolean esGasto() { return false; }

    @Override
    public int calcularPuntos(Rango rango) {
        int base = (int) (monto / 100) * puntosPorCada100();
        return (int) Math.round(base * (1 + bonoPuntos(rango)));
    }

    /** Bono extra de puntos según el rango del cliente. */
    public static double bonoPuntos(Rango rango) {
        return switch (rango) {
            case BRONCE -> 0.00;
            case PLATA -> 0.05;
            case ORO -> 0.10;
            case PLATINO -> 0.20;
        };
    }

    public int asignarPuntos(Rango rango) {
        puntosGenerados = calcularPuntos(rango);
        return puntosGenerados;
    }

    @Override
    public final void revertir() throws MonederoException {
        exigirNoRevertida();
        deshacer();
        revertida = true;
    }

    /** Regla: la comisión nunca puede ser negativa. */
    private static void exigirComisionValida(double comision) throws MontoInvalidoException {
        if (comision < 0) {
            throw new MontoInvalidoException("La comisión no puede ser negativa.");
        }
    }

    /** Regla: una transacción sólo se puede revertir una vez. */
    private void exigirNoRevertida() throws MonederoException {
        if (revertida) {
            throw new MonederoException("La transacción #" + id + " ya fue revertida.");
        }
    }

    @Override public boolean isRevertida() { return revertida; }

    public void marcarSospechosa() { this.sospechosa = true; }

    public int getId() { return id; }
    public LocalDateTime getFecha() { return fecha; }
    public double getMonto() { return monto; }
    public double getComision() { return comision; }
    public Monedero getMonedero() { return monedero; }
    public String getCategoria() { return categoria; }
    public int getPuntosGenerados() { return puntosGenerados; }
    public boolean isSospechosa() { return sospechosa; }
}
