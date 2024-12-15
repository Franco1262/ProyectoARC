import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.*;

public class Servidor {
    private ServerSocket serverSocketTCP;
    private final ExecutorService executorService;
    private int clienteId = 1;
    private int maxClientes, vecinos, numIteraciones, n_grupos;
    private final List<ClienteInfo> clientes = new ArrayList<>();
    private final List<List<ClienteInfo>> Grupos = new ArrayList<>();
    private boolean finalizar = false;

    public Servidor(int port, int maxClientes, int vecinos, int numIteraciones) throws IOException {
        this.maxClientes = maxClientes;
        this.vecinos = vecinos;
        this.numIteraciones = numIteraciones;
        this.serverSocketTCP = new ServerSocket(port);
        this.executorService = Executors.newCachedThreadPool();
    }

    public void iniciar() {
        try {
            while (!finalizar) {
                Socket clienteSocket = serverSocketTCP.accept();
                executorService.submit(() -> manejarClientes(clienteSocket));
            }
        } catch (IOException e) {
            System.out.println("Error al aceptar conexiones: " + e.getMessage());
        } finally {
            cerrarSocket();
        }
    }

    private void cerrarSocket() {
        if (serverSocketTCP != null && !serverSocketTCP.isClosed()) {
            executorService.shutdown();
            try {
                serverSocketTCP.close();
            } catch (IOException e) {
                System.out.println("Error al cerrar el socket del servidor.");
            }
        }
    }

    private void manejarClientes(Socket clienteSocket) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(clienteSocket.getInputStream(), StandardCharsets.US_ASCII));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(clienteSocket.getOutputStream(), StandardCharsets.US_ASCII), true)
        ) {
            System.out.println("Nueva conexión aceptada desde: " + clienteSocket.getInetAddress() + ":" + clienteSocket.getPort());
            String mensaje;
            while ((mensaje = in.readLine()) != null) {
                if (mensaje.startsWith("Registro")) {
                    synchronized (this) {
                        registrarCliente(clienteSocket, in, out, mensaje);
                    }
                } else if (mensaje.startsWith("SolicitarNumClientesIter")) {
                    String respuesta = maxClientes + " " + numIteraciones + " " + vecinos;
                    out.println(respuesta);
                } else if (mensaje.startsWith("[EnvioCoordenadas]")) {
                    manejarCoordenadas(clienteSocket, mensaje);
                } else if (mensaje.startsWith("[ACK]")) {
                    manejarACK(mensaje);
                } else if (mensaje.startsWith("[TiempoMedio]")) {
                    manejarTiempoMedio(clienteSocket, mensaje);
                }
            }
        } catch (IOException e) {
            System.err.println("Error al manejar al cliente: " + e.getMessage());
        }
    }

    private void registrarCliente(Socket clienteSocket, BufferedReader in, PrintWriter out, String mensaje) {
        clientes.add(new ClienteInfo(clienteSocket.getInetAddress(), clienteSocket.getPort(), "", clienteId, in, out));
        System.out.println("Cliente " + clienteId + " registrado.");
        if (clienteId == maxClientes) {
            System.out.println("Se alcanzó el número máximo de clientes. Creando grupos...");
            crearGrupos();
            enviarInicioIteracion();
        }
        clienteId++;
    }

    private void manejarCoordenadas(Socket clienteSocket, String mensaje) {
        int puerto = clienteSocket.getPort();
        for (List<ClienteInfo> grupo : Grupos) {
            for (ClienteInfo cliente : grupo) {
                if (cliente.puerto == puerto) {
                    cliente.coordenadas = mensaje;
                    EnviarCoordenadasVecinos(cliente, grupo);
                    return;
                }
            }
        }
    }

   private void manejarACK(String mensaje) {
    try {
        // Extraer el ID del cliente que envió el ACK
        int ackSenderId = Integer.parseInt(mensaje.split(" ")[1]);

        synchronized (clientes) {
            // Encontrar el cliente que envió el ACK
            ClienteInfo sender = clientes.get(ackSenderId - 1);

            // Determinar a qué grupo pertenece
            for (List<ClienteInfo> grupo : Grupos) {
                if (grupo.contains(sender)) {
                    // Reenviar el ACK a todos los demás vecinos del grupo
                    for (ClienteInfo vecino : grupo) {
                        if (!vecino.equals(sender)) {
                            vecino.out.println(mensaje);
                           // System.out.println("ACK reenviado a cliente " + vecino.idSesion + " desde cliente " + ackSenderId);
                        }
                    }
                    break;
                }
            }
        }
    } catch (Exception e) {
        System.err.println("Error al manejar ACK: " + e.getMessage());
    }
}

