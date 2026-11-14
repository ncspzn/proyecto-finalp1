package monedero.ui;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import monedero.excepciones.MonederoException;
import monedero.modelo.*;
import monedero.servicio.*;

import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Interfaz JavaFX del monedero virtual. Toda la lógica vive en el paquete servicio. */
public class MonederoApp extends Application {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Banco banco = DatosDemo.crear();
    private final Planificador planificador = new Planificador(banco);
    private final ServicioReportes reportes = new ServicioReportes();
    private final ComboBox<Cliente> cmbCliente = new ComboBox<>();
    private final List<Runnable> refrescadores = new ArrayList<>();
    private Cliente actual;

    @FunctionalInterface
    private interface Accion { Object ejecutar() throws MonederoException; }

    @Override
    public void start(Stage stage) {
        Button btnNuevo = new Button("Nuevo cliente");
        btnNuevo.setOnAction(e -> dialogoNuevoCliente());
        cmbCliente.getItems().setAll(banco.getClientes());
        cmbCliente.setOnAction(e -> {
            actual = cmbCliente.getValue();
            refrescarTodo();
        });
        HBox top = new HBox(10, new Label("Cliente:"), cmbCliente, btnNuevo);
        top.setPadding(new Insets(10));

        TabPane tabs = new TabPane(tabResumen(), tabOperaciones(), tabHistorial(), tabPuntos(),
                tabProgramadas(), tabReportes(), tabNotificaciones());
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        BorderPane root = new BorderPane(tabs);
        root.setTop(top);
        stage.setTitle("Monedero Virtual con Puntos");
        stage.setScene(new Scene(root, 1050, 680));
        stage.show();
        cmbCliente.getSelectionModel().selectFirst();
    }

    // ============================================================ pestañas
    private Tab tabResumen() {
        TableView<Monedero> tabla = new TableView<>();
        tabla.getColumns().add(col("Monedero", Monedero::getNombre));
        tabla.getColumns().add(col("Tipo", Monedero::getTipo));
        tabla.getColumns().add(col("Saldo", m -> money(m.getSaldo())));
        Label lblTotal = new Label();
        TextField txtNombre = new TextField();
        txtNombre.setPromptText("Nombre del nuevo monedero");
        ComboBox<String> cmbTipo = new ComboBox<>(FXCollections.observableArrayList("Gastos diarios", "Ahorros"));
        cmbTipo.getSelectionModel().selectFirst();
        Button btnCrear = new Button("Crear monedero");
        btnCrear.setOnAction(e -> ejecutar(() -> {
            banco.crearMonedero(actual, txtNombre.getText(), "Ahorros".equals(cmbTipo.getValue()));
            txtNombre.clear();
            return null;
        }, "Monedero creado."));
        refrescadores.add(() -> {
            tabla.getItems().setAll(actual.getMonederos());
            lblTotal.setText("Saldo total: " + money(actual.getSaldoTotal()) + "   |   Rango: " + actual.getRango()
                    + "   |   Puntos: " + actual.getPuntosDisponibles());
        });
        VBox box = new VBox(10, lblTotal, tabla, new HBox(10, txtNombre, cmbTipo, btnCrear));
        box.setPadding(new Insets(10));
        return new Tab("Monederos", box);
    }

    private Tab tabOperaciones() {
        ComboBox<Monedero> cmbOrigen = new ComboBox<>();
        ComboBox<Cliente> cmbDestino = new ComboBox<>();
        TextField txtMonto = new TextField();
        txtMonto.setPromptText("Monto");
        TextField txtCategoria = new TextField();
        txtCategoria.setPromptText("Categoría (Comida, Ocio...)");
        Button btnDep = new Button("Depositar");
        Button btnRet = new Button("Retirar");
        Button btnTra = new Button("Transferir al monedero principal de:");

        btnDep.setOnAction(e -> ejecutar(() -> banco.depositar(actual, cmbOrigen.getValue(),
                Validador.parsearMonto(txtMonto.getText()), txtCategoria.getText()), "Depósito realizado."));
        btnRet.setOnAction(e -> ejecutar(() -> banco.retirar(actual, cmbOrigen.getValue(),
                Validador.parsearMonto(txtMonto.getText()), txtCategoria.getText()), "Retiro realizado."));
        btnTra.setOnAction(e -> ejecutar(() -> {
            Cliente dest = cmbDestino.getValue();
            if (dest == null) throw new MonederoException("Selecciona el cliente destino.");
            return banco.transferir(actual, cmbOrigen.getValue(), dest.getMonederoPrincipal().getId(),
                    Validador.parsearMonto(txtMonto.getText()), txtCategoria.getText());
        }, "Transferencia realizada."));

        refrescadores.add(() -> {
            recargar(cmbOrigen, actual.getMonederos());
            recargar(cmbDestino, banco.getClientes().stream().filter(c -> c != actual).toList());
        });
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(10);
        g.setPadding(new Insets(15));
        g.addRow(0, new Label("Monedero:"), cmbOrigen);
        g.addRow(1, new Label("Monto:"), txtMonto);
        g.addRow(2, new Label("Categoría:"), txtCategoria);
        g.addRow(3, btnDep, btnRet);
        g.addRow(4, btnTra, cmbDestino);
        return new Tab("Operaciones", g);
    }

