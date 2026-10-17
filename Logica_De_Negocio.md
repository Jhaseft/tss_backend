# CLAUDE.md — Lógica de negocio: Sistema de Carga y Análisis de Encuestas (Titulados de Ingeniería en Sistemas, UMSS)

> Este documento explica **QUÉ debe hacer el sistema y con qué reglas**.
> El equipo define la base de datos, la arquitectura y el stack (Spring Boot + React + PostgreSQL). Respeta lo que ya exista en el repo y pregunta antes de imponer estructura.

---

## 1. Propósito

Es un trabajo universitario: el **Análisis de Mercado sobre el perfil, la formación continua y la empleabilidad de los titulados de Ingeniería en Sistemas (UMSS)**. Sirve como insumo para el Plan de Desarrollo Estratégico y la acreditación **ARCUSUR**.

Las encuestas reales se llenaron fuera del sistema:
- **Titulados:** Microsoft Forms.
- **Empleadores:** Google Forms.

En ambos casos se exportan a Excel o CSV.

**El sistema NO crea ni publica formularios para que alguien los llene.** Su trabajo es:

1. **Cargar** el Excel o CSV exportado.
2. **Reconstruir dinámicamente la encuesta** a partir del archivo: secciones, preguntas, tipo de cada pregunta, opciones y lógica condicional. Se muestra como si la encuesta hubiera sido diseñada en nuestra plataforma; es una vista de solo lectura.
3. **Registrar cada fila como una respuesta (envío) de esa encuesta**, con su fecha y hora original. Así los datos quedan como si hubieran sido recolectados por la plataforma.
4. **Analizar**: estadística descriptiva, **cruce de variables con Chi-cuadrado**, visualización, **simulación de escenarios** y exportación de resultados para el informe.

Cada archivo cargado genera una encuesta independiente con sus propios datos y análisis. Se puede cargar más de un archivo, por ejemplo uno de titulados y otro de empleadores.

---

## 2. Flujo general

```
Cargar archivo → Detectar origen → Inferir preguntas y tipos → Revisar/ajustar → Confirmar
     → Registrar envíos → Limpiar y normalizar → Variables derivadas
     → Análisis univariado → Cruces + Chi-cuadrado → Dashboard → Simulación → Exportar
```

---

## 3. Carga del archivo y reconstrucción de la encuesta

### 3.1 Lectura
- Acepta `.xlsx`, `.xls` y `.csv`.
- En CSV, detecta el separador (`,` o `;`) y la codificación (UTF-8 o Latin-1).
- Si el Excel tiene varias hojas, el usuario elige cuál cargar.
- La primera fila son los encabezados, y **cada encabezado es el texto de una pregunta**.

### 3.2 Detectar el origen y separar metadatos
Las columnas de metadatos **no son preguntas**:

| Origen | Columnas típicas | Qué se hace |
|---|---|---|
| **MS Forms** | `ID`, `Hora de inicio`, `Hora de finalización`, `Correo electrónico`, `Nombre`, `Hora de la última modificación` (o en inglés) | `Hora de finalización` = fecha del envío. El resto se guarda como metadato. |
| **Google Forms** | `Marca temporal`, `Dirección de correo electrónico` | `Marca temporal` = fecha del envío. |

Si no se reconoce el origen, se trata como un archivo genérico y el usuario indica qué columna es la fecha, si existe.

### 3.3 Inferir el tipo de cada pregunta
Para cada columna se analizan sus valores no vacíos. Llamamos `n` a la cantidad de valores no vacíos y `k` a la cantidad de valores distintos. Las reglas se aplican en este orden:

