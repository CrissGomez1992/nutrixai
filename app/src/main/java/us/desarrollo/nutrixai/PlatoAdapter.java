package us.desarrollo.nutrixai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Dibuja la lista del historial: una fila por plato
public class PlatoAdapter extends RecyclerView.Adapter<PlatoAdapter.Fila> {

    public interface AlPedirBorrar {
        void alPedirBorrar(Plato plato);
    }

    private final List<Plato> platos = new ArrayList<>();
    private final Map<String, Bitmap> fotos = new HashMap<>();   // fotos ya descargadas
    private final RepositorioPlatos repositorio;
    private final AlPedirBorrar oyenteBorrar;

    public PlatoAdapter(RepositorioPlatos repositorio, AlPedirBorrar oyenteBorrar) {
        this.repositorio = repositorio;
        this.oyenteBorrar = oyenteBorrar;
    }

    // Sustituye la lista por la nueva y olvida las fotos de platos que ya no existen
    public void actualizar(List<Plato> nuevos) {
        platos.clear();
        platos.addAll(nuevos);

        Set<String> ids = new HashSet<>();
        for (Plato plato : nuevos) {
            ids.add(plato.id);
        }
        fotos.keySet().retainAll(ids);

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
        View vista = LayoutInflater.from(padre.getContext()).inflate(R.layout.item_plato, padre, false);
        return new Fila(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull Fila fila, int posicion) {
        Plato plato = platos.get(posicion);
        Context contexto = fila.itemView.getContext();

        fila.txtKcal.setText(contexto.getString(R.string.kcal_valor, plato.calorias));
        fila.txtMacros.setText(contexto.getString(R.string.macros_resumen,
                plato.carbohidratos, plato.proteinas, plato.grasas));
        fila.txtTipoFecha.setText(contexto.getString(R.string.tipo_fecha,
                nombreTipo(contexto, plato.tipoComida), formatearFecha(contexto, plato.fecha)));

        String ingredientes = ingredientesVisibles(plato);
        fila.txtIngredientes.setText(ingredientes);
        fila.txtIngredientes.setVisibility(ingredientes.isEmpty() ? View.GONE : View.VISIBLE);

        fila.btnBorrar.setOnClickListener(v -> oyenteBorrar.alPedirBorrar(plato));

        // La foto: si ya se descargó se usa; si no, se pide solo la de esta fila
        fila.idPlato = plato.id;
        Bitmap foto = fotos.get(plato.id);
        if (foto != null) {
            fila.imgMiniatura.setImageBitmap(foto);
        } else {
            fila.imgMiniatura.setImageDrawable(null);
            repositorio.cargarFoto(plato.id, base64 -> {
                Bitmap descargada = decodificar(base64);
                if (descargada == null) {
                    return;
                }
                fotos.put(plato.id, descargada);
                // La fila se pudo reutilizar para otro plato mientras llegaba la foto
                if (plato.id.equals(fila.idPlato)) {
                    fila.imgMiniatura.setImageBitmap(descargada);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return platos.size();
    }

    // ---------- Ayudas ----------

    private static Bitmap decodificar(String base64) {
        if (base64 == null) {
            return null;
        }
        byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
        BitmapFactory.Options opciones = new BitmapFactory.Options();
        opciones.inSampleSize = 2;   // la mitad de tamaño: la miniatura es pequeña
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opciones);
    }

    private static String nombreTipo(Context contexto, String tipo) {
        if (Plato.DESAYUNO.equals(tipo)) {
            return contexto.getString(R.string.tipo_desayuno);
        } else if (Plato.ALMUERZO.equals(tipo)) {
            return contexto.getString(R.string.tipo_almuerzo);
        } else if (Plato.CENA.equals(tipo)) {
            return contexto.getString(R.string.tipo_cena);
        }
        return contexto.getString(R.string.tipo_snack);
    }

    // "Hoy, 14:05", "Ayer, 20:40" o "12 nov, 14:05"
    private static String formatearFecha(Context contexto, long milisegundos) {
        String hora = DateFormat.getTimeFormat(contexto).format(new Date(milisegundos));
        if (DateUtils.isToday(milisegundos)) {
            return contexto.getString(R.string.fecha_hoy, hora);
        }
        if (DateUtils.isToday(milisegundos + DateUtils.DAY_IN_MILLIS)) {
            return contexto.getString(R.string.fecha_ayer, hora);
        }
        String dia = new SimpleDateFormat("d MMM", Locale.getDefault()).format(new Date(milisegundos));
        return contexto.getString(R.string.fecha_otro, dia, hora);
    }

    // Los ingredientes que superaron el umbral cuando se guardó el plato
    private static String ingredientesVisibles(Plato plato) {
        if (plato.ingredientes == null) {
            return "";
        }
        StringBuilder texto = new StringBuilder();
        for (Plato.IngredienteGuardado ingrediente : plato.ingredientes) {
            if (ingrediente.probabilidad >= plato.umbral) {
                if (texto.length() > 0) {
                    texto.append(", ");
                }
                texto.append(ingrediente.nombre);
            }
        }
        // Una ✓ indica que el usuario revisó y confirmó los ingredientes
        if (plato.editado && texto.length() > 0) {
            return "✓ " + texto;
        }
        return texto.toString();
    }

    // Guarda las vistas de una fila para no buscarlas cada vez
    static class Fila extends RecyclerView.ViewHolder {
        final ImageView imgMiniatura;
        final TextView txtKcal;
        final TextView txtMacros;
        final TextView txtTipoFecha;
        final TextView txtIngredientes;
        final ImageButton btnBorrar;
        String idPlato;

        Fila(@NonNull View vista) {
            super(vista);
            imgMiniatura = vista.findViewById(R.id.img_miniatura);
            txtKcal = vista.findViewById(R.id.txt_kcal);
            txtMacros = vista.findViewById(R.id.txt_macros);
            txtTipoFecha = vista.findViewById(R.id.txt_tipo_fecha);
            txtIngredientes = vista.findViewById(R.id.txt_ingredientes);
            btnBorrar = vista.findViewById(R.id.btn_borrar);
        }
    }
}
