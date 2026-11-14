package monedero.servicio;

import monedero.excepciones.MonederoException;
import monedero.excepciones.PuntosInsuficientesException;
import monedero.modelo.Beneficio;
import monedero.modelo.Cliente;
import monedero.modelo.Monedero;
import monedero.modelo.Transaccion;

import java.time.LocalDate;

/** Acumulación, reversión y canje de puntos. */
public class ServicioPuntos {
    public static final double VALOR_BONO = 50.0;
    public static final int DIAS_VIGENCIA = 30;

    public static int costoDe(Beneficio b) {
        return switch (b) {
            case DESCUENTO_TRANSFERENCIA -> 100;
            case MES_SIN_RETIROS -> 500;
            case BONO_SALDO -> 1000;
        };
    }

    public static String descripcionDe(Beneficio b) {
        return switch (b) {
            case DESCUENTO_TRANSFERENCIA -> "10% menos en la comisión de transferencias (30 días)";
            case MES_SIN_RETIROS -> "Un mes sin cargos por retiros (30 días)";
            case BONO_SALDO -> "Bono de 50 unidades en el monedero";
        };
    }

    /** Asigna a la transacción sus puntos (con el bono del rango) y los registra en el cliente. */
    public int acreditar(Cliente cliente, Transaccion t) {
        int puntos = t.asignarPuntos(cliente.getRango());
        if (puntos > 0) {
            cliente.registrarPuntos(Cliente.movimiento(puntos,
                    "Transacción #" + t.getId() + " (" + t.getTipo() + ")", false));
        }
        return puntos;
    }

    /** Al revertir una transacción se descuentan los puntos que había generado. */
    public void revertir(Cliente cliente, Transaccion t) {
        int puntos = t.getPuntosGenerados();
        if (puntos > 0) {
            cliente.registrarPuntos(Cliente.movimiento(-puntos,
                    "Reversión de la transacción #" + t.getId(), false));
        }
    }

    public void canjear(Cliente cliente, Beneficio beneficio, Monedero destinoBono) throws MonederoException {
        exigirPuntosSuficientes(cliente, beneficio);
        switch (beneficio) {
            case BONO_SALDO -> destinoBono.depositar(VALOR_BONO);
            case DESCUENTO_TRANSFERENCIA, MES_SIN_RETIROS ->
                    cliente.activarBeneficio(beneficio, LocalDate.now().plusDays(DIAS_VIGENCIA));
        }
        cliente.registrarPuntos(Cliente.movimiento(-costoDe(beneficio),
                "Canje: " + descripcionDe(beneficio), true));
    }

    /** Regla: el cliente debe tener puntos disponibles para cubrir el costo del beneficio. */
    private void exigirPuntosSuficientes(Cliente cliente, Beneficio beneficio) throws PuntosInsuficientesException {
        int disponibles = cliente.getPuntosDisponibles();
        if (disponibles < costoDe(beneficio)) {
            throw new PuntosInsuficientesException(disponibles, costoDe(beneficio));
        }
    }
}
