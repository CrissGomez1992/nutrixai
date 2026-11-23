package us.desarrollo.nutrixai;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ResultFragment extends Fragment {

    private static final String ARG_FOTO = "foto_uri";
    private static final int LADO_MAX = 1024;   // la foto se reduce a este tamaño aproximado

    // Un hilo secundario para el análisis, y un "mensajero" para volver al hilo principal
    private final ExecutorService hilo = Executors.newSingleThreadExecutor();
    private final Handler principal = new Handler(Looper.getMainLooper());

    // Lo que hace falta para guardar: se rellenan cuando termina el análisis
    private ResultadoPrediccion resultadoActual;
    private Bitmap bitmapActual;

    // Ingredientes corregidos por el usuario; null mientras no los haya editado
    private List<String> ingredientesEditados;

    // Crea la pantalla y le entrega la foto dentro de sus "argumentos"
    public static ResultFragment nuevo(Uri foto) {
        ResultFragment fragmento = new ResultFragment();
        Bundle argumentos = new Bundle();
        argumentos.putString(ARG_FOTO, foto.toString());
        fragmento.setArguments(argumentos);
        return fragmento;
    }

    public ResultFragment() {
        super(R.layout.fragment_result);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String uriTexto = getArguments() != null ? getArguments().getString(ARG_FOTO) : null;
        if (uriTexto == null) {
            Toast.makeText(requireContext(), R.string.error_foto, Toast.LENGTH_SHORT).show();
            volver();
            return;
        }

        // 1. Tipo de comida: lo elige el usuario; si no elige, se usará el de la hora
        ChipGroup grupoTipo = view.findViewById(R.id.grupo_tipo_comida);
        TextView txtSugerencia = view.findViewById(R.id.txt_sugerencia);
        Chip chipSugerido = view.findViewById(chipSegunHora());
        txtSugerencia.setText(getString(R.string.tipo_comida_sugerencia, chipSugerido.getText()));
        grupoTipo.setOnCheckedStateChangeListener((grupo, marcados) ->
                txtSugerencia.setVisibility(marcados.isEmpty() ? View.VISIBLE : View.GONE));

        // 2. Botón "Nueva foto"
        view.findViewById(R.id.btn_nueva_foto).setOnClickListener(v -> volver());

        // Botón "Guardar plato": empieza desactivado hasta que el modelo termina
        view.findViewById(R.id.btn_guardar).setOnClickListener(v -> guardar(view));

        // 3. Cargar la foto y analizarla, todo en el hilo secundario
        Uri foto = Uri.parse(uriTexto);
        Context contexto = requireContext().getApplicationContext();
        ImageView imgFoto = view.findViewById(R.id.img_foto);

        hilo.execute(() -> {
            try {
                Bitmap bitmap = cargarBitmap(contexto, foto);
                principal.post(() -> {
                    if (isAdded()) {
                        bitmapActual = bitmap;            // se usará al guardar
                        imgFoto.setImageBitmap(bitmap);   // se ve la foto mientras se analiza
                    }
                });

                ResultadoPrediccion resultado;
                try (ModeloNutricion modelo = new ModeloNutricion(contexto)) {
                    resultado = modelo.predecir(bitmap);
                }
                principal.post(() -> {
                    if (isAdded()) {
                        mostrarResultado(view, resultado);
                    }
                });
            } catch (IOException | RuntimeException e) {
                principal.post(() -> {
                    if (isAdded()) {
                        mostrarError(view);
                    }
                });
            }
        });
    }

    // Rellena la pantalla con los datos del modelo
    private void mostrarResultado(View view, ResultadoPrediccion r) {
        view.findViewById(R.id.panel_analizando).setVisibility(View.GONE);
        view.findViewById(R.id.panel_resultados).setVisibility(View.VISIBLE);

        // Ya hay algo que guardar: se activa el botón
        resultadoActual = r;
        view.findViewById(R.id.btn_guardar).setEnabled(true);

        ((TextView) view.findViewById(R.id.txt_calorias))
                .setText(getString(R.string.calorias_valor, Math.round(r.calorias)));
        ((TextView) view.findViewById(R.id.txt_carbos))
                .setText(getString(R.string.valor_gramos, Math.round(r.carbohidratos)));
        ((TextView) view.findViewById(R.id.txt_proteinas))
                .setText(getString(R.string.valor_gramos, Math.round(r.proteinas)));
        ((TextView) view.findViewById(R.id.txt_grasas))
                .setText(getString(R.string.valor_gramos, Math.round(r.grasas)));

        // La barra reparte su ancho según los gramos de cada macro
        ponerPeso(view.findViewById(R.id.barra_carbos), r.carbohidratos);
        ponerPeso(view.findViewById(R.id.barra_proteinas), r.proteinas);
        ponerPeso(view.findViewById(R.id.barra_grasas), r.grasas);

        mostrarIngredientes(view, r);
    }

    // Etiquetas con los ingredientes que superan el umbral, y el detalle desplegable
    private void mostrarIngredientes(View view, ResultadoPrediccion r) {
        // Las etiquetas se dibujan en un método aparte, porque se repiten al editar
        pintarIngredientes(view);
        view.findViewById(R.id.btn_editar_ingredientes).setOnClickListener(v -> abrirEditor(view));

        // Detalle: una fila por cada uno de los ingredientes más probables
        TextView txtExplicacion = view.findViewById(R.id.txt_detalle_explicacion);
        txtExplicacion.setText(getString(R.string.detalle_explicacion, Math.round(r.umbral * 100)));

        LinearLayout lista = view.findViewById(R.id.lista_detalle);
        lista.removeAllViews();
        LayoutInflater inflador = LayoutInflater.from(requireContext());
        for (ResultadoPrediccion.Ingrediente ingrediente : r.masProbables) {
            View fila = inflador.inflate(R.layout.item_ingrediente, lista, false);
            int porcentaje = Math.round(ingrediente.probabilidad * 100);
            ((TextView) fila.findViewById(R.id.txt_nombre_ingrediente)).setText(ingrediente.nombre);
            ((TextView) fila.findViewById(R.id.txt_porcentaje_ingrediente))
                    .setText(getString(R.string.porcentaje, porcentaje));
            ((LinearProgressIndicator) fila.findViewById(R.id.barra_ingrediente))
                    .setProgress(porcentaje);
            lista.addView(fila);
        }

        // El botón despliega o recoge el detalle
        View panelDetalle = view.findViewById(R.id.panel_detalle);
        TextView btnDetalle = view.findViewById(R.id.btn_detalle);
        btnDetalle.setOnClickListener(v -> {
            boolean abrir = panelDetalle.getVisibility() != View.VISIBLE;
            panelDetalle.setVisibility(abrir ? View.VISIBLE : View.GONE);
            btnDetalle.setText(abrir ? R.string.ocultar_detalle : R.string.ver_detalle);
        });
    }

    private void mostrarError(View view) {
        view.findViewById(R.id.panel_analizando).setVisibility(View.GONE);
        Toast.makeText(requireContext(), R.string.error_modelo, Toast.LENGTH_LONG).show();
    }

    // Cambia el "peso" de una franja de la barra; el mínimo evita que desaparezca
    private void ponerPeso(View franja, float gramos) {
        LinearLayout.LayoutParams parametros = (LinearLayout.LayoutParams) franja.getLayoutParams();
        parametros.weight = Math.max(gramos, 0.1f);
        franja.setLayoutParams(parametros);
    }

    // Lee la foto reducida y con la orientación correcta
    private static Bitmap cargarBitmap(Context contexto, Uri uri) throws IOException {
        ContentResolver resolver = contexto.getContentResolver();

        // Primero solo se mide la foto, sin cargarla en memoria
        BitmapFactory.Options medida = new BitmapFactory.Options();
        medida.inJustDecodeBounds = true;
        try (InputStream entrada = resolver.openInputStream(uri)) {
            BitmapFactory.decodeStream(entrada, null, medida);
        }
        int mayor = Math.max(medida.outWidth, medida.outHeight);
        int reduccion = 1;
        while (mayor / (reduccion * 2) >= LADO_MAX) {
            reduccion *= 2;
        }

        // Después se carga ya reducida
        BitmapFactory.Options opciones = new BitmapFactory.Options();
        opciones.inSampleSize = reduccion;
        Bitmap bitmap;
        try (InputStream entrada = resolver.openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(entrada, null, opciones);
        }
        if (bitmap == null) {
            throw new IOException("No se pudo leer la foto");
        }

        // Las fotos de cámara suelen venir giradas y llevan anotado cuánto
        int grados = gradosDeGiro(resolver, uri);
        if (grados != 0) {
            Matrix giro = new Matrix();
            giro.postRotate(grados);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), giro, true);
        }
        return bitmap;
    }

    // Lee de la foto cuántos grados hay que girarla
    private static int gradosDeGiro(ContentResolver resolver, Uri uri) {
        try (InputStream entrada = resolver.openInputStream(uri)) {
            if (entrada == null) {
                return 0;
            }
            ExifInterface exif = new ExifInterface(entrada);
            switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL)) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (IOException e) {
            return 0;
        }
    }

    // Guarda el plato en Firebase y, si sale bien, abre el historial
    private void guardar(View view) {
        if (resultadoActual == null || bitmapActual == null) {
            return;
        }
        TextView btnGuardar = view.findViewById(R.id.btn_guardar);
        btnGuardar.setEnabled(false);               // evita guardar dos veces seguidas
        btnGuardar.setText(R.string.guardando);

        ChipGroup grupoTipo = view.findViewById(R.id.grupo_tipo_comida);
        Plato plato = crearPlato(resultadoActual, tipoElegido(grupoTipo));
        String foto = fotoABase64(bitmapActual);

        new RepositorioPlatos().guardar(plato, foto, exito -> {
            if (!isAdded()) {
                return;
            }
            if (exito) {
                Toast.makeText(requireContext(), R.string.plato_guardado, Toast.LENGTH_SHORT).show();
                BottomNavigationView menu = requireActivity().findViewById(R.id.bottom_nav);
                menu.setSelectedItemId(R.id.nav_historial);
            } else {
                btnGuardar.setEnabled(true);
                btnGuardar.setText(R.string.btn_guardar);
                Toast.makeText(requireContext(), R.string.error_guardar, Toast.LENGTH_LONG).show();
            }
        });
    }

    // Tipo de comida: el que marcó el usuario o, si no marcó ninguno, el de la hora
    private String tipoElegido(ChipGroup grupo) {
        int chip = grupo.getCheckedChipId();
        if (chip == View.NO_ID) {
            chip = chipSegunHora();
        }
        if (chip == R.id.chip_desayuno) {
            return Plato.DESAYUNO;
        } else if (chip == R.id.chip_almuerzo) {
            return Plato.ALMUERZO;
        } else if (chip == R.id.chip_cena) {
            return Plato.CENA;
        }
        return Plato.SNACK;
    }

    // Convierte el resultado del modelo en el plato que se guarda, con la hora actual.
    // Si el usuario corrigió los ingredientes, se guardan los suyos con probabilidad 100 %.
    private Plato crearPlato(ResultadoPrediccion r, String tipoComida) {
        boolean editado = ingredientesEditados != null;
        List<Plato.IngredienteGuardado> ingredientes = new ArrayList<>();

        if (editado) {
            for (String nombre : ingredientesEditados) {
                // Se conserva lo que había dicho el modelo, para poder comparar después
                ingredientes.add(new Plato.IngredienteGuardado(
                        nombre, 1.0, probabilidadDelModelo(nombre), true));
            }
        } else {
            for (ResultadoPrediccion.Ingrediente ingrediente : r.masProbables) {
                double probabilidad = Math.round(ingrediente.probabilidad * 1000) / 1000.0;
                ingredientes.add(new Plato.IngredienteGuardado(
                        ingrediente.nombre, probabilidad, probabilidad, false));
            }
        }

        double umbral = Math.round(r.umbral * 1000) / 1000.0;
        return new Plato(Math.round(r.calorias), Math.round(r.carbohidratos),
                Math.round(r.proteinas), Math.round(r.grasas),
                tipoComida, System.currentTimeMillis(), umbral, editado, ingredientes);
    }

    // Lo que el modelo daba a este ingrediente; 0 si no es uno de los que conoce
    private double probabilidadDelModelo(String nombre) {
        for (ResultadoPrediccion.Ingrediente ingrediente : resultadoActual.todas) {
            if (ingrediente.nombre.equalsIgnoreCase(nombre)) {
                return Math.round(ingrediente.probabilidad * 1000) / 1000.0;
            }
        }
        return 0;
    }

    // ---------- Ingredientes: mostrar y editar ----------

    // Dibuja las etiquetas: las del modelo, o las del usuario si ya las corrigió
    private void pintarIngredientes(View view) {
        ChipGroup grupo = view.findViewById(R.id.grupo_ingredientes);
        TextView txtSin = view.findViewById(R.id.txt_sin_ingredientes);
        TextView txtConfirmados = view.findViewById(R.id.txt_confirmados);
        grupo.removeAllViews();

        boolean editado = ingredientesEditados != null;
        List<String> nombres = new ArrayList<>();
        if (editado) {
            nombres.addAll(ingredientesEditados);
        } else {
            for (ResultadoPrediccion.Ingrediente ingrediente : resultadoActual.ingredientes) {
                nombres.add(ingrediente.nombre);
            }
        }

        txtConfirmados.setVisibility(editado && !nombres.isEmpty() ? View.VISIBLE : View.GONE);
        txtSin.setText(editado ? R.string.sin_ingredientes_editado : R.string.sin_ingredientes);
        txtSin.setVisibility(nombres.isEmpty() ? View.VISIBLE : View.GONE);

        for (String nombre : nombres) {
            Chip chip = new Chip(requireContext());
            chip.setText(nombre);
            chip.setCheckable(false);
            chip.setClickable(false);
            chip.setChipStrokeWidth(0f);
            if (editado) {
                // Verde oscuro: ingredientes confirmados por el usuario
                chip.setChipBackgroundColorResource(R.color.verde_principal);
                chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
            } else {
                chip.setChipBackgroundColorResource(R.color.verde_claro);
                chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.verde_oscuro));
            }
            grupo.addView(chip);
        }
    }

    // Ventana para quitar y añadir ingredientes
    private void abrirEditor(View view) {
        if (resultadoActual == null) {
            return;
        }
        View dialogo = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_editar_ingredientes, null);
        ChipGroup grupo = dialogo.findViewById(R.id.grupo_edicion);
        AutoCompleteTextView entrada = dialogo.findViewById(R.id.entrada_ingrediente);

        // Se trabaja sobre una copia: si el usuario cancela, no se pierde nada
        List<String> trabajo = new ArrayList<>();
        if (ingredientesEditados != null) {
            trabajo.addAll(ingredientesEditados);
        } else {
            for (ResultadoPrediccion.Ingrediente ingrediente : resultadoActual.ingredientes) {
                trabajo.add(ingrediente.nombre);
            }
        }
        for (String nombre : new ArrayList<>(trabajo)) {
            crearChipEdicion(grupo, trabajo, nombre);
        }

        // Sugerencias: los 75 ingredientes que conoce el modelo (también vale texto libre)
        List<String> sugerencias = new ArrayList<>();
        for (ResultadoPrediccion.Ingrediente ingrediente : resultadoActual.todas) {
            sugerencias.add(ingrediente.nombre);
        }
        entrada.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, sugerencias));

        Runnable anadir = () -> {
            String texto = entrada.getText().toString().trim().toLowerCase(Locale.getDefault());
            entrada.setText("");
            if (texto.isEmpty() || contiene(trabajo, texto)) {
                return;
            }
            trabajo.add(texto);
            crearChipEdicion(grupo, trabajo, texto);
        };
        dialogo.findViewById(R.id.btn_anadir_ingrediente).setOnClickListener(v -> anadir.run());
        entrada.setOnEditorActionListener((v, accion, evento) -> {
            anadir.run();
            return true;
        });
        entrada.setOnItemClickListener((padre, v, posicion, id) -> anadir.run());

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.editar_titulo)
                .setView(dialogo)
                .setNegativeButton(R.string.cancelar, null)
                .setPositiveButton(R.string.editar_aplicar, (d, boton) -> {
                    ingredientesEditados = new ArrayList<>(trabajo);
                    pintarIngredientes(view);
                })
                .show();
    }

    // Una etiqueta con ✕ dentro de la ventana de edición
    private void crearChipEdicion(ChipGroup grupo, List<String> trabajo, String nombre) {
        Chip chip = new Chip(requireContext());
        chip.setText(nombre);
        chip.setCheckable(false);
        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(v -> {
            trabajo.remove(nombre);
            grupo.removeView(chip);
        });
        grupo.addView(chip);
    }

    // Indica si el nombre ya está en la lista, sin distinguir mayúsculas
    private static boolean contiene(List<String> lista, String nombre) {
        for (String elemento : lista) {
            if (elemento.equalsIgnoreCase(nombre)) {
                return true;
            }
        }
        return false;
    }

    // Reduce la foto, la comprime como JPEG y la convierte en texto para guardarla en Firebase
    private static String fotoABase64(Bitmap original) {
        int ladoMax = 400;
        float escala = Math.min(1f, (float) ladoMax / Math.max(original.getWidth(), original.getHeight()));
        Bitmap reducida = Bitmap.createScaledBitmap(original,
                Math.round(original.getWidth() * escala),
                Math.round(original.getHeight() * escala), true);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        reducida.compress(Bitmap.CompressFormat.JPEG, 75, salida);
        return Base64.encodeToString(salida.toByteArray(), Base64.NO_WRAP);
    }

    // Devuelve el chip que corresponde a la hora actual del móvil
    private int chipSegunHora() {
        int hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hora >= 5 && hora < 11) {
            return R.id.chip_desayuno;
        } else if (hora >= 11 && hora < 16) {
            return R.id.chip_almuerzo;
        } else if (hora >= 19 && hora < 23) {
            return R.id.chip_cena;
        }
        return R.id.chip_snack;   // de 16 a 19 y de madrugada
    }

    private void volver() {
        getParentFragmentManager().popBackStack();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hilo.shutdownNow();   // cancelamos el análisis si el usuario sale de la pantalla
    }
}
