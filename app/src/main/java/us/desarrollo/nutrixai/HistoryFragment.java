package us.desarrollo.nutrixai;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public class HistoryFragment extends Fragment {

    private final RepositorioPlatos repositorio = new RepositorioPlatos();
    private Runnable dejarDeEscuchar;   // corta la conexión con Firebase al salir de la pantalla

    public HistoryFragment() {
        super(R.layout.fragment_history);   // diseño de esta pantalla
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView lista = view.findViewById(R.id.lista_platos);
        PlatoAdapter adaptador = new PlatoAdapter(repositorio, this::confirmarBorrado);
        lista.setAdapter(adaptador);

        View cargando = view.findViewById(R.id.cargando);
        View txtVacio = view.findViewById(R.id.txt_vacio);
        View tarjetaResumen = view.findViewById(R.id.tarjeta_resumen);

        // Firebase avisa cada vez que cambia algo: al abrir, al guardar y al borrar
        dejarDeEscuchar = repositorio.escucharPlatos(new RepositorioPlatos.AlRecibirPlatos() {
            @Override
            public void alRecibir(List<Plato> platos) {
                if (!isAdded()) {
                    return;
                }
                cargando.setVisibility(View.GONE);
                adaptador.actualizar(platos);
                txtVacio.setVisibility(platos.isEmpty() ? View.VISIBLE : View.GONE);
                tarjetaResumen.setVisibility(platos.isEmpty() ? View.GONE : View.VISIBLE);
                mostrarResumen(view, platos);
            }

            @Override
            public void alFallar() {
                if (!isAdded()) {
                    return;
                }
                cargando.setVisibility(View.GONE);
                Toast.makeText(requireContext(), R.string.error_historial, Toast.LENGTH_LONG).show();
            }
        });
    }

    // Nº de platos, media de calorías y calorías de hoy
    private void mostrarResumen(View view, List<Plato> platos) {
        int total = platos.size();
        int suma = 0;
        int hoy = 0;
        for (Plato plato : platos) {
            suma += plato.calorias;
            if (DateUtils.isToday(plato.fecha)) {
                hoy += plato.calorias;
            }
        }
        int media = total == 0 ? 0 : Math.round((float) suma / total);

        ((TextView) view.findViewById(R.id.txt_resumen_platos)).setText(String.valueOf(total));
        ((TextView) view.findViewById(R.id.txt_resumen_media)).setText(String.valueOf(media));
        ((TextView) view.findViewById(R.id.txt_resumen_hoy)).setText(String.valueOf(hoy));
    }

    // Pregunta antes de borrar, porque no se puede deshacer
    private void confirmarBorrado(Plato plato) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.borrar_titulo)
                .setMessage(R.string.borrar_mensaje)
                .setNegativeButton(R.string.cancelar, null)
                .setPositiveButton(R.string.borrar, (dialogo, boton) ->
                        repositorio.borrar(plato.id, exito -> {
                            if (isAdded()) {
                                Toast.makeText(requireContext(),
                                        exito ? R.string.plato_borrado : R.string.error_borrar,
                                        Toast.LENGTH_SHORT).show();
                            }
                        }))
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dejarDeEscuchar != null) {
            dejarDeEscuchar.run();
        }
    }
}
