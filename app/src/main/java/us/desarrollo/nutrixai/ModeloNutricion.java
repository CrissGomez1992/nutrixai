package us.desarrollo.nutrixai;

import android.content.Context;
import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Carga el modelo TFLite y calcula los macros y los ingredientes de una foto
public class ModeloNutricion implements Closeable {

    private static final String ARCHIVO_MODELO = "nutrition_ing_model.tflite";
    private static final String ARCHIVO_AJUSTES = "nutrition_ing_scaler.json";
    private static final int NUM_MACROS = 4;            // calorías, carbohidratos, proteínas, grasas
    private static final int MAX_INGREDIENTES = 3;      // máximo de etiquetas de ingredientes
    private static final int TOP_DETALLE = 3;           // ingredientes que salen en el detalle

    private Interpreter interprete;
    private ByteBuffer bufferModelo;                    // se guarda para que no se libere
    private int tamano;                                 // 224
    private float umbral;
    private final float[] media = new float[NUM_MACROS];
    private final float[] desviacion = new float[NUM_MACROS];
    private final List<String> nombresIngredientes = new ArrayList<>();
    private int salidaMacros = -1;                      // en qué posición sale cada resultado
    private int salidaIngredientes = -1;

    public ModeloNutricion(Context contexto) throws IOException {
        // 1. Leer el JSON con las medias, desviaciones, ingredientes y umbral
        try {
            JSONObject json = new JSONObject(
                    new String(leerBytes(contexto, ARCHIVO_AJUSTES), StandardCharsets.UTF_8));
            tamano = json.getInt("input_size");
            umbral = (float) json.getDouble("ingredient_threshold");
            JSONArray jsonMedia = json.getJSONArray("mean");
            JSONArray jsonDesviacion = json.getJSONArray("std");
            for (int i = 0; i < NUM_MACROS; i++) {
                media[i] = (float) jsonMedia.getDouble(i);
                desviacion[i] = (float) jsonDesviacion.getDouble(i);
            }
            JSONArray jsonIngredientes = json.getJSONArray("ingredients");
            for (int i = 0; i < jsonIngredientes.length(); i++) {
                nombresIngredientes.add(jsonIngredientes.getString(i));
            }
        } catch (JSONException e) {
            throw new IOException("El archivo " + ARCHIVO_AJUSTES + " no es válido", e);
        }

        // 2. Cargar el modelo en memoria y crear el intérprete
        byte[] bytes = leerBytes(contexto, ARCHIVO_MODELO);
        bufferModelo = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
        bufferModelo.put(bytes);
        bufferModelo.rewind();
        interprete = new Interpreter(bufferModelo);

        // 3. Identificar cada salida por su forma: 4 valores son los macros,
        //    y tantos valores como ingredientes son las probabilidades
        for (int i = 0; i < interprete.getOutputTensorCount(); i++) {
            int[] forma = interprete.getOutputTensor(i).shape();
            int ultimo = forma[forma.length - 1];
            if (ultimo == NUM_MACROS) {
                salidaMacros = i;
            } else if (ultimo == nombresIngredientes.size()) {
                salidaIngredientes = i;
            }
        }
        if (salidaMacros < 0 || salidaIngredientes < 0) {
            throw new IOException("El modelo no tiene las salidas esperadas");
        }
    }

    // Calcula los macros y los ingredientes de una foto.
    // Es lento: debe llamarse desde un hilo secundario, nunca desde el principal.
    public synchronized ResultadoPrediccion predecir(Bitmap foto) {
        // Preparar la entrada: foto a 224x224, píxeles en orden R,G,B, valores de 0 a 255
        Bitmap ajustada = Bitmap.createScaledBitmap(foto, tamano, tamano, true);
        int[] pixeles = new int[tamano * tamano];
        ajustada.getPixels(pixeles, 0, tamano, 0, 0, tamano, tamano);

        ByteBuffer entrada = ByteBuffer.allocateDirect(4 * tamano * tamano * 3)
                .order(ByteOrder.nativeOrder());
        for (int pixel : pixeles) {
            entrada.putFloat((pixel >> 16) & 0xFF);   // rojo
            entrada.putFloat((pixel >> 8) & 0xFF);    // verde
            entrada.putFloat(pixel & 0xFF);           // azul
        }
        entrada.rewind();

        // Ejecutar el modelo
        float[][] macrosNormalizados = new float[1][NUM_MACROS];
        float[][] probabilidades = new float[1][nombresIngredientes.size()];
        Map<Integer, Object> salidas = new HashMap<>();
        salidas.put(salidaMacros, macrosNormalizados);
        salidas.put(salidaIngredientes, probabilidades);
        interprete.runForMultipleInputsOutputs(new Object[]{entrada}, salidas);

        // Volver a kcal y gramos: valor = salida * desviación + media
        float[] macros = new float[NUM_MACROS];
        for (int i = 0; i < NUM_MACROS; i++) {
            macros[i] = Math.max(0f, macrosNormalizados[0][i] * desviacion[i] + media[i]);
        }

        // Todos los ingredientes con su probabilidad, de más a menos probable
        List<ResultadoPrediccion.Ingrediente> ordenados = ordenarIngredientes(probabilidades[0]);

        // Solo los que superan el umbral se muestran como etiquetas
        List<ResultadoPrediccion.Ingrediente> sobreUmbral = new ArrayList<>();
        for (ResultadoPrediccion.Ingrediente ingrediente : ordenados) {
            if (ingrediente.probabilidad >= umbral && sobreUmbral.size() < MAX_INGREDIENTES) {
                sobreUmbral.add(ingrediente);
            }
        }

        // Los más probables, superen o no el umbral, para el detalle
        List<ResultadoPrediccion.Ingrediente> masProbables = new ArrayList<>(
                ordenados.subList(0, Math.min(TOP_DETALLE, ordenados.size())));

        return new ResultadoPrediccion(macros[0], macros[1], macros[2], macros[3],
                umbral, sobreUmbral, masProbables, ordenados);
    }

    // Une cada nombre con su probabilidad y ordena de mayor a menor
    private List<ResultadoPrediccion.Ingrediente> ordenarIngredientes(float[] probs) {
        List<ResultadoPrediccion.Ingrediente> lista = new ArrayList<>();
        for (int i = 0; i < probs.length; i++) {
            lista.add(new ResultadoPrediccion.Ingrediente(nombresIngredientes.get(i), probs[i]));
        }
        Collections.sort(lista, (a, b) -> Float.compare(b.probabilidad, a.probabilidad));
        return lista;
    }

    // Lee un archivo de la carpeta assets y devuelve sus bytes
    private static byte[] leerBytes(Context contexto, String nombre) throws IOException {
        try (InputStream entrada = contexto.getAssets().open(nombre)) {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            byte[] bloque = new byte[8192];
            int leidos;
            while ((leidos = entrada.read(bloque)) != -1) {
                salida.write(bloque, 0, leidos);
            }
            return salida.toByteArray();
        }
    }

    @Override
    public void close() {
        interprete.close();
    }
}
