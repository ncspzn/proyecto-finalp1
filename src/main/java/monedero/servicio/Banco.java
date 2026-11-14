package monedero.servicio;

import monedero.excepciones.CuentaNoEncontradaException;
import monedero.excepciones.DatosInvalidosException;
import monedero.excepciones.MonederoException;
import monedero.modelo.Beneficio;
import monedero.modelo.Cliente;
import monedero.modelo.Deposito;
import monedero.modelo.Monedero;
import monedero.modelo.MonederoAhorros;
import monedero.modelo.MonederoGastos;
import monedero.modelo.Rango;
import monedero.modelo.Retiro;
import monedero.modelo.TipoTransaccion;
import monedero.modelo.Transaccion;
import monedero.modelo.Transferencia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Fachada del sistema: orquesta transacciones, comisiones, puntos, rangos, alertas y reversiones. */
public class Banco {
    public static final double TASA_RETIRO = 0.01;
    public static final double TASA_TRANSFERENCIA = 0.02;

    private final Map<String, Cliente> clientes = new LinkedHashMap<>();
    private final List<Transaccion> transacciones = new ArrayList<>();
    private final ServicioPuntos puntos = new ServicioPuntos();
    private final DetectorAtipicas detector = new DetectorAtipicas();
    private final ServicioNotificaciones notificaciones = new ServicioNotificaciones();

    // ------------------------------------------------------------------ clientes y monederos
    public Cliente registrarCliente(String nombre, String email, String telefono) throws DatosInvalidosException {
        Cliente c = new Cliente(nombre, email, telefono);
        c.agregarMonedero(new MonederoGastos("Gastos diarios", c.getId()));
        c.agregarMonedero(new MonederoAhorros("Ahorros", c.getId()));
        clientes.put(c.getId(), c);
        return c;
    }

    public Monedero crearMonedero(Cliente c, String nombre, boolean ahorros) throws DatosInvalidosException {
        String n = Validador.validarNombre(nombre);
        Monedero m = ahorros ? new MonederoAhorros(n, c.getId()) : new MonederoGastos(n, c.getId());
        c.agregarMonedero(m);
        return m;
    }

    public Cliente buscarCliente(String id) throws CuentaNoEncontradaException {
        return Optional.ofNullable(clientes.get(id))
                .orElseThrow(() -> new CuentaNoEncontradaException("cliente", id));
    }

    public Monedero buscarMonedero(String id) throws CuentaNoEncontradaException {
        return clientes.values().stream()
                .flatMap(c -> c.getMonederos().stream())
                .filter(m -> m.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new CuentaNoEncontradaException("monedero", id));
    }

    /** Regla: la transacción debe existir y haber sido iniciada por el cliente. */
    public Transaccion buscarTransaccion(Cliente c, int idTransaccion) throws CuentaNoEncontradaException {
        return c.getTransacciones().stream()
                .filter(x -> x.getId() == idTransaccion)
                .findFirst()
                .orElseThrow(() -> new CuentaNoEncontradaException("transacción", String.valueOf(idTransaccion)));
    }

    public List<Cliente> getClientes() { return List.copyOf(clientes.values()); }

    // ------------------------------------------------------------------ operaciones
    public Transaccion depositar(Cliente c, Monedero m, double monto, String categoria) throws MonederoException {
        exigirPropiedad(c, m);
        return registrar(c, new Deposito(m, monto, categoria));
    }

    public Transaccion retirar(Cliente c, Monedero m, double monto, String categoria) throws MonederoException {
        exigirPropiedad(c, m);
        Validador.validarMonto(monto);
        return registrar(c, new Retiro(m, monto, calcularComisionRetiro(c, monto), categoria));
    }

    public Transaccion transferir(Cliente c, Monedero origen, String idMonederoDestino, double monto,
                                  String categoria) throws MonederoException {
        exigirPropiedad(c, origen);
        Validador.validarMonto(monto);
        Monedero destino = buscarMonedero(idMonederoDestino);
        Transferencia t = new Transferencia(origen, destino, monto, calcularComisionTransferencia(c, monto), categoria);
        registrar(c, t);
        clientes.values().stream()
                .filter(otro -> otro.getId().equals(destino.getIdPropietario()) && otro != c)
                .findFirst()
                .ifPresent(receptor -> notificaciones.notificar(receptor,
                        String.format("Recibiste $%,.2f de %s en '%s'.", monto, c.getNombre(), destino.getNombre())));
        return t;
    }

