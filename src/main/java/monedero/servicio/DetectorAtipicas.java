package monedero.servicio;

import monedero.modelo.Cliente;
import monedero.modelo.TipoTransaccion;
import monedero.modelo.Transaccion;

import java.util.List;
import java.util.Optional;
import java.util.stream.DoubleStream;

/**
 * Detección de transacciones atípicas (regla de las 2 desviaciones estándar).
 *
 * Una transacción es atípica si monto > promedio + 2 × desviación estándar.
 *
 * Justificación:
 *  - Si los montos de un cliente se distribuyen de forma aproximada normal, ~95% quedan dentro de
 *    2σ de la media, así que un monto por encima del umbral es raro (~2.5%) y merece revisión.
 *  - Se usa la desviación poblacional (dividir entre n) porque el historial del cliente es todo
 *    el universo que se analiza, no una muestra de otro conjunto.
 *  - Se exige una muestra mínima: con pocos datos la desviación no es confiable.
 *  - Las estadísticas se calculan POR TIPO de transacción: un salario depositado de 5.000 no debe
 *    inflar la desviación con la que se juzgan retiros de ~50 (comparar manzanas con manzanas).
 *  - Al evaluar una transacción NUEVA se usa el historial previo, para que la propia operación
 *    atípica no infle la media y la desviación y se "esconda" a sí misma.
 *
 * Complejidad temporal (n = transacciones del cliente):
 *  - calcular(): promedio O(n) + varianza O(n) = O(n).
 *  - esAtipica(): O(n) (calcula estadísticas) + O(1) comparación = O(n).
 *  - detectar(): 3 tipos × (O(n) estadísticas + O(n) filtrado) = O(n) (3 es constante).
 *  Espacio: O(n) por el arreglo de montos.
 */
public class DetectorAtipicas {
    public static final int MUESTRA_MINIMA = 5;
    public static final double FACTOR_DESVIACION = 2.0;

    public record Estadisticas(double promedio, double desviacion, double umbral, int n) { }

    public Optional<Estadisticas> calcular(List<Transaccion> historial, TipoTransaccion tipo) {
        double[] montos = historial.stream()
                .filter(t -> !t.isRevertida() && t.getTipo() == tipo)
                .mapToDouble(Transaccion::getMonto)
                .toArray();
        if (montos.length < MUESTRA_MINIMA) {
            return Optional.empty();
        }
        double promedio = DoubleStream.of(montos).average().orElse(0);
        double varianza = DoubleStream.of(montos)
                .map(m -> (m - promedio) * (m - promedio))
                .sum() / montos.length;
        double desviacion = Math.sqrt(varianza);
        return Optional.of(new Estadisticas(promedio, desviacion,
                promedio + FACTOR_DESVIACION * desviacion, montos.length));
    }

    /** ¿Sería atípica una nueva operación de este monto, dado el historial actual? */
    public boolean esAtipica(Cliente cliente, TipoTransaccion tipo, double monto) {
        return calcular(cliente.getTransacciones(), tipo)
                .map(e -> monto > e.umbral())
                .orElse(false);
    }

    /** Recorre todo el historial y devuelve las transacciones que superan el umbral de su tipo. */
    public List<Transaccion> detectar(Cliente cliente) {
        return java.util.Arrays.stream(TipoTransaccion.values())
                .flatMap(tipo -> calcular(cliente.getTransacciones(), tipo)
                        .map(e -> cliente.getTransacciones().stream()
                                .filter(t -> !t.isRevertida() && t.getTipo() == tipo && t.getMonto() > e.umbral()))
                        .orElseGet(java.util.stream.Stream::empty))
                .sorted(java.util.Comparator.comparingInt(Transaccion::getId))
                .toList();
    }
}
