const { initializeApp, cert } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore } = require("firebase-admin/firestore");

const serviceAccount = require("./serviceAccountKey.json");
const colaboradores = require("./colaboradores.json");

// Inicializar Firebase Admin usando la sintaxis moderna
initializeApp({
  credential: cert(serviceAccount)
});

const auth = getAuth();
const db = getFirestore();

async function importarBaseCompleta() {
  for (const emp of colaboradores) {
    try {
      // 1. Crear cuenta en Firebase Authentication
      const userRecord = await auth.createUser({
        email: emp.correo,
        password: emp.password,
        displayName: emp.nombreCompleto
      });

      const uid = userRecord.uid;

      // 2. Registrar en la colección USUARIOS (vinculado por su UID de Auth)
      await db.collection("USUARIOS").doc(uid).set({
        correo: emp.correo,
        nombre: emp.nombreCompleto,
        rol: emp.rol,
        codigoColaborador: emp.codigoColaborador,
        estado: true
      });

      // 3. Registrar en la colección COLABORADORES con todos sus datos de planilla
      await db.collection("COLABORADORES").doc(uid).set({
        codigoColaborador: emp.codigoColaborador,
        dni: emp.dni,
        cargo: emp.cargo,
        nombres: emp.nombres,
        apellidos: emp.apellidos,
        jornalDia: emp.jornalDia,
        jornalNoche: emp.jornalNoche,
        horasExtras: emp.horasExtras,
        bancoCredito: emp.bcp,
        interbank: emp.interbank,
        yapePlin: emp.yapePlin,
        estado: true
      });

      console.log(`[EXITO] Importado: ${emp.nombreCompleto} (${emp.codigoColaborador})`);

    } catch (error) {
      console.error(`[ERROR] Al importar a ${emp.nombreCompleto}:`, error.message);
    }
  }
  console.log("\n¡Proceso de importación masiva finalizado con éxito!");
}

importarBaseCompleta();