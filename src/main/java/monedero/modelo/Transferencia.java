package monedero.modelo;

import monedero.excepciones.DatosInvalidosException;
import monedero.excepciones.MonederoException;

import java.time.LocalDateTime;

/** Transferencia: 3 puntos por cada 100 unidades. El origen paga monto + comisión. */
public class Transferencia extends Transaccion {
    private final Monedero destino;

    public Transferencia(Monedero origen, Monedero destino, double monto, double comision, String categoria)
            throws MonederoException {
        super(origen, monto, comision, categoria, LocalDateTime.now());
        exigirDestinoDistinto(origen, destino);
        this.destino = destino;
    }

    /** Regla: no se puede transferir de un monedero a sí mismo. */
    private static void exigirDestinoDistinto(Monedero origen, Monedero destino) throws DatosInvalidosException {
        if (origen.getId().equals(destino.getId())) {
            throw new DatosInvalidosException("El monedero de origen y destino no pueden ser el mismo.");
        }
    }

    public Monedero getDestino() { return destino; }

    @Override public TipoTransaccion getTipo() { return TipoTransaccion.TRANSFERENCIA; }
    @Override protected int puntosPorCada100() { return 3; }
    @Override public boolean esGasto() { return true; }

    @Override public void ejecutar() throws MonederoException {
        getMonedero().retirar(getMonto() + getComision());
        destino.depositar(getMonto());
    }

    @Override protected void deshacer() throws MonederoException {
        destino.retirar(getMonto());
        getMonedero().depositar(getMonto() + getComision());
    }
}
