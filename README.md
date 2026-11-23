# NutrixAI

Aplicación Android que estima las **calorías y los gramos de carbohidratos, proteínas y grasas** de un plato a partir de una foto, y propone los **ingredientes más probables**. El análisis se hace en el propio móvil con un modelo de TensorFlow Lite obtenido con *transfer learning*. Los platos analizados se guardan en un historial en la nube.

Trabajo fin de curso de *Aplicaciones móviles*.

> **Aviso.** Es un prototipo académico. Las cifras son estimaciones hechas a partir de una imagen y no sustituyen el consejo de un profesional sanitario. No deben usarse para decidir la dosis de insulina ni otras decisiones de tratamiento.

## Qué hace

- Toma una foto con la cámara o elige una de la galería.
- Muestra calorías, carbohidratos, proteínas y grasas, con una barra de proporciones.
- Propone hasta 3 ingredientes si superan un umbral de probabilidad, y permite ver el detalle de probabilidades.
- Permite **editar los ingredientes**: los que confirma el usuario se guardan con probabilidad 100 %, junto a la probabilidad que dio el modelo.
- Registra el tipo de comida (desayuno, almuerzo, cena o snack) y la hora. Si el usuario no elige tipo, se usa el que corresponde a la hora.
- Guarda el plato con su foto en un historial por usuario, con resumen, y permite borrarlo.

## Tecnologías

| Parte | Tecnología |
|---|---|
| App | Android (Java), Material 3, RecyclerView. minSdk 24, targetSdk 37 |
| Modelo en el móvil | LiteRT 2.1.0 (antes TensorFlow Lite), clase `Interpreter` |
| Modelo base | MobileNetV2 preentrenada en ImageNet (transfer learning) |
| Dataset | [Nutrition5k](https://github.com/google-research-datasets/nutrition5k) (Google Research, CC BY 4.0), subconjunto de fotos cenitales |
| Nube | Firebase Authentication (anónimo) y Realtime Database |

## Estructura

```
app/src/main/
├── java/us/desarrollo/nutrixai/
│   ├── MainActivity.java          Marco de la app, menú inferior e inicio de sesión anónimo
│   ├── HomeFragment.java          Inicio: cámara y galería
│   ├── ResultFragment.java        Análisis, resultados, edición de ingredientes y guardado
│   ├── HistoryFragment.java       Historial y resumen
│   ├── PlatoAdapter.java          Lista del historial
│   ├── ModeloNutricion.java       Carga el modelo y ejecuta la predicción
│   ├── RepositorioPlatos.java     Acceso a Firebase (guardar, escuchar, borrar)
│   ├── Plato.java                 Datos de un plato guardado
│   └── ResultadoPrediccion.java   Datos que devuelve el modelo
├── assets/
│   ├── nutrition_ing_model.tflite Modelo (unos 2,7 MB)
│   └── nutrition_ing_scaler.json  Medias, desviaciones, ingredientes y umbral
└── res/                           Diseños, textos, colores y temas
```

## Cómo compilar y ejecutar

El archivo `app/google-services.json` **no está en el repositorio**, porque contiene la configuración de un proyecto de Firebase concreto. Sin él, la app no compila. Hay que crear uno propio, y es gratuito:

1. Clona el repositorio y ábrelo con Android Studio.
2. En la [consola de Firebase](https://console.firebase.google.com/) crea un proyecto en el plan **Spark** (gratuito).
3. Añade una app Android con el nombre de paquete **`us.desarrollo.nutrixai`** y descarga su `google-services.json`. Cópialo en la carpeta `app/`. En `app/google-services.json.example` hay una plantilla de su estructura.
4. Activa **Authentication** y, en *Método de acceso*, el proveedor **Anónimo**.
5. Crea una **Realtime Database** en modo bloqueado y publica estas reglas:

```json
{
  "rules": {
    "usuarios": {
      "$uid": {
        ".read": "auth != null && auth.uid === $uid",
        ".write": "auth != null && auth.uid === $uid"
      }
    }
  }
}
```

6. Sincroniza Gradle y ejecuta la app en un móvil o emulador con Android 7.0 (API 24) o superior. La cámara real se prueba mejor en un móvil físico.

No se usa Cloud Storage, que exige un plan de pago. Las fotos se reducen a un máximo de 400 píxeles, se comprimen como JPEG y se guardan como texto en la propia base de datos.

## Datos en Firebase

```
usuarios/<uid>/platos/<idPlato>   calorías, macros, tipoComida, fecha, umbral, editado, ingredientes
usuarios/<uid>/fotos/<idPlato>    foto JPEG en base64
```

Cada ingrediente guarda `nombre`, `probabilidad` (1.0 si lo confirma el usuario), `probabilidadModelo` y `confirmado`.

## El modelo

Un único modelo con dos salidas, entrenado sobre fotos de Nutrition5k redimensionadas a 224 × 224:

- **Macros:** regresión de 4 valores (calorías, carbohidratos, proteínas y grasas), normalizados con la media y la desviación del entrenamiento.
- **Ingredientes:** clasificación multietiqueta de 75 ingredientes, con una probabilidad independiente para cada uno.

La entrada es `float32` de 224 × 224 × 3 con valores de 0 a 255, porque la normalización está dentro del modelo. La app identifica las dos salidas por su forma, `[1,4]` y `[1,75]`, y recupera kcal y gramos con `valor = salida × desviación + media`. Las métricas y el proceso de entrenamiento están descritos en la memoria del trabajo.

## Limitaciones

- El dataset son platos de comedores de California, fotografiados desde arriba. Con platos caseros u otros ángulos el error puede ser mayor.
- Estimar la cantidad a partir de una foto en dos dimensiones es difícil.
- El reconocimiento de ingredientes es modesto, y los nombres están en inglés.
- Nutrition5k no incluye fibra ni mediciones de glucosa.

## Créditos

- Thames, Q. et al. (2021). *Nutrition5k: Towards Automatic Nutritional Understanding of Generic Food*. CVPR. Dataset con licencia CC BY 4.0.
- Sandler, M. et al. (2018). *MobileNetV2: Inverted Residuals and Linear Bottlenecks*. CVPR.

Autora: Cristina Gomez ([@CrissGomez1992](https://github.com/CrissGomez1992)).
