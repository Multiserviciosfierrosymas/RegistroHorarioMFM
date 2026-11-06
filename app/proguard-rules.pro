# Conservar todas las clases de datos y modelos para que Firestore no pierda las propiedades en Release
-keep class com.registrohorario_mfm.** { *; }

# Conservar constructores y campos para la serialización de Firebase
-keepclassmembers class com.registrohorario_mfm.** {
    <init>();
    <fields>;
}

# Evitar que Gson o Firebase oculten o re-nombren los campos serializables
-keepattributes *Annotation*,Signature,EnclosingMethod,InnerClasses