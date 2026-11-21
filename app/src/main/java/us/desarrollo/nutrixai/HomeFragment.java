package us.desarrollo.nutrixai;

import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import java.io.File;

public class HomeFragment extends Fragment {

    // Lanzador de la cámara del sistema: nos avisa con true si se hizo la foto
    private final ActivityResultLauncher<Uri> lanzadorCamara =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), seHizoFoto -> {
                if (seHizoFoto) {
                    abrirResultado(uriFotoCamara());
                }
            });

    // Lanzador del selector de fotos: nos devuelve la foto elegida, o null si se canceló
    private final ActivityResultLauncher<PickVisualMediaRequest> lanzadorGaleria =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    abrirResultado(uri);
                }
            });

    public HomeFragment() {
        super(R.layout.fragment_home);   // diseño de esta pantalla
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btn_hacer_foto).setOnClickListener(v -> {
            try {
                lanzadorCamara.launch(uriFotoCamara());
            } catch (ActivityNotFoundException e) {
                // El móvil no tiene aplicación de cámara
                Toast.makeText(requireContext(), R.string.error_foto, Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.btn_galeria).setOnClickListener(v ->
                lanzadorGaleria.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
    }

    // Dirección (content://) del archivo donde la cámara dejará la foto
    private Uri uriFotoCamara() {
        File carpeta = new File(requireContext().getCacheDir(), "fotos");   // la de file_paths.xml
        carpeta.mkdirs();                                                    // la crea si no existe
        File archivo = new File(carpeta, "plato.jpg");
        return FileProvider.getUriForFile(requireContext(),
                requireContext().getPackageName() + ".fileprovider", archivo);
    }

    // Abre la pantalla de resultado, apilándola sobre la actual
    private void abrirResultado(Uri foto) {
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.contenedor, ResultFragment.nuevo(foto))
                .addToBackStack(null)
                .commit();
    }
}

