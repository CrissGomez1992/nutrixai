package us.desarrollo.nutrixai;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Lee y escribe los platos del usuario en Firebase Realtime Database
public class RepositorioPlatos {

    // Se avisa con true si salió bien y con false si falló
    public interface Resultado {
        void alTerminar(boolean exito);
    }

    // Guarda el plato y su foto. Si todavía no hay usuario, lo crea antes de guardar.
    public void guardar(Plato plato, String fotoBase64, Resultado resultado) {
        FirebaseAuth autenticacion = FirebaseAuth.getInstance();
        FirebaseUser usuario = autenticacion.getCurrentUser();

        if (usuario != null) {
            escribir(usuario.getUid(), plato, fotoBase64, resultado);
        } else {
            autenticacion.signInAnonymously()
                    .addOnSuccessListener(sesion ->
                            escribir(sesion.getUser().getUid(), plato, fotoBase64, resultado))
                    .addOnFailureListener(error -> resultado.alTerminar(false));
        }
    }

    private void escribir(String idUsuario, Plato plato, String fotoBase64, Resultado resultado) {
        DatabaseReference carpeta = FirebaseDatabase.getInstance()
                .getReference("usuarios")
                .child(idUsuario);

        // Firebase crea un identificador único y ordenado en el tiempo para el plato
        String idPlato = carpeta.child("platos").push().getKey();
        if (idPlato == null) {
            resultado.alTerminar(false);
            return;
        }

        // Los dos datos se escriben a la vez: o se guardan los dos o ninguno
        Map<String, Object> cambios = new HashMap<>();
        cambios.put("platos/" + idPlato, plato);
        cambios.put("fotos/" + idPlato, fotoBase64);

        carpeta.updateChildren(cambios)
                .addOnCompleteListener(tarea -> resultado.alTerminar(tarea.isSuccessful()));
    }

    // ---------- Leer ----------

    public interface AlRecibirPlatos {
        void alRecibir(List<Plato> platos);   // la lista llega cada vez que algo cambia
        void alFallar();
    }

    public interface AlRecibirFoto {
        void alRecibir(String fotoBase64);    // null si el plato no tiene foto o falló
    }

    // Escucha los platos del usuario en tiempo real, del más reciente al más antiguo.
    // Devuelve una acción que hay que ejecutar para dejar de escuchar.
    public Runnable escucharPlatos(AlRecibirPlatos oyente) {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            oyente.alFallar();
            return () -> { };
        }

        DatabaseReference platos = carpetaDe(usuario.getUid()).child("platos");
        ValueEventListener escucha = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot datos) {
                List<Plato> lista = new ArrayList<>();
                for (DataSnapshot hijo : datos.getChildren()) {
                    Plato plato = hijo.getValue(Plato.class);
                    if (plato != null) {
                        plato.id = hijo.getKey();   // el identificador es el nombre del nodo
                        lista.add(plato);
                    }
                }
                Collections.sort(lista, (a, b) -> Long.compare(b.fecha, a.fecha));
                oyente.alRecibir(lista);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                oyente.alFallar();
            }
        };
        platos.addValueEventListener(escucha);
        return () -> platos.removeEventListener(escucha);
    }

    // Descarga la foto de un plato (solo esa, para no cargar todas a la vez)
    public void cargarFoto(String idPlato, AlRecibirFoto oyente) {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            oyente.alRecibir(null);
            return;
        }
        carpetaDe(usuario.getUid()).child("fotos").child(idPlato).get()
                .addOnCompleteListener(tarea -> {
                    String foto = tarea.isSuccessful()
                            ? tarea.getResult().getValue(String.class) : null;
                    oyente.alRecibir(foto);
                });
    }

    // ---------- Borrar ----------

    // Borra el plato y su foto a la vez
    public void borrar(String idPlato, Resultado resultado) {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            resultado.alTerminar(false);
            return;
        }
        // Escribir null en una ruta equivale a borrarla
        Map<String, Object> cambios = new HashMap<>();
        cambios.put("platos/" + idPlato, null);
        cambios.put("fotos/" + idPlato, null);
        carpetaDe(usuario.getUid()).updateChildren(cambios)
                .addOnCompleteListener(tarea -> resultado.alTerminar(tarea.isSuccessful()));
    }

    private DatabaseReference carpetaDe(String idUsuario) {
        return FirebaseDatabase.getInstance().getReference("usuarios").child(idUsuario);
    }
}