    /** Revierte una transacción iniciada por el cliente y recalcula sus puntos. */
    public void revertir(Cliente c, int idTransaccion) throws MonederoException {
        Transaccion t = buscarTransaccion(c, idTransaccion);
        t.revertir();
        puntos.revertir(c, t);
        notificaciones.notificar(c, "Se revirtió la transacción #" + t.getId() + ". Tus puntos fueron recalculados.");
    }

    public void canjear(Cliente c, Beneficio beneficio, Monedero destinoBono) throws MonederoException {
        if (beneficio == Beneficio.BONO_SALDO) {
            exigirPropiedad(c, destinoBono);
        }
        puntos.canjear(c, beneficio, destinoBono);
        notificaciones.notificar(c, "Canjeaste: " + ServicioPuntos.descripcionDe(beneficio));
    }

    // ------------------------------------------------------------------ comisiones
    public double calcularComisionRetiro(Cliente c, double monto) {
        return c.tieneBeneficio(Beneficio.MES_SIN_RETIROS) ? 0.0 : redondear(monto * TASA_RETIRO);
    }

    /** Descuento sobre la comisión de transferencias según el rango. */
    public static double descuentoComision(Rango rango) {
        return switch (rango) {
            case BRONCE -> 0.00;
            case PLATA -> 0.10;
            case ORO -> 0.20;
            case PLATINO -> 0.30;
        };
    }

    /** Comisión base reducida por el descuento del rango y por el beneficio canjeado. */
    public double calcularComisionTransferencia(Cliente c, double monto) {
        Rango rango = c.getRango();
        double factor = (1 - descuentoComision(rango))
                * (c.tieneBeneficio(Beneficio.DESCUENTO_TRANSFERENCIA) ? 0.90 : 1.0);
        return redondear(monto * TASA_TRANSFERENCIA * factor);
    }

    // ------------------------------------------------------------------ consultas
    /** Transacciones iniciadas por el cliente más las transferencias que recibió, de la más reciente. */
    public List<Transaccion> historialCompleto(Cliente c) {
        return transacciones.stream()
                .filter(t -> t.getMonedero().getIdPropietario().equals(c.getId())
                        || (t instanceof Transferencia tr && tr.getDestino().getIdPropietario().equals(c.getId())))
                .sorted(Comparator.comparing(Transaccion::getFecha).reversed())
                .toList();
    }

    public DetectorAtipicas getDetector() { return detector; }
    public ServicioNotificaciones getNotificaciones() { return notificaciones; }
    public List<Transaccion> getTransacciones() { return Collections.unmodifiableList(transacciones); }

    // ------------------------------------------------------------------ internos
    private Transaccion registrar(Cliente c, Transaccion t) throws MonederoException {
        // 1) Se evalúa contra el historial PREVIO (la operación no puede esconderse a sí misma).
        Optional<DetectorAtipicas.Estadisticas> stats = detector.calcular(c.getTransacciones(), t.getTipo());
        boolean atipica = stats.map(e -> t.getMonto() > e.umbral()).orElse(false);

        // 2) Se ejecuta; si falla (saldo, monto...) no se registra nada.
        t.ejecutar();
        c.agregarTransaccion(t);
        transacciones.add(t);

        // 3) Puntos y rango.
        Rango antes = c.getRango();
        int ganados = puntos.acreditar(c, t);
        if (c.getRango() != antes) {
            notificaciones.notificar(c, "¡Subiste al rango " + c.getRango() + "!");
        }
        if (ganados > 0) {
            notificaciones.notificar(c, "Ganaste " + ganados + " puntos con la transacción #" + t.getId() + ".");
        }

        // 4) Alertas.
        if (atipica) {
            t.marcarSospechosa();
            notificaciones.alertarSospechosa(c, t.getMonto(), stats.get().umbral());
        }
        if (t.getTipo() != TipoTransaccion.DEPOSITO && t.getMonedero().tieneSaldoBajo()) {
            notificaciones.alertarSaldoBajo(c, t.getMonedero());
        }
        return t;
    }

    /** Regla: el cliente sólo puede operar sobre sus propios monederos. */
    private void exigirPropiedad(Cliente c, Monedero m) throws DatosInvalidosException {
        if (m == null || !m.getIdPropietario().equals(c.getId())) {
            throw new DatosInvalidosException("El monedero seleccionado no pertenece al cliente.");
        }
    }

    private static double redondear(double v) { return Math.round(v * 100.0) / 100.0; }
}
