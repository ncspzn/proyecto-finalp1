package monedero.servicio;

import monedero.excepciones.MonederoException;
import monedero.modelo.Cliente;

/** Datos de ejemplo para probar la aplicación sin tener que registrar todo a mano. */
public final class DatosDemo {
    private DatosDemo() { }

    public static Banco crear() {
        Banco banco = new Banco();
        try {
            Cliente ana = banco.registrarCliente("Ana Gómez", "ana@correo.com", "3001234567");
            Cliente luis = banco.registrarCliente("Luis Pérez", "luis@correo.com", "3109876543");

            banco.depositar(ana, ana.getMonederoPrincipal(), 2000, "Salario");
            banco.depositar(luis, luis.getMonederoPrincipal(), 1500, "Salario");
            double[] gastos = {40, 55, 35, 60, 45, 50};
            String[] cats = {"Comida", "Transporte", "Comida", "Ocio", "Comida", "Transporte"};
            for (int i = 0; i < gastos.length; i++) {
                banco.retirar(ana, ana.getMonederoPrincipal(), gastos[i], cats[i]);
            }
            banco.transferir(ana, ana.getMonederoPrincipal(), luis.getMonederoPrincipal().getId(), 300, "Préstamo");
        } catch (MonederoException e) {
            throw new IllegalStateException("Error cargando datos de demo", e);
        }
        return banco;
    }
}