    private Tab tabHistorial() {
        TableView<Transaccion> tabla = new TableView<>();
        tabla.getColumns().add(col("ID", t -> String.valueOf(t.getId())));
        tabla.getColumns().add(col("Fecha", t -> t.getFecha().format(FECHA)));
        tabla.getColumns().add(col("Tipo", t -> t.getTipo().toString()));
        tabla.getColumns().add(col("Monto", t -> money(t.getMonto())));
        tabla.getColumns().add(col("Comisión", t -> money(t.getComision())));
        tabla.getColumns().add(col("Categoría", Transaccion::getCategoria));
        tabla.getColumns().add(col("Puntos", t -> String.valueOf(t.getPuntosGenerados())));
        tabla.getColumns().add(col("Estado", t -> t.isRevertida() ? "Revertida" : t.isSospechosa() ? "⚠ Sospechosa" : "OK"));
        tabla.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Transaccion t, boolean vacio) {
                super.updateItem(t, vacio);
                setStyle(vacio || t == null ? "" : t.isSospechosa() ? "-fx-background-color: #ffe0b2;"
                        : t.isRevertida() ? "-fx-text-fill: gray;" : "");
            }
        });

        ComboBox<TipoTransaccion> cmbFiltro = new ComboBox<>(FXCollections.observableArrayList(TipoTransaccion.values()));
        cmbFiltro.setPromptText("Todos los tipos");
        Button btnTodos = new Button("Quitar filtro");
        Button btnRevertir = new Button("Revertir seleccionada");
        Runnable cargar = () -> tabla.getItems().setAll(
                reportes.filtrar(banco.historialCompleto(actual), cmbFiltro.getValue(), 0));
        cmbFiltro.setOnAction(e -> cargar.run());
        btnTodos.setOnAction(e -> { cmbFiltro.setValue(null); cargar.run(); });
        btnRevertir.setOnAction(e -> {
            Transaccion sel = tabla.getSelectionModel().getSelectedItem();
            if (sel == null) { error("Selecciona una transacción."); return; }
            ejecutar(() -> { banco.revertir(actual, sel.getId()); return null; }, "Transacción revertida; puntos recalculados.");
        });
        refrescadores.add(cargar);
        VBox box = new VBox(10, new HBox(10, cmbFiltro, btnTodos, btnRevertir), tabla);
        box.setPadding(new Insets(10));
        return new Tab("Historial", box);
    }

    private Tab tabPuntos() {
        Label lblPuntos = new Label();
        Label lblRango = new Label();
        ProgressBar barra = new ProgressBar(0);
        barra.setPrefWidth(350);
        ComboBox<Monedero> cmbBono = new ComboBox<>();
        VBox canjes = new VBox(8);
        for (Beneficio b : Beneficio.values()) {
            Button btn = new Button(String.format("Canjear (%d pts): %s", ServicioPuntos.costoDe(b), ServicioPuntos.descripcionDe(b)));
            btn.setOnAction(e -> ejecutar(() -> { banco.canjear(actual, b, cmbBono.getValue()); return null; }, "Canje realizado."));
            canjes.getChildren().add(btn);
        }
        TableView<RegistroPuntos> tabla = new TableView<>();
        tabla.getColumns().add(col("Fecha", r -> r.fecha().format(FECHA)));
        tabla.getColumns().add(col("Puntos", r -> (r.delta() > 0 ? "+" : "") + r.delta()));
        tabla.getColumns().add(col("Motivo", RegistroPuntos::motivo));

        refrescadores.add(() -> {
            Rango r = actual.getRango();
            lblPuntos.setText("Puntos disponibles: " + actual.getPuntosDisponibles()
                    + "   (acumulados históricos: " + actual.getPuntosAcumulados() + ")");
            lblRango.setText("Rango: " + r + String.format(" | -%.0f%% comisión transferencias | +%.0f%% puntos",
                    Banco.descuentoComision(r) * 100, Transaccion.bonoPuntos(r) * 100));
            barra.setProgress(Cliente.siguienteRango(r).map(s -> (double) (actual.getPuntosAcumulados() - Cliente.puntosMinimos(r))
                    / (Cliente.puntosMinimos(s) - Cliente.puntosMinimos(r))).orElse(1.0));
            recargar(cmbBono, actual.getMonederos());
            tabla.getItems().setAll(invertida(actual.getHistorialPuntos()));
        });
        VBox box = new VBox(10, lblPuntos, lblRango, new Label("Progreso al siguiente rango:"), barra,
                new Label("Monedero para el bono de saldo:"), cmbBono, new Separator(), canjes, tabla);
        box.setPadding(new Insets(10));
        return new Tab("Puntos y rangos", box);
    }

    private Tab tabProgramadas() {
        ComboBox<Monedero> cmbOrigen = new ComboBox<>();
        ComboBox<Cliente> cmbDestino = new ComboBox<>();
        ComboBox<TipoTransaccion> cmbTipo = new ComboBox<>(
                FXCollections.observableArrayList(TipoTransaccion.DEPOSITO, TipoTransaccion.TRANSFERENCIA));
        cmbTipo.getSelectionModel().selectFirst();
        ComboBox<Frecuencia> cmbFrec = new ComboBox<>(FXCollections.observableArrayList(Frecuencia.values()));
        cmbFrec.getSelectionModel().select(Frecuencia.MENSUAL);
        TextField txtMonto = new TextField();
        txtMonto.setPromptText("Monto");
        DatePicker fecha = new DatePicker(LocalDate.now().plusDays(1));
        DatePicker fechaSim = new DatePicker(LocalDate.now());
        TextArea log = new TextArea();
        log.setEditable(false);
        log.setPrefRowCount(5);

        TableView<TransaccionProgramada> tabla = new TableView<>();
        tabla.getColumns().add(col("Próxima fecha", tp -> tp.getProximaEjecucion().toString()));
        tabla.getColumns().add(col("Tipo", tp -> tp.getTipo().toString()));
        tabla.getColumns().add(col("Monto", tp -> money(tp.getMonto())));
        tabla.getColumns().add(col("Frecuencia", tp -> tp.getFrecuencia().toString()));
        tabla.getColumns().add(col("Origen", tp -> tp.getOrigen().getNombre()));
        tabla.getColumns().add(col("Destino", tp -> tp.getDestino() == null ? "—" : tp.getDestino().getNombre()));

        Button btnProg = new Button("Programar");
        btnProg.setOnAction(e -> ejecutar(() -> {
            Cliente d = cmbDestino.getValue();
            planificador.programar(actual, cmbTipo.getValue(), Validador.parsearMonto(txtMonto.getText()),
                    cmbOrigen.getValue(), cmbTipo.getValue() == TipoTransaccion.TRANSFERENCIA && d != null
                            ? d.getMonederoPrincipal() : null, cmbFrec.getValue(), fecha.getValue());
            return null;
        }, "Transacción programada."));
        Button btnProc = new Button("Procesar vencidas hasta la fecha");
        btnProc.setOnAction(e -> {
            List<String> res = planificador.procesar(fechaSim.getValue());
            long rec = planificador.enviarRecordatorios(fechaSim.getValue(), 3);
            log.setText(res.isEmpty() ? "Nada vencido." : String.join("\n", res));
            log.appendText("\nRecordatorios enviados: " + rec);
            refrescarTodo();
        });

        refrescadores.add(() -> {
            recargar(cmbOrigen, actual.getMonederos());
            recargar(cmbDestino, banco.getClientes().stream().filter(c -> c != actual).toList());
            tabla.getItems().setAll(planificador.ordenadasDe(actual));
        });
        HBox f1 = new HBox(10, cmbTipo, cmbOrigen, new Label("→"), cmbDestino);
        HBox f2 = new HBox(10, txtMonto, cmbFrec, new Label("Primera fecha:"), fecha, btnProg);
        HBox f3 = new HBox(10, new Label("Fecha simulada:"), fechaSim, btnProc);
        VBox box = new VBox(10, f1, f2, tabla, f3, log);
        box.setPadding(new Insets(10));
        return new Tab("Programadas", box);
    }

    private Tab tabReportes() {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setStyle("-fx-font-family: monospace;");
        PieChart pie = new PieChart();
        pie.setTitle("Gasto por categoría");
        refrescadores.add(() -> {
            List<Transaccion> h = banco.historialCompleto(actual);
            area.setText(reportes.informe(actual, h, banco.getDetector()));
            pie.getData().setAll(reportes.gastoPorCategoria(h).entrySet().stream()
                    .map(e -> new PieChart.Data(e.getKey(), e.getValue())).toList());
        });
        HBox box = new HBox(10, area, pie);
        HBox.setHgrow(area, Priority.ALWAYS);
        box.setPadding(new Insets(10));
        return new Tab("Reportes", box);
    }

    private Tab tabNotificaciones() {
        ListView<String> lista = new ListView<>();
        ComboBox<CanalNotificacion> cmbCanal = new ComboBox<>(FXCollections.observableArrayList(CanalNotificacion.values()));
        cmbCanal.setOnAction(e -> {
            if (actual != null && cmbCanal.getValue() != null) actual.setCanalPreferido(cmbCanal.getValue());
        });
        refrescadores.add(() -> {
            cmbCanal.setValue(actual.getCanalPreferido());
            lista.getItems().setAll(invertida(actual.getNotificaciones()).stream()
                    .map(n -> n.fecha().format(FECHA) + " [" + n.canal() + "] " + n.mensaje()).toList());
        });
        VBox box = new VBox(10, new HBox(10, new Label("Canal preferido:"), cmbCanal), lista);
        box.setPadding(new Insets(10));
        VBox.setVgrow(lista, Priority.ALWAYS);
        return new Tab("Notificaciones", box);
    }

    // ============================================================ utilidades
    private void ejecutar(Accion accion, String exito) {
        try {
            Object r = accion.ejecutar();
            refrescarTodo();
            if (r instanceof Transaccion t && t.isSospechosa()) {
                new Alert(Alert.AlertType.WARNING, exito + "\n\n⚠ La operación fue marcada como potencialmente sospechosa.").showAndWait();
            } else {
                new Alert(Alert.AlertType.INFORMATION, exito).showAndWait();
            }
        } catch (MonederoException ex) {
            error(ex.getMessage());
        }
    }

    private void refrescarTodo() {
        if (actual != null) refrescadores.forEach(Runnable::run);
    }

    private void dialogoNuevoCliente() {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Nuevo cliente");
        TextField n = new TextField(), m = new TextField(), t = new TextField();
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(10);
        g.addRow(0, new Label("Nombre:"), n);
        g.addRow(1, new Label("Correo:"), m);
        g.addRow(2, new Label("Teléfono:"), t);
        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            try {
                Cliente c = banco.registrarCliente(n.getText(), m.getText(), t.getText());
                cmbCliente.getItems().setAll(banco.getClientes());
                cmbCliente.setValue(c);
            } catch (MonederoException ex) {
                error(ex.getMessage());
            }
        });
    }

    private <T> void recargar(ComboBox<T> cmb, List<T> items) {
        T sel = cmb.getValue();
        cmb.getItems().setAll(items);
        int i = sel == null ? -1 : items.indexOf(sel);
        if (i >= 0) cmb.getSelectionModel().select(i);
        else if (!items.isEmpty()) cmb.getSelectionModel().selectFirst();
    }

    private <T> TableColumn<T, String> col(String titulo, Function<T, String> f) {
        TableColumn<T, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(d -> new SimpleStringProperty(f.apply(d.getValue())));
        c.setPrefWidth(130);
        return c;
    }

    private static <T> List<T> invertida(List<T> l) {
        List<T> copia = new ArrayList<>(l);
        java.util.Collections.reverse(copia);
        return copia;
    }

    private static String money(double v) { return String.format("$%,.2f", v); }

    private static void error(String msg) { new Alert(Alert.AlertType.ERROR, msg).showAndWait(); }

    public static void main(String[] args) { launch(args); }
}
