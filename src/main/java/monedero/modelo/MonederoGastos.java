package monedero.modelo;

/** Monedero de gastos diarios: permite dejar el saldo en cero. */
public class MonederoGastos extends Monedero {
    public MonederoGastos(String nombre, String idPropietario) {
        super(nombre, idPropietario);
    }

    @Override public String getTipo() { return "Gastos diarios"; }
    @Override public double getSaldoMinimo() { return 0.0; }
    @Override public double getUmbralSaldoBajo() { return 20.0; }
}
