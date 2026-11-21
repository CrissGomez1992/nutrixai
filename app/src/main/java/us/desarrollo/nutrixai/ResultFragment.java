package us.desarrollo.nutrixai;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.Calendar;

public class ResultFragment extends Fragment {

    private static final String ARG_FOTO = "foto_uri";

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

        // 1. Mostrar la foto que nos han pasado
        String uriTexto = getArguments() != null ? getArguments().getString(ARG_FOTO) : null;
        if (uriTexto == null) {
            Toast.makeText(requireContext(), R.string.error_foto, Toast.LENGTH_SHORT).show();
            volver();
            return;
        }
        ImageView imgFoto = view.findViewById(R.id.img_foto);
        imgFoto.setImageURI(Uri.parse(uriTexto));

        // 2. Tipo de comida: lo elige el usuario; si no elige, se usará el de la hora
        ChipGroup grupoTipo = view.findViewById(R.id.grupo_tipo_comida);
        TextView txtSugerencia = view.findViewById(R.id.txt_sugerencia);
        Chip chipSugerido = view.findViewById(chipSegunHora());
        txtSugerencia.setText(getString(R.string.tipo_comida_sugerencia, chipSugerido.getText()));

        // El aviso solo se ve mientras no haya ningún chip marcado
        grupoTipo.setOnCheckedStateChangeListener((grupo, marcados) ->
                txtSugerencia.setVisibility(marcados.isEmpty() ? View.VISIBLE : View.GONE));

        // 3. El botón "Nueva foto" vuelve a la pantalla anterior
        view.findViewById(R.id.btn_nueva_foto).setOnClickListener(v -> volver());
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
}
