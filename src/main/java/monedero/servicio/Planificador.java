package monedero.servicio;

import monedero.excepciones.DatosInvalidosException;
import monedero.excepciones.MonederoException;
import monedero.modelo.Cliente;
import monedero.modelo.Frecuencia;
import monedero.modelo.Monedero;
import monedero.modelo.TipoTransaccion;
import monedero.modelo.TransaccionProgramada;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Procesa las transacciones programadas en orden de fecha de ejecución.
 * Usa un ordenamiento por mezcla (merge sort) propio: O(n log n) en todos los casos y estable
 * (si dos transacciones tienen la misma fecha conservan el orden en que se programaron).
 */
public class Planificador {
    private final Banco banco;
    private final List<TransaccionProgramada> programadas = new ArrayList<>();

    public Planificador(Banco banco) { this.banco = banco; }

    public TransaccionProgramada programar(Cliente c, TipoTransaccion tipo, double monto, Monedero origen,
                                           Monedero destino, Frecuencia frecuencia, LocalDate desde)
            throws MonederoException {
        Validador.validarMonto(monto);
        exigirProgramable(tipo, destino, desde);
        TransaccionProgramada tp = new TransaccionProgramada(c, tipo, monto, origen, destino, frecuencia, desde);
        programadas.add(tp);
        return tp;
    }

    /** Regla: sólo depósitos y transferencias; la transferencia exige destino; toda programación exige fecha. */
    private void exigirProgramable(TipoTransaccion tipo, Monedero destino, LocalDate desde)
            throws DatosInvalidosException {
        if (tipo == TipoTransaccion.RETIRO) {
            throw new DatosInvalidosException("Sólo se pueden programar depósitos y transferencias.");
        }
        if (tipo == TipoTransaccion.TRANSFERENCIA && destino == null) {
            throw new DatosInvalidosException("Selecciona el monedero destino de la transferencia.");
        }
        if (desde == null) {
            throw new DatosInvalidosException("Selecciona la fecha de ejecución.");
        }
    }

    /** Todas las programadas activas del cliente, ordenadas por fecha. */
    public List<TransaccionProgramada> ordenadasDe(Cliente c) {
        return mergeSort(programadas.stream()
                .filter(tp -> tp.isActiva() && tp.getCliente() == c)
                .toList());
    }

    /** Ejecuta, en orden de fecha, todo lo vencido a la fecha indicada. Devuelve un log legible. */
    public List<String> procesar(LocalDate hoy) {
        List<String> log = new ArrayList<>();
        List<TransaccionProgramada> pendientes = mergeSort(programadas.stream()
                .filter(tp -> tp.estaVencida(hoy))
                .toList());

        for (TransaccionProgramada tp : pendientes) {
            try {
                if (tp.getTipo() == TipoTransaccion.DEPOSITO) {
                    banco.depositar(tp.getCliente(), tp.getOrigen(), tp.getMonto(), "Programada");
                } else {
                    banco.transferir(tp.getCliente(), tp.getOrigen(), tp.getDestino().getId(), tp.getMonto(), "Programada");
                }
                log.add(String.format("OK  #%d %s de $%,.2f (%s)", tp.getId(), tp.getTipo(), tp.getMonto(),
                        tp.getCliente().getNombre()));
            } catch (MonederoException e) {
                log.add(String.format("ERROR #%d: %s", tp.getId(), e.getMessage()));
                banco.getNotificaciones().notificar(tp.getCliente(),
                        "No se pudo ejecutar la transacción programada #" + tp.getId() + ": " + e.getMessage());
            }
            tp.avanzar();
        }
        return log;
    }

    /** Recordatorios de lo que se ejecutará en los próximos días. Devuelve cuántos se enviaron. */
    public long enviarRecordatorios(LocalDate hoy, int dias) {
        List<TransaccionProgramada> proximas = programadas.stream()
                .filter(tp -> tp.isActiva() && !tp.getProximaEjecucion().isBefore(hoy)
                        && ChronoUnit.DAYS.between(hoy, tp.getProximaEjecucion()) <= dias)
                .toList();
        proximas.forEach(tp -> banco.getNotificaciones().notificar(tp.getCliente(),
                String.format("Recordatorio: el %s se ejecutará tu %s programado de $%,.2f.",
                        tp.getProximaEjecucion(), tp.getTipo(), tp.getMonto())));
        return proximas.size();
    }

    // ------------------------------------------------------------------ merge sort
    public static <T extends Comparable<? super T>> List<T> mergeSort(List<T> lista) {
        if (lista.size() <= 1) {
            return new ArrayList<>(lista);
        }
        int medio = lista.size() / 2;
        List<T> izq = mergeSort(lista.subList(0, medio));
        List<T> der = mergeSort(lista.subList(medio, lista.size()));
        return mezclar(izq, der);
    }

    private static <T extends Comparable<? super T>> List<T> mezclar(List<T> izq, List<T> der) {
        List<T> res = new ArrayList<>(izq.size() + der.size());
        int i = 0, j = 0;
        while (i < izq.size() && j < der.size()) {
            // <= mantiene la estabilidad: ante empate gana el elemento de la izquierda.
            res.add(izq.get(i).compareTo(der.get(j)) <= 0 ? izq.get(i++) : der.get(j++));
        }
        while (i < izq.size()) res.add(izq.get(i++));
        while (j < der.size()) res.add(der.get(j++));
        return res;
    }
}