| Si… | Tipo |
|---|---|
| El encabezado dice correo/email, o los valores tienen formato de email | **Email** (dato personal) |
| El encabezado dice teléfono/celular/WhatsApp, o los valores parecen números de teléfono | **Teléfono** (dato personal) |
| El encabezado dice LinkedIn/link, o los valores son URLs | **URL** (dato personal) |
| Todos los valores son fechas | **Fecha** |
| Todos los valores son números y no son pocas categorías repetidas | **Numérica** |
| Los valores contienen separadores de selección múltiple y los elementos separados se repiten entre filas | **Selección múltiple** |
| Varias columnas comparten la pregunta con el patrón `Pregunta [Ítem]` (Google Forms) y la misma escala | **Matriz Likert** (se agrupan en una sola pregunta, con un ítem por columna) |
| Los valores coinciden con una escala Likert conocida (§3.5) | **Likert** (ordinal) |
| `k` es pequeño (≈ ≤ 15) y los valores se repiten | **Opción única**: ordinal si las opciones son rangos ordenables, nominal si no |
| Los textos son casi todos distintos y cortos | **Texto corto** |
| Los textos son largos (promedio > 80 caracteres) | **Texto largo** |

**Selección múltiple, cómo separar:**
- **MS Forms** separa con `;` y suele dejar un `;` al final.
- **Google Forms** separa con `, `. **Cuidado** con las opciones que tienen comas internas, como "Básicas (Física, Calculo, Algebras)". Primero intenta reconocer opciones completas ya conocidas, empezando por la coincidencia más larga, y solo después divide por el separador.
- Un elemento que aparece una sola vez y no coincide con ninguna opción frecuente se trata como respuesta de **"Otro"**: se guarda la opción "Otro" y el texto escrito.

**Opción única ordinal:** se detecta cuando las opciones forman una secuencia. Ejemplos:
- "Ya trabajaba / Menos de 1 mes / 1–4 meses / 4–8 / 8–12 / Más de 12 meses"
- "1 / 2 / 3 / Más de 3"
- Micro / Pequeña / Mediana / Grande
- Licenciatura → Post-doctorado
- Rangos de edad

Las opciones se guardan **en ese orden lógico**, no en orden alfabético.

**Normalización de valores:** quita espacios sobrantes y compara sin tildes ni mayúsculas para agrupar variantes ("Si", "SI", "Sí" son una sola opción). Como texto visible se muestra la variante más frecuente.

**Confianza:** cada inferencia tiene un nivel de confianza, y las de confianza baja se resaltan para que el usuario las revise.

### 3.4 Inferir la lógica condicional
Si la columna B está vacía en casi todas las filas (≥ 95 %) donde la columna A tiene cierto valor, y llena en las demás, se sugiere la regla **"B solo aplica si A = …"**.

Debe detectar al menos estas:
- "¿Ha realizado formación complementaria?" = **NO** → no aplican nivel, institución, financiamiento ni nombre del programa.
- "¿Interesado en posgrado?" = **NO** → no aplican área ni institución preferida.
- "Situación laboral" ≠ **emprendimiento** → no aplican origen, entregable, financiamiento, satisfacción ni importancia de la formación para el negocio.

Hay que distinguir dos casos: una respuesta vacía porque **no aplicaba** según la regla, y una respuesta vacía porque **el encuestado no respondió** (faltante). Se reportan por separado.

### 3.5 Escalas Likert reconocidas

| Escala | Valores (texto → número) |
|---|---|
| Acuerdo 4 puntos | Totalmente en desacuerdo = 1 · En desacuerdo = 2 · De acuerdo = 3 · Totalmente de acuerdo = 4 |
| Satisfacción 3 puntos | Insatisfecho = 1 · Algo satisfecho = 2 · Satisfecho = 3 |
| Importancia 4 puntos | No es importante = 1 · Poco importante = 2 · Importante = 3 · Muy importante = 4 |
| Empleadores | Totalmente desacuerdo = 1 · Parcialmente de acuerdo = 2 · Totalmente de acuerdo = 3 · **No sabe = sin número** |

**"No sabe"** se cuenta en las frecuencias, pero **se excluye de medias y desviaciones** y se reporta aparte como porcentaje.

Las escalas deben ser configurables para poder agregar otras.

### 3.6 Revisión antes de confirmar
El sistema muestra la encuesta reconstruida **como un formulario de solo lectura**, con secciones, preguntas en orden, opciones y reglas condicionales.

El usuario puede corregir la inferencia:
- cambiar el tipo de una pregunta;
- fusionar o renombrar opciones;
- reordenar opciones ordinales;
- marcar o desmarcar datos personales;
- aceptar o descartar reglas condicionales;
- ignorar columnas.

