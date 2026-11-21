package us.desarrollo.nutrixai;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_main);

        // Deja margen para la barra de estado y los botones de navegación del sistema
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        BottomNavigationView menu = findViewById(R.id.bottom_nav);

        // Qué hacer cuando el usuario pulsa una opción del menú inferior
        menu.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_escanear) {
                mostrar(new HomeFragment());
                return true;
            } else if (item.getItemId() == R.id.nav_historial) {
                mostrar(new HistoryFragment());
                return true;
            }
            return false;
        });

        // Al abrir la app por primera vez, empezamos en "Escanear"
        if (savedInstanceState == null) {
            menu.setSelectedItemId(R.id.nav_escanear);
        }
    }

    // Cambia el contenido del hueco central por el fragmento indicado
    private void mostrar(Fragment fragmento) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.contenedor, fragmento)
                .commit();
    }
}
