package us.desarrollo.nutrixai;

import java.util.List;

// Guarda juntos los datos que devuelve el modelo para una foto
public class ResultadoPrediccion {

    // Un ingrediente con la probabilidad que le da el modelo (de 0 a 1)
    public static class Ingrediente {
        public final String nombre;
        public final float probabilidad;

        public Ingrediente(String nombre, float probabilidad) {
            this.nombre = nombre;
            this.probabilidad = probabilidad;
        }
    }

    public final float calorias;        // kcal
    public final float carbohidratos;   // gramos
    public final float proteinas;       // gramos
    public final float grasas;          // gramos
    public final float umbral;          // probabilidad mínima para mostrar un ingrediente
    public final List<Ingrediente> ingredientes;    // los que superan el umbral
    public final List<Ingrediente> masProbables;    // los más probables, superen o no el umbral

    public ResultadoPrediccion(float calorias, float carbohidratos, float proteinas, float grasas,
                               float umbral, List<Ingrediente> ingredientes,
                               List<Ingrediente> masProbables) {
        this.calorias = calorias;
        this.carbohidratos = carbohidratos;
        this.proteinas = proteinas;
        this.grasas = grasas;
        this.umbral = umbral;
        this.ingredientes = ingredientes;
        this.masProbables = masProbables;
    }
}
