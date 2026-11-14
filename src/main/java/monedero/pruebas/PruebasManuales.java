package monedero.pruebas;

import monedero.excepciones.*;
import monedero.modelo.*;
import monedero.servicio.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Pruebas manuales por consola (sin dependencias). Ejecutar con:
 *   mvn compile exec:java -Dexec.mainClass=monedero.pruebas.PruebasManuales
 * o directamente desde el IDE. Cada caso imprime OK o FALLA.
 */
public class PruebasManuales {
    private static int ok = 0, fallas = 0;

    public static void main(String[] args) throws Exception {
        Banco banco = new Banco();
        Cliente ana = banco.registrarCliente("Ana", "ana@correo.com", "3001234567");
        Cliente luis = banco.registrarCliente("Luis", "luis@correo.com", "3109876543");
        Monedero gastosAna = ana.getMonederoPrincipal();
        Monedero gastosLuis = luis.getMonederoPrincipal();

        // ---- Depósitos, retiros y saldo
        banco.depositar(ana, gastosAna, 1000, "Salario");
        check("Depósito aumenta el saldo", gastosAna.getSaldo() == 1000);
        check("Depósito de 1000 da 10 puntos", ana.getPuntosDisponibles() == 10);

        banco.retirar(ana, gastosAna, 200, "Comida");
        check("Retiro descuenta monto + 1% de comisión", Math.abs(gastosAna.getSaldo() - 798) < 1e-9);
        check("Retiro de 200 suma 4 puntos (total 14)", ana.getPuntosDisponibles() == 14);

        // ---- Excepciones personalizadas
        lanza("Saldo insuficiente", SaldoInsuficienteException.class, () -> banco.retirar(ana, gastosAna, 5000, "X"));
        lanza("Monto negativo", MontoInvalidoException.class, () -> banco.depositar(ana, gastosAna, -5, "X"));
        lanza("Monto cero", MontoInvalidoException.class, () -> banco.depositar(ana, gastosAna, 0, "X"));
        lanza("Monedero destino inexistente", CuentaNoEncontradaException.class,
                () -> banco.transferir(ana, gastosAna, "M-9999", 10, "X"));
        lanza("Canje sin puntos", PuntosInsuficientesException.class,
                () -> banco.canjear(ana, Beneficio.BONO_SALDO, gastosAna));
        lanza("Correo inválido", DatosInvalidosException.class,
                () -> banco.registrarCliente("Pepe", "no-es-correo", "3001234567"));
        lanza("Monedero ajeno", DatosInvalidosException.class,
                () -> banco.depositar(ana, gastosLuis, 10, "X"));
        check("Una operación fallida no deja rastro en el historial", ana.getTransacciones().size() == 2);

        // ---- Transferencias
        double saldoLuisAntes = gastosLuis.getSaldo();
        Transaccion tr = banco.transferir(ana, gastosAna, gastosLuis.getId(), 300, "Préstamo");
        check("Transferencia: comisión 2% en Bronce", Math.abs(tr.getComision() - 6.0) < 1e-9);
        check("Transferencia acredita al destino", Math.abs(gastosLuis.getSaldo() - saldoLuisAntes - 300) < 1e-9);
        check("Transferencia de 300 da 9 puntos", tr.getPuntosGenerados() == 9);
        check("El receptor ve la transferencia en su historial",
                banco.historialCompleto(luis).contains(tr));
        check("El receptor recibió una notificación", !luis.getNotificaciones().isEmpty());

        // ---- Reversión y recálculo de puntos
        int antes = ana.getPuntosDisponibles();
        banco.revertir(ana, tr.getId());
        check("Revertir devuelve el dinero", Math.abs(gastosAna.getSaldo() - 798) < 1e-9);
        check("Revertir recalcula los puntos (-9)", ana.getPuntosDisponibles() == antes - 9);
        lanza("No se puede revertir dos veces", MonederoException.class, () -> banco.revertir(ana, tr.getId()));

        // ---- Rangos
        check("Rango 0 -> Bronce", Cliente.calcularRango(0) == Rango.BRONCE);
        check("Rango 500 -> Bronce", Cliente.calcularRango(500) == Rango.BRONCE);
        check("Rango 501 -> Plata", Cliente.calcularRango(501) == Rango.PLATA);
        check("Rango 1000 -> Plata", Cliente.calcularRango(1000) == Rango.PLATA);
        check("Rango 1001 -> Oro", Cliente.calcularRango(1001) == Rango.ORO);
        check("Rango 5000 -> Oro", Cliente.calcularRango(5000) == Rango.ORO);
        check("Rango 5001 -> Platino", Cliente.calcularRango(5001) == Rango.PLATINO);

        // ---- Canje de puntos y subida de rango
        Cliente rico = banco.registrarCliente("Rico", "rico@correo.com", "3201112233");
        banco.depositar(rico, rico.getMonederoPrincipal(), 50_000, "Herencia");  // 500 puntos
        check("Rico: 500 puntos y sigue en Bronce", rico.getPuntosDisponibles() == 500 && rico.getRango() == Rango.BRONCE);
        banco.depositar(rico, rico.getMonederoPrincipal(), 10_000, "Otro");      // +100 = 600
        check("Rico sube a Plata", rico.getRango() == Rango.PLATA);
        double comisionAntes = banco.calcularComisionTransferencia(rico, 1000);
        banco.canjear(rico, Beneficio.DESCUENTO_TRANSFERENCIA, null);
        double comisionDespues = banco.calcularComisionTransferencia(rico, 1000);
        check("Plata: comisión 2% con 10% de descuento = 18", Math.abs(comisionAntes - 18.0) < 1e-9);
        check("Canje 100 pts reduce otro 10% (16.20)", Math.abs(comisionDespues - 16.2) < 1e-9);
        check("El canje gasta puntos pero NO baja el rango",
                rico.getPuntosDisponibles() == 500 && rico.getRango() == Rango.PLATA);
        double saldoAntes = rico.getMonederoPrincipal().getSaldo();
        banco.canjear(rico, Beneficio.MES_SIN_RETIROS, null);
        Transaccion sinCargo = banco.retirar(rico, rico.getMonederoPrincipal(), 100, "Test");
        check("Mes sin cargos por retiros: comisión 0", sinCargo.getComision() == 0.0);
        check("Retiro sin cargo descuenta sólo el monto", Math.abs(rico.getMonederoPrincipal().getSaldo() - (saldoAntes - 100)) < 1e-9);

        // ---- Detección de atípicas
        Cliente tipico = banco.registrarCliente("Tipico", "tipico@correo.com", "3004445566");
        Monedero mt = tipico.getMonederoPrincipal();
        banco.depositar(tipico, mt, 5000, "Salario");
        for (double g : new double[]{50, 60, 55, 45, 52, 48}) banco.retirar(tipico, mt, g, "Comida");
        List<Transaccion> hist = tipico.getTransacciones();
        DetectorAtipicas det = banco.getDetector();
        check("Se calculan estadísticas de retiros con >= 5 retiros", det.calcular(hist, TipoTransaccion.RETIRO).isPresent());
        Transaccion grande = banco.retirar(tipico, mt, 1500, "Compra");
        check("Retiro de 1500 es marcado como sospechoso", grande.isSospechosa());
        check("Se generó alerta al cliente",
                tipico.getNotificaciones().stream().anyMatch(n -> n.mensaje().contains("sospechosa")));
        Transaccion normal = banco.retirar(tipico, mt, 50, "Comida");
        check("Retiro normal NO es sospechoso", !normal.isSospechosa());
        check("detectar() devuelve la atípica", det.detectar(tipico).contains(grande));
        check("Con pocos datos no se detecta nada", banco.getDetector().calcular(ana.getTransacciones(), TipoTransaccion.RETIRO).isEmpty());

        // ---- Planificador y ordenamiento
        Planificador plan = new Planificador(banco);
        LocalDate hoy = LocalDate.now();
        plan.programar(ana, TipoTransaccion.DEPOSITO, 100, gastosAna, null, Frecuencia.MENSUAL, hoy.plusDays(20));
        plan.programar(ana, TipoTransaccion.DEPOSITO, 50, gastosAna, null, Frecuencia.SEMANAL, hoy.plusDays(5));
        plan.programar(ana, TipoTransaccion.DEPOSITO, 10, gastosAna, null, Frecuencia.UNICA, hoy.plusDays(1));
        List<TransaccionProgramada> orden = plan.ordenadasDe(ana);
        check("Merge sort ordena por fecha ascendente",
                orden.get(0).getMonto() == 10 && orden.get(1).getMonto() == 50 && orden.get(2).getMonto() == 100);
        List<Integer> nums = Planificador.mergeSort(List.of(5, 3, 9, 1, 7, 3, 2));
        check("Merge sort genérico", nums.equals(List.of(1, 2, 3, 3, 5, 7, 9)));

        double saldoPrevio = gastosAna.getSaldo();
        List<String> log = plan.procesar(hoy.plusDays(6));
        check("Se procesan las 2 vencidas", log.size() == 2);
        check("Saldo sube 10 + 50", Math.abs(gastosAna.getSaldo() - saldoPrevio - 60) < 1e-9);
        check("La única queda inactiva y la semanal se reprograma", plan.ordenadasDe(ana).size() == 2);
        lanza("No se programan retiros", DatosInvalidosException.class,
                () -> plan.programar(ana, TipoTransaccion.RETIRO, 10, gastosAna, null, Frecuencia.UNICA, hoy));

        // ---- Reportes
        ServicioReportes rep = new ServicioReportes();
        check("Gasto por categoría suma Comida", rep.gastoPorCategoria(tipico.getTransacciones()).get("Comida") > 300);
        check("El salario de 5000 NO se marca como atípico de los retiros", !tipico.getTransacciones().get(0).isSospechosa());
        System.out.println("\n" + rep.informe(tipico, banco.historialCompleto(tipico), banco.getDetector()));

        System.out.printf("%nResultado: %d OK, %d FALLAS%n", ok, fallas);
        if (fallas > 0) System.exit(1);
    }

    @FunctionalInterface
    private interface Accion { void ejecutar() throws Exception; }

    private static void check(String nombre, boolean condicion) {
        if (condicion) { ok++; System.out.println("OK     " + nombre); }
        else { fallas++; System.out.println("FALLA  " + nombre); }
    }

    private static void lanza(String nombre, Class<? extends Exception> esperada, Accion accion) {
        try {
            accion.ejecutar();
            check(nombre + " (debía lanzar " + esperada.getSimpleName() + ")", false);
        } catch (Exception e) {
            check(nombre + " -> " + esperada.getSimpleName(), esperada.isInstance(e));
        }
    }
}