También ve un resumen previo: filas válidas, filas con problemas y valores que no encajaron en ninguna opción.

### 3.7 Confirmar
- Se crea la encuesta con su estructura y se registra **un envío por fila**, con su fecha original.
- Se genera un **reporte de carga**: filas cargadas, filas con error (fila, columna y motivo) y valores no reconocidos.
- **Evitar duplicados:** si se vuelve a subir el mismo archivo o las mismas filas, no se duplican.
- Si se carga **otro archivo del mismo formulario**, se pueden agregar sus filas a la encuesta ya creada, emparejando columnas con preguntas.

### 3.8 Validaciones de negocio
- Las preguntas numéricas deben ser números ≥ 0.
- **Tiempo desempleado ≤ años de vida profesional.** Si no se cumple, se marca la fila como inconsistente; no se descarta en silencio.
- En empleadores, el **nombre de la empresa** se normaliza (mayúsculas, sin espacios extra) para contar empresas únicas.
- **Datos personales** (email, teléfono, LinkedIn, nombre): se almacenan, pero **nunca aparecen en análisis, dashboard ni exportaciones analíticas**. Solo pueden salir en un export de contactos aparte.

---

## 4. Preparación de datos (Data Wrangling)

- Cada pregunta de **selección múltiple** se analiza también como un conjunto de variables **dicotómicas**, una por opción (marcó / no marcó).
- Las preguntas **Likert** se convierten a su número para calcular medias.
- **Variables derivadas** que el usuario puede crear y luego usar como cualquier pregunta:
  - **Agrupar o recodificar opciones**, por ejemplo para juntar categorías con muy pocas respuestas antes de un Chi-cuadrado.
  - **Discretizar una numérica en rangos.** Ejemplo obligatorio: *Años de vida profesional* → **Segmento de madurez**: Junior (< 3), Semi-senior (3–7), Consolidado (> 7), con umbrales editables.
  - **Razón entre dos numéricas.** Ejemplo: *tasa de desempleo individual* = años desempleado / años de vida profesional. Si el denominador es 0, el resultado queda vacío.
  - **Situación de ocupación**: "Ocupado" = trabaja en una organización o tiene emprendimiento; "No ocupado" = no trabaja.

---

## 5. Análisis univariado (por cada pregunta)

| Tipo | Qué calcular |
|---|---|
| Opción única (nominal) | Frecuencia absoluta, frecuencia relativa (%), moda |
| Opción única (ordinal) | Lo anterior + frecuencia acumulada, en orden lógico |
| Selección múltiple | % de encuestados que marcó cada opción (base = quienes respondieron la pregunta) y ranking |
| Numérica | n, media, mediana, desviación estándar, mínimo, máximo, Q1, Q3 |
| Likert / Matriz Likert | Por ítem: media, desviación estándar, mediana, % "de acuerdo" (dos categorías superiores), % "No sabe" |
| Texto | Términos más frecuentes (sin palabras vacías en español, normalizando tecnologías: "React.js" → "React") y listado de respuestas |

Cada resultado indica **n válido** y **n de faltantes**.

---

## 6. Cruce de variables con Chi-cuadrado (núcleo del análisis)

### 6.1 Qué se puede cruzar
- Cualquier par de variables **categóricas** de la misma encuesta: opción única, Likert, una opción de una múltiple tratada como dicotómica, o una variable derivada.
- Las **numéricas** deben discretizarse antes de cruzarse (§4).
- Todo cruce acepta **filtros**. Ejemplos: "solo titulados ocupados", "solo empresas privadas".

### 6.2 Qué entrega cada cruce
1. **Tabla de contingencia** con frecuencias observadas y totales por fila, por columna y general.
2. Porcentajes **por fila**, **por columna** y **sobre el total**.
3. **Frecuencias esperadas**: (total fila × total columna) / total general.
4. **Estadístico χ²** = Σ (Observada − Esperada)² / Esperada.
5. **Grados de libertad** = (filas − 1) × (columnas − 1).
6. **p-valor**.
7. **V de Cramér**, para medir la fuerza de la asociación, con interpretación orientativa: < 0.1 despreciable, 0.1–0.3 débil, 0.3–0.5 moderada, > 0.5 fuerte.
8. **Interpretación automática** con α = 0.05 (configurable):
   - Si p < α: "Se rechaza H₀: existe asociación estadísticamente significativa entre X e Y."
   - Si p ≥ α: "No hay evidencia suficiente para afirmar asociación entre X e Y."