private void manejarTiempoMedio(Socket clienteSocket, String mensaje) {
    int port = clienteSocket.getPort();
    for (ClienteInfo cliente : clientes) {
        if (cliente.puerto == port) {
            // Guardar el tiempo promedio recibido del cliente
            cliente.TiempoMedio = Integer.parseInt(mensaje.split(" ")[1]);
            System.out.println("Tiempo promedio recibido del cliente " + cliente.idSesion + ": " + cliente.TiempoMedio + " ms");
            break;
        }
    }

    // Verificar si todos los clientes han enviado sus tiempos medios
    if (IterationsFinished()) {
        CalcularTiempos();
        enviarFinalizacionSimulacion();
        finalizar = true; // Finalizar el servidor
    }
}

private void CalcularTiempos() {
    long totalTime = 0;

    for (int i = 0; i < n_grupos; i++) {
        long groupTime = 0;
        for (ClienteInfo cliente : Grupos.get(i)) {
            groupTime += cliente.TiempoMedio;
        }
        long groupAverage = groupTime / Grupos.get(i).size();
        totalTime += groupTime;

        System.out.println("Tiempo medio del grupo " + (i + 1) + ": " + groupAverage + " ms");
    }

    long finalAverage = totalTime / clientes.size();
    System.out.println("Tiempo total promedio de todos los clientes: " + finalAverage + " ms");
}

    private void crearGrupos() {
        n_grupos = maxClientes / vecinos;
        for (int i = 0; i < n_grupos; i++) {
            List<ClienteInfo> grupo = new ArrayList<>();
            for (int j = 0; j < vecinos; j++) {
                grupo.add(clientes.get((vecinos * i) + j));
            }
            Grupos.add(grupo);
        }
        System.out.println("Grupos creados: " + n_grupos);
    }

    private void EnviarCoordenadasVecinos(ClienteInfo cliente, List<ClienteInfo> grupo) {
        for (ClienteInfo vecino : grupo) {
            if (!vecino.equals(cliente)) {
                vecino.out.println(cliente.coordenadas);
            }
        }
    }

    private void enviarInicioIteracion() {
        for (ClienteInfo cliente : clientes) {
            cliente.out.println("[IniciarIteracion]");
        }
    }

    private void enviarFinalizacionSimulacion() {
        for (ClienteInfo cliente : clientes) {
            cliente.out.println("[SimulacionFinalizada]");
        }
    }

   private Boolean IterationsFinished() {
    for (ClienteInfo cliente : clientes) {
        if (cliente.TiempoMedio == 0) {
            return false;
        }
    }
    return true;
}


    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("Ingrese el número total de clientes: ");
            int numClientes = scanner.nextInt();
            System.out.print("Ingrese el número de vecinos por grupo: ");
            int vecinos = scanner.nextInt();
            System.out.print("Ingrese el número de iteraciones: ");
            int numIteraciones = scanner.nextInt();

            if (numClientes % vecinos != 0) {
                System.out.println("El número de vecinos debe ser divisor de N.");
                return;
            }

            Servidor servidor = new Servidor(6789, numClientes, vecinos, numIteraciones);
            servidor.iniciar();
        } catch (IOException e) {
            System.out.println("Error al iniciar el servidor: " + e.getMessage());
        }
    }
}

class ClienteInfo {
    InetAddress direccion;
    int puerto;
    String coordenadas;
    int idSesion;
    int TiempoMedio = 0;
    BufferedReader in;
    PrintWriter out;

    ClienteInfo(InetAddress direccion, int puerto, String coordenadas, int idSesion, BufferedReader in, PrintWriter out) {
        this.direccion = direccion;
        this.puerto = puerto;
        this.coordenadas = coordenadas;
        this.idSesion = idSesion;
        this.in = in;
        this.out = out;
    }
}