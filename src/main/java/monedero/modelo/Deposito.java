package monedero.modelo;

import monedero.excepciones.MonederoException;

import java.time.LocalDateTime;

/** Depósito: 1 punto por cada 100 unidades. */
public class Deposito extends Transaccion {
    public Deposito(Monedero monedero, double monto, String categoria) throws MonederoException {
        super(monedero, monto, 0.0, categoria, LocalDateTime.now());
    }

    @Override public TipoTransaccion getTipo() { return TipoTransaccion.DEPOSITO; }
    @Override protected int puntosPorCada100() { return 1; }

    @Override public void ejecutar() throws MonederoException {
        getMonedero().depositar(getMonto());
    }

    @Override protected void deshacer() throws MonederoException {
        getMonedero().retirar(getMonto());
    }
}