9. **Control de supuestos:**
   - Si **más del 20 % de las celdas** tienen frecuencia esperada < 5, o alguna esperada es < 1, se muestra una advertencia y se sugiere agrupar categorías.
   - En tablas **2×2**, también se calcula el **test exacto de Fisher**.
   - Las filas o columnas con total 0 se excluyen.

Hipótesis de cada cruce:
- **H₀:** las variables son independientes.
- **H₁:** existe asociación.

### 6.3 Cruces obligatorios del trabajo
El sistema debe ofrecerlos como accesos rápidos, cuando las preguntas existen en la encuesta cargada.

**Encuesta de titulados**

| # | Variable fila | Variable columna | Qué responde |
|---|---|---|---|
| T1 | Fuente de financiamiento | Nivel del programa (diplomado, maestría…) | ¿Hay dependencia financiera según el nivel? (HU3) |
| T2 | Área de interés de posgrado | Segmento de madurez | ¿Juniors y consolidados prefieren áreas distintas? |
| T3 | Área de interés de posgrado | Género | ¿Las preferencias difieren por género? |
| T4 | Interés en posgrado (SI/NO) | Situación laboral | ¿Influye la situación laboral en el interés? |
| T5 | Institución preferida | Segmento de madurez | ¿Dónde estudiarían según su madurez profesional? |
| T6 | Tiempo hasta el primer empleo | Formación complementaria (SI/NO) | ¿La formación extra se asocia a una inserción más rápida? |
| T7 | Satisfacción global (Likert) | Situación laboral | ¿La satisfacción varía según la ocupación? |
| T8 | Concordancia formación–mercado (Likert) | Segmento de madurez | ¿La percepción de pertinencia cambia con la experiencia? |
| T9 | Institución donde cursó | Fuente de financiamiento | ¿Ciertas instituciones se asocian a becas? |
| T10 | Cada aspecto "a mejorar" (dicotómica) | Segmento de madurez | ¿Qué mejoras pide cada segmento? |

**Encuesta de empleadores**

| # | Variable fila | Variable columna | Qué responde |
|---|---|---|---|
| E1 | Tipo de organización (público/privado) | Proyección de contratación | ¿Qué sector contratará más? |
| E2 | Tamaño de organización | Nivel de formación demandado | ¿Las empresas grandes exigen posgrado? |
| E3 | Rubro | Contrató Ing. de Sistemas en los últimos 5 años | ¿Qué rubros absorben titulados? |
| E4 | Tipo de organización | Cada ítem de la matriz Likert | ¿El sector público y el privado valoran distinto la formación? |
| E5 | Tamaño de organización | Cada cargo (dicotómica) | ¿Qué cargos ocupan según el tamaño de la empresa? |

Además de los cruces predefinidos, debe existir un **cruce libre**: el usuario elige dos variables cualesquiera y obtiene la tabla, el χ² y el heatmap.

### 6.4 Matriz de asociaciones
Debe haber una opción para calcular el χ² de **todos los pares** de variables categóricas de la encuesta, o de un subconjunto elegido.

El resultado es una matriz de **p-valores** y otra de **V de Cramér**, mostradas como heatmap, junto con un ranking de los pares significativos. Sirve para la sección "Diagnóstico y síntesis de resultados" del informe.

En la matriz se marcan los pares que no cumplen el supuesto de esperadas ≥ 5.

---

## 7. Visualización (dashboard por encuesta)

**Indicadores principales:**
- n de respuestas y rango de fechas de recolección;
- % de formación complementaria, % de interés en posgrado y % de ocupados;
- media y desviación estándar de años de vida profesional;
- media de satisfacción global;
- en empleadores: n de **empresas únicas**, cuántas **públicas** y cuántas **privadas**. El trabajo exige al menos 3 empresas tecnológicas, con al menos una pública y una privada, así que el sistema debe indicar si se cumple.

**Gráficos:**

