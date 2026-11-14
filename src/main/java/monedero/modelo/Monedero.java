package monedero.modelo;

import monedero.excepciones.MonederoException;
import monedero.excepciones.MontoInvalidoException;
import monedero.excepciones.SaldoInsuficienteException;
import monedero.servicio.Validador;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Clase abstracta que modela un monedero. El saldo está encapsulado (private):
 * sólo se modifica mediante depositar() y retirar(), que validan las reglas.
 * Las subclases definen su propio saldo mínimo y umbral de alerta (polimorfismo).
 */
public abstract class Monedero {
    private static final AtomicInteger SECUENCIA = new AtomicInteger(1);

    private final String id;
    private final String nombre;
    private final String idPropietario;
    private double saldo;

    protected Monedero(String nombre, String idPropietario) {
        this.id = String.format("M-%04d", SECUENCIA.getAndIncrement());
        this.nombre = nombre;
        this.idPropietario = idPropietario;
    }

    public abstract String getTipo();

    /** Saldo que siempre debe quedar en el monedero después de un retiro. */
    public abstract double getSaldoMinimo();

    /** Por debajo de este saldo se genera una alerta de saldo bajo. */
    public abstract double getUmbralSaldoBajo();

    public void depositar(double monto) throws MontoInvalidoException {
        Validador.validarMonto(monto);
        saldo += monto;
    }

    public void retirar(double monto) throws MonederoException {
        Validador.validarMonto(monto);
        exigirSaldoSuficiente(monto);
        saldo -= monto;
    }

    /** Regla: después de retirar debe quedar al menos el saldo mínimo del tipo de monedero. */
    private void exigirSaldoSuficiente(double monto) throws SaldoInsuficienteException {
        double disponible = saldo - getSaldoMinimo();
        if (monto > disponible + 1e-9) {
            throw new SaldoInsuficienteException(Math.max(disponible, 0), monto);
        }
    }

    public boolean tieneSaldoBajo() { return saldo < getUmbralSaldoBajo(); }

    public String getId() { return id; }
    public String getNombre() { return nombre; }
    public String getIdPropietario() { return idPropietario; }
    public double getSaldo() { return saldo; }

    @Override
    public String toString() {
        return String.format("%s [%s] – $%,.2f", nombre, getTipo(), saldo);
    }
}
