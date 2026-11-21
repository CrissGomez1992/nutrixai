package us.desarrollo.nutrixai;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class HomeFragment extends Fragment {

    public HomeFragment() {
        super(R.layout.fragment_home);   // le decimos qué diseño usar
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Por ahora los botones solo avisan; en la parte 2 abrirán la cámara y la galería
        view.findViewById(R.id.btn_hacer_foto).setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.pronto, Toast.LENGTH_SHORT).show());

        view.findViewById(R.id.btn_galeria).setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.pronto, Toast.LENGTH_SHORT).show());
    }
}