| Gráfico | Para qué |
|---|---|
| Barras | Frecuencias de opción única |
| Barras horizontales ordenadas | Selección múltiple (aspectos útiles, aspectos a mejorar, asignaturas) |
| Histograma y boxplot | Numéricas |
| **Línea temporal de respuestas por fecha** | Muestra la recolección como si hubiera ocurrido en la plataforma |
| **Mapa de calor** | Tabla de contingencia de cada cruce y matriz de asociaciones |
| **Diagrama de radar** | Medias de los ítems Likert; permite superponer segmentos, como público vs privado o junior vs consolidado |
| **Pareto de áreas de interés** | Resaltar si dos o tres áreas concentran más del 70 % de las preferencias (HU2) |

**Filtros globales** por cualquier variable categórica. Todos los gráficos e indicadores se recalculan con el filtro.

---

## 8. Simulación (lo que pide el ingeniero)

Los datos cargados son la base para experimentar escenarios. El enunciado habla de "experimentación por simulación de sistemas" y de "analizar diferentes escenarios para apoyar la toma de decisiones".

### 8.1 Escenarios comparativos (what-if)
- El usuario define escenarios por filtros, por ejemplo: "solo juniors", "solo con recursos propios", "solo empresas privadas".
- El sistema muestra los indicadores y cruces del escenario **lado a lado** con el total y con otros escenarios.

### 8.2 Bootstrap (intervalos de confianza)
- Remuestreo con reemplazo de los envíos, por defecto **1 000 iteraciones**.
- Entrega un **IC 95 %** de las proporciones y medias clave: % de interés en posgrado por área, % de financiamiento con recursos propios, media de satisfacción, etc.
- Se justifica porque la muestra es pequeña y **por conveniencia**, así que los intervalos muestran la incertidumbre.

### 8.3 Proyección Monte Carlo de demanda
- **Entradas:**
  - tamaño de una cohorte hipotética N, por ejemplo 200 titulados;
  - número de iteraciones;
  - **semilla** fija, para que los resultados sean reproducibles en el informe.
- En cada iteración se simulan N titulados con las proporciones observadas: interés en posgrado → área → institución preferida → fuente de financiamiento.
- **Salidas:** distribución, media e intervalo del número esperado de interesados por área, de cuántos irían a la FCyT o la EUPG y de cuántos necesitarían beca.
- Pregunta tipo que responde: *"Si la FCyT abre una maestría en IA, ¿cuántos inscritos esperamos y cuántos requerirían beca?"*

### 8.4 Ajuste de parámetros
El usuario puede modificar proporciones observadas para ver su efecto, por ejemplo: "¿qué pasa si las becas suben del 10 % al 30 %?". También puede cambiar los umbrales de los segmentos.

### 8.5 Simulación de recolección
Las respuestas se presentan con su fecha original, en una línea de tiempo y con avance acumulado. Así se ve la evolución como si se hubieran recolectado en la plataforma.

---

## 9. Exportación (insumo para el informe)

- **Datos limpios (CSV / Excel):**
  - una fila por respuesta y una columna por variable;
  - las selecciones múltiples como columnas 0/1;
  - las Likert con su número;
  - las variables derivadas incluidas;
  - **sin datos personales**.
- **Reporte (Excel):**
  - una hoja por pregunta con su tabla de frecuencias;
  - una hoja por cruce, con la tabla, las esperadas, χ², gl, p-valor, V de Cramér, la interpretación y la advertencia de supuestos;
  - una hoja con la matriz de asociaciones;
  - una hoja de escenarios, bootstrap y Monte Carlo, con sus parámetros y la semilla.
- **Imágenes PNG** de cada gráfico.
- **Contactos** (email, teléfono, LinkedIn): export aparte, solo para administrador.
- **Definición de la encuesta reconstruida** (JSON), para documentarla en el informe.

---

## 10. Metodología visible en el sistema

Una pantalla describe el pipeline con los números reales de la encuesta seleccionada:

> Carga (n filas, origen) → Reconstrucción (n preguntas por tipo) → Limpieza (faltantes, inconsistencias) → Variables derivadas → Univariado → Cruces χ² (n significativos) → Simulación → Exportación

