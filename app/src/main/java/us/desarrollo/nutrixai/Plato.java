package us.desarrollo.nutrixai;

import com.google.firebase.database.Exclude;

import java.util.List;

// Un plato guardado en Firebase
public class Plato {

    // Valores que se guardan como tipo de comida
    public static final String DESAYUNO = "desayuno";
    public static final String ALMUERZO = "almuerzo";
    public static final String CENA = "cena";
    public static final String SNACK = "snack";

    // Un ingrediente del plato
    public static class IngredienteGuardado {
        public String nombre;
        public double probabilidad;         // de 0 a 1; es 1.0 si lo confirmó el usuario
        public double probabilidadModelo;   // la que dio el modelo (0 si no la conocía)
        public boolean confirmado;          // true si el usuario lo añadió o lo mantuvo al editar

        // Firebase necesita un constructor vacío para reconstruirlo al leer
        public IngredienteGuardado() {
        }

        public IngredienteGuardado(String nombre, double probabilidad,
                                   double probabilidadModelo, boolean confirmado) {
            this.nombre = nombre;
            this.probabilidad = probabilidad;
            this.probabilidadModelo = probabilidadModelo;
            this.confirmado = confirmado;
        }
    }

    @Exclude
    public String id;               // es el nombre del nodo en Firebase, no se guarda dentro

    public int calorias;            // kcal
    public int carbohidratos;       // gramos
    public int proteinas;           // gramos
    public int grasas;              // gramos
    public String tipoComida;       // uno de los cuatro valores de arriba
    public long fecha;              // momento en que se guardó, en milisegundos
    public double umbral;           // probabilidad mínima para mostrar un ingrediente como etiqueta
    public boolean editado;         // true si el usuario corrigió los ingredientes del modelo
    public List<IngredienteGuardado> ingredientes;   // los del modelo, o los del usuario si editó

    // Firebase necesita un constructor vacío para reconstruir el plato al leerlo
    public Plato() {
    }

    public Plato(int calorias, int carbohidratos, int proteinas, int grasas,
                 String tipoComida, long fecha, double umbral, boolean editado,
                 List<IngredienteGuardado> ingredientes) {
        this.editado = editado;
        this.calorias = calorias;
        this.carbohidratos = carbohidratos;
        this.proteinas = proteinas;
        this.grasas = grasas;
        this.tipoComida = tipoComida;
        this.fecha = fecha;
        this.umbral = umbral;
        this.ingredientes = ingredientes;
    }
}

