package monedero.modelo;

import monedero.excepciones.MonederoException;

/** Comportamiento común de las operaciones que se pueden deshacer. */
public interface Reversible {
    void revertir() throws MonederoException;
    boolean isRevertida();
}