Alimenta las secciones del informe "Técnicas de recolección de datos", "Pipeline de procesamiento estadístico" y "Diagnóstico y síntesis de resultados".

Debe aclarar que el muestreo es **por conveniencia** y que los resultados son descriptivos y exploratorios, no generalizables.

---

## 11. Pruebas recomendadas

- **Datos sintéticos de prueba:** genera archivos que imiten el export de MS Forms (titulados, unas 40 filas) y de Google Forms (empleadores, unas 10 filas), basados en el Anexo A y respetando la lógica condicional. Sirven para probar sin los archivos reales.
- **Tests de inferencia:**
  - tipos de pregunta;
  - separación de múltiples con comas internas;
  - agrupación de matrices de Google Forms;
  - detección de reglas condicionales.
- **Tests estadísticos:** χ², esperadas, gl, p-valor, V de Cramér, Fisher y descriptivos, comparados con un ejemplo resuelto a mano.

---

## Anexo A — Encuestas de referencia

Al cargar los archivos reales, la encuesta reconstruida debería parecerse a esto. Si difiere, **mandan las categorías del archivo**.

### A.1 Titulados (MS Forms)

| Sección | Pregunta | Tipo esperado | Opciones / condición |
|---|---|---|---|
| Datos generales | Edad actual | Única ordinal | Rangos (tomar del archivo; el PDF solo muestra "35 años o más") |
|  | Género | Única | Tomar del archivo |
| Formación complementaria | ¿Ha realizado formación complementaria? | Única | SI / NO |
|  | Programa de mayor nivel | Única ordinal | Diplomado, Especialidad, Maestría, Doctorado, Pos doctorado — *si SI* |
|  | ¿Dónde lo cursó? | Única | EUPG; Dirección de Posgrado FCyT; IBNORCA; Univ. Internacionales; Univ. Públicas Nacionales; Univ. Privadas Nacionales; Otro — *si SI* |
|  | Fuente de financiamiento | Única | Recursos propios; Beca total; Beca parcial — *si SI* |
|  | Nombre del programa | Texto corto | *si SI* |
| Interés en posgrado | ¿Interesado en posgrado en áreas de Sistemas? | Única | SI / NO |
|  | Área de interés | Única | IA; Robótica; Ingeniería de Software; Base de Datos; Redes de Datos y Seguridad — *si SI* |
|  | Organización preferida | Única | Las mismas 7 opciones — *si SI* |
|  | ¿Hay posgrados en la FCyT afines a Sistemas? | Likert 4 (acuerdo) | — |
| Empleabilidad | Años de vida profesional | Numérica | ≥ 0 |
|  | Tiempo desempleado | Numérica | ≤ años de vida profesional |
|  | Situación laboral | Única | Trabajando en una organización; NO trabajo; Tiempo completo en emprendimiento propio |
|  | Origen del emprendimiento | Única | Idea propia; Idea conjunta; StartUp; Spin Off; Otro — *si emprendimiento* |
|  | Tipo de entregable | Única | Producto; Servicio; Ambos — *si emprendimiento* |
|  | Financiamiento externo del negocio | Única | Préstamos bancarios; Inversores; No, propio — *si emprendimiento* |
|  | Satisfacción con el negocio | Likert 3 (satisfacción) | *si emprendimiento* |
|  | Importancia de la formación para el negocio | Likert 4 (importancia) | *si emprendimiento* |
|  | ¿Es su primer empleo? | Única | SI / NO |
|  | Tiempo hasta el primer trabajo relacionado | Única ordinal | Ya trabajaba; < 1 mes; 1–4; 4–8; 8–12; > 12 meses |
|  | Nº de empleos desde la titulación | Única ordinal | 1; 2; 3; Más de 3 |
| Valoración de la carrera | Satisfacción global con la formación | Likert 4 (acuerdo) | — |
|  | Concordancia formación–requerimientos | Likert 4 (acuerdo) | — |
|  | Aspectos de bastante utilidad | Múltiple | Práctica en proyectos reales; pasantías; horarios; habilidades analíticas; equipos multidisciplinarios; herramientas de la industria |
|  | Aspectos a mejorar | Múltiple | Integración teoría-práctica; relación con empresas; horarios para trabajadores o virtuales; actualización del plan; extracurriculares; más especialización |
|  | Asignaturas que dieron ventaja competitiva | Múltiple (hasta 5) + "Otra" | Introducción; Base de Datos; IA; Redes de Computadora; Electivas; T.I.S.; Básicas (Física, Calculo, Algebras); Planificación de Proyectos y Gestión; Tecnología Redes Avanzadas |
|  | Asignaturas poco útiles | Múltiple + "Otra" | Básicas; Aplic. Interactivas para TV Digital; Telefonía IP; Web Semánticas; Graficación por Computadora; Robótica; Taller de Prog. en Bajo Nivel; Teoría de Autómatas; Programación Funcional; Teoría de Grafos; Contabilidad Básica. *El enunciado dice 5 y el formulario dice 2: no imponer límite al cargar.* |
| Red de contactos | ¿Le interesa la red de contactos? | Única | SI / NO |
|  | Email, teléfono, LinkedIn | Email / Teléfono / URL | **Datos personales** |

