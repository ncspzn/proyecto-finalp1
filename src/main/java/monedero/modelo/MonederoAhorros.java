package monedero.modelo;

/** Monedero de ahorros: exige conservar un saldo mínimo. */
public class MonederoAhorros extends Monedero {
    public MonederoAhorros(String nombre, String idPropietario) {
        super(nombre, idPropietario);
    }

    @Override public String getTipo() { return "Ahorros"; }
    @Override public double getSaldoMinimo() { return 10.0; }
    @Override public double getUmbralSaldoBajo() { return 50.0; }
}
