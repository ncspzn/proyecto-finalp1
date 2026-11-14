package monedero.servicio;

import monedero.modelo.Cliente;
import monedero.modelo.TipoTransaccion;
import monedero.modelo.Transaccion;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Reportes y análisis de patrones de gasto, todo con lambdas y Stream API. */
public class ServicioReportes {

    public Map<TipoTransaccion, Double> totalPorTipo(List<Transaccion> h) {
        return h.stream().filter(t -> !t.isRevertida())
                .collect(Collectors.groupingBy(Transaccion::getTipo,
                        () -> new EnumMap<>(TipoTransaccion.class),
                        Collectors.summingDouble(Transaccion::getMonto)));
    }

    public Map<String, Double> gastoPorCategoria(List<Transaccion> h) {
        return h.stream().filter(t -> !t.isRevertida() && t.esGasto())
                .collect(Collectors.groupingBy(Transaccion::getCategoria,
                        Collectors.summingDouble(Transaccion::getMonto)));
    }

    public Map<YearMonth, Double> gastoMensual(List<Transaccion> h) {
        return h.stream().filter(t -> !t.isRevertida() && t.esGasto())
                .collect(Collectors.groupingBy(t -> YearMonth.from(t.getFecha()),
                        TreeMap::new,
                        Collectors.summingDouble(Transaccion::getMonto)));
    }

    public Optional<Transaccion> mayorGasto(List<Transaccion> h) {
        return h.stream().filter(t -> !t.isRevertida() && t.esGasto())
                .max(Comparator.comparingDouble(Transaccion::getMonto));
    }

    public double promedioGasto(List<Transaccion> h) {
        return h.stream().filter(t -> !t.isRevertida() && t.esGasto())
                .mapToDouble(Transaccion::getMonto).average().orElse(0);
    }

    public double totalComisiones(List<Transaccion> h) {
        return h.stream().filter(t -> !t.isRevertida()).mapToDouble(Transaccion::getComision).sum();
    }

    /** Filtro por tipo (null = todos) y monto mínimo. */
    public List<Transaccion> filtrar(List<Transaccion> h, TipoTransaccion tipo, double montoMinimo) {
        return h.stream()
                .filter(t -> tipo == null || t.getTipo() == tipo)
                .filter(t -> t.getMonto() >= montoMinimo)
                .toList();
    }

    /** Informe de texto con el patrón de gasto del cliente. */
    public String informe(Cliente c, List<Transaccion> h, DetectorAtipicas detector) {
        StringBuilder sb = new StringBuilder();
        sb.append("INFORME DE ").append(c.getNombre().toUpperCase()).append("\n");
        sb.append(String.format("Saldo total: $%,.2f | Rango: %s | Puntos: %d%n%n",
                c.getSaldoTotal(), c.getRango(), c.getPuntosDisponibles()));

        sb.append("Totales por tipo:\n");
        totalPorTipo(h).forEach((tipo, total) -> sb.append(String.format("  %-14s $%,.2f%n", tipo, total)));

        sb.append("\nGasto por categoría:\n");
        gastoPorCategoria(h).entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(e -> sb.append(String.format("  %-14s $%,.2f%n", e.getKey(), e.getValue())));

        sb.append("\nGasto mensual:\n");
        gastoMensual(h).forEach((mes, total) -> sb.append(String.format("  %s  $%,.2f%n", mes, total)));

        sb.append(String.format("%nPromedio por gasto: $%,.2f%n", promedioGasto(h)));
        mayorGasto(h).ifPresent(t -> sb.append(String.format("Mayor gasto: #%d $%,.2f (%s)%n",
                t.getId(), t.getMonto(), t.getCategoria())));
        sb.append(String.format("Comisiones pagadas: $%,.2f%n", totalComisiones(h)));

        sb.append("\nANÁLISIS DE ATÍPICAS (promedio + 2σ, por tipo de transacción):\n");
        for (TipoTransaccion tipo : TipoTransaccion.values()) {
            sb.append("  ").append(tipo).append(": ");
            detector.calcular(c.getTransacciones(), tipo).ifPresentOrElse(
                    e -> sb.append(String.format("promedio $%,.2f | desv. $%,.2f | umbral $%,.2f (n=%d)%n",
                            e.promedio(), e.desviacion(), e.umbral(), e.n())),
                    () -> sb.append("sin muestra suficiente (mínimo ")
                            .append(DetectorAtipicas.MUESTRA_MINIMA).append(")\n"));
        }
        List<Transaccion> atipicas = detector.detectar(c);
        if (atipicas.isEmpty()) {
            sb.append("  Sin transacciones atípicas.\n");
        } else {
            atipicas.forEach(t -> sb.append(String.format("  ⚠ #%d %s $%,.2f%n", t.getId(), t.getTipo(), t.getMonto())));
        }
        return sb.toString();
    }
}