### A.2 Empleadores (Google Forms)

| Pregunta | Tipo esperado | Opciones |
|---|---|---|
| Correo | Email | **Dato personal** |
| Nombre de la empresa o institución | Texto corto | Normalizar para contar empresas únicas |
| Tipo de organización | Única | Público / Privado |
| Tamaño | Única ordinal | Micro (≤ 10); Pequeña (11–30); Mediana (31–99); Grande (≥ 100) |
| Rubro | Única | Desarrollo de software; Servicios TI; Telecomunicaciones; Soporte técnico; Consultoría / transformación digital; Banca; Instituciones públicas; Educación e investigación; Industria; Comercio; Salud; Ciberseguridad; Análisis de datos / BI; Startups; Otros |
| ¿Contrató Ing. de Sistemas en los últimos 5 años? | Única | SI / No |
| Proyección de contratación | Única ordinal | Alta probabilidad; Depende del crecimiento; No prevista; No requiere |
| Nivel de formación demandado | Única ordinal | Licenciatura; Diplomado; Especialidad; Maestría; Doctorado; Post-doctorado |
| Medio de convocatoria | Única (verificar en el archivo si es múltiple) | Medios escritos/audiovisuales; Internet y redes; Postulación competitiva; Oferta personal; Recomendación; Otros |
| Cargos que desempeñan | Múltiple + Otro | Apoyo; Técnico; Analista; Especialista; Supervisor/Coordinador; Jefatura/Gerencial; Consultor |
| Tendencias emergentes | Texto largo | — |
| Herramientas que debería manejar | Texto largo | — |
| Competencias fundamentales | Texto largo | — |
| Formación y perfil profesional | **Matriz Likert** (escala de empleadores, con "No sabe") | 10 ítems. En el PDF aparecen duplicados, pero son 10. |

Los 10 ítems de la matriz:
1. Confianza en la Carrera
2. Título consistente con las competencias
3. Las autoridades consultan a los empleadores
4. Conoce el perfil de egreso
5. Perfil coherente con el desempeño
6. Desempeño destacado e integral
7. Se incorpora la opinión del sector en prácticas y titulación
8. Competencias laborales / soft skills
9. Valores éticos y responsabilidad social
10. Participó en retroalimentación

## Anexo B — Historias de usuario del enunciado

| HU | Cómo se cubre |
|---|---|
| HU1 Antigüedad y empleabilidad | Años de vida profesional → media, mediana, desviación estándar y segmento de madurez; tasa de inserción = % ocupados; sector según el rubro de los empleadores. |
| HU2 Formación continua | Nivel y área → frecuencias y Pareto del 70 %. |
| HU3 Financiamiento | Cruce T1 (financiamiento × nivel) con χ²; financiamiento de los emprendimientos. |
| HU4 Brechas de competencias | Likert de titulados, aspectos útiles y a mejorar, asignaturas; matriz de empleadores (ítem 8 = soft skills) y textos abiertos; medias y desviaciones. |
| HU5 Reportes | Tablas cruzadas, matriz de asociaciones, heatmap, radar, exportación CSV/Excel y dashboard. |