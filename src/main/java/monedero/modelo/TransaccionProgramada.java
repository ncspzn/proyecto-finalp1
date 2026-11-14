package monedero.modelo;

import java.time.LocalDate;

/** Transacción recurrente (depósito automático o transferencia periódica). Se ordena por fecha. */
public class TransaccionProgramada implements Comparable<TransaccionProgramada> {
    private static int secuencia = 1;

    private final int id;
    private final Cliente cliente;
    private final TipoTransaccion tipo;
    private final double monto;
    private final Monedero origen;
    private final Monedero destino;   // null en depósitos
    private final Frecuencia frecuencia;
    private LocalDate proximaEjecucion;
    private boolean activa = true;

    public TransaccionProgramada(Cliente cliente, TipoTransaccion tipo, double monto, Monedero origen,
                                 Monedero destino, Frecuencia frecuencia, LocalDate primeraEjecucion) {
        this.id = secuencia++;
        this.cliente = cliente;
        this.tipo = tipo;
        this.monto = monto;
        this.origen = origen;
        this.destino = destino;
        this.frecuencia = frecuencia;
        this.proximaEjecucion = primeraEjecucion;
    }

    /** Avanza a la siguiente fecha; las de ejecución única se desactivan. */
    public void avanzar() {
        if (frecuencia == Frecuencia.UNICA) {
            activa = false;
        } else {
            proximaEjecucion = siguienteFecha(frecuencia, proximaEjecucion);
        }
    }

    /** Siguiente fecha de ejecución según la frecuencia. */
    public static LocalDate siguienteFecha(Frecuencia frecuencia, LocalDate desde) {
        return switch (frecuencia) {
            case UNICA -> desde;
            case DIARIA -> desde.plusDays(1);
            case SEMANAL -> desde.plusWeeks(1);
            case MENSUAL -> desde.plusMonths(1);
        };
    }

    public boolean estaVencida(LocalDate hoy) { return activa && !proximaEjecucion.isAfter(hoy); }

    @Override
    public int compareTo(TransaccionProgramada otra) {
        return proximaEjecucion.compareTo(otra.proximaEjecucion);
    }

    public int getId() { return id; }
    public Cliente getCliente() { return cliente; }
    public TipoTransaccion getTipo() { return tipo; }
    public double getMonto() { return monto; }
    public Monedero getOrigen() { return origen; }
    public Monedero getDestino() { return destino; }
    public Frecuencia getFrecuencia() { return frecuencia; }
    public LocalDate getProximaEjecucion() { return proximaEjecucion; }
    public boolean isActiva() { return activa; }
}
