package monedero.modelo;

import monedero.excepciones.MonederoException;

import java.time.LocalDateTime;

/** Retiro: 2 puntos por cada 100 unidades. Descuenta monto + comisión. */
public class Retiro extends Transaccion {
    public Retiro(Monedero monedero, double monto, double comision, String categoria) throws MonederoException {
        super(monedero, monto, comision, categoria, LocalDateTime.now());
    }

    @Override public TipoTransaccion getTipo() { return TipoTransaccion.RETIRO; }
    @Override protected int puntosPorCada100() { return 2; }
    @Override public boolean esGasto() { return true; }

    @Override public void ejecutar() throws MonederoException {
        getMonedero().retirar(getMonto() + getComision());
    }

    @Override protected void deshacer() throws MonederoException {
        getMonedero().depositar(getMonto() + getComision());
    }
}
