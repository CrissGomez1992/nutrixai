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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ResultFragment extends Fragment {

    private static final String ARG_FOTO = "foto_uri";
    private static final int LADO_MAX = 1024;   // la foto se reduce a este tamaño aproximado

    // Un hilo secundario para el análisis, y un "mensajero" para volver al hilo principal
    private final ExecutorService hilo = Executors.newSingleThreadExecutor();
    private final Handler principal = new Handler(Looper.getMainLooper());

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

        // 3. Cargar la foto y analizarla, todo en el hilo secundario
        Uri foto = Uri.parse(uriTexto);
        Context contexto = requireContext().getApplicationContext();
        ImageView imgFoto = view.findViewById(R.id.img_foto);

        hilo.execute(() -> {
            try {
                Bitmap bitmap = cargarBitmap(contexto, foto);
                principal.post(() -> {
                    if (isAdded()) {
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
        ChipGroup grupoIngredientes = view.findViewById(R.id.grupo_ingredientes);
        TextView txtSinIngredientes = view.findViewById(R.id.txt_sin_ingredientes);
        grupoIngredientes.removeAllViews();

        if (r.ingredientes.isEmpty()) {
            // Ningún ingrediente supera el umbral: se avisa en lugar de mostrar suposiciones débiles
            txtSinIngredientes.setVisibility(View.VISIBLE);
        } else {
            txtSinIngredientes.setVisibility(View.GONE);
            for (ResultadoPrediccion.Ingrediente ingrediente : r.ingredientes) {
                Chip chip = new Chip(requireContext());
                chip.setText(ingrediente.nombre);
                chip.setCheckable(false);
                chip.setClickable(false);
                chip.setChipBackgroundColorResource(R.color.verde_claro);
                chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.verde_oscuro));
                chip.setChipStrokeWidth(0f);
                grupoIngredientes.addView(chip);
            }
        }

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
