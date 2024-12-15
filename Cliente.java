import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class Cliente {
    private String host = "localhost";
    private int port = 6789;
    private int client_id;
    private final Object ackLock = new Object();
    private BufferedReader in;
    private PrintWriter out;
    private Socket socket;
    private boolean iniciar = false;
    private int ack_count = 0;
    private int vecinos;
    private int numIteraciones;
    private static final int MAX_RETRIES = 10; 
    private static final int RETRY_DELAY = 2000;

    public Cliente(int id, int vecinos, int numIteraciones) {
        this.client_id = id;
        this.vecinos = vecinos;
        this.numIteraciones = numIteraciones;
    }

    public void run() {
        try {
            connectToServer();
            waitForStartSignal();
            performIterations();
            closeConnection();
        } catch (IOException e) {
            System.out.println("Error en el cliente " + client_id + ": " + e.getMessage());
        }
    }

    private void connectToServer() throws IOException {
        int attempts = 0;
        boolean connected = false;
        
        while (attempts < MAX_RETRIES && !connected) 
        {
            try 
            {
                socket = new Socket(host, port);  // Crear un socket independiente para cada cliente
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                out.println("Registro Cliente: " + client_id);
                System.out.println("Cliente " + client_id + " registrado en el servidor.");
                connected = true;
            } 
            
            catch (Exception e) 
            {
                attempts++;
                System.out.println("Error al conectar el cliente " + client_id + ", intento: " + attempts);
        
                // Manejo de Thread.sleep con try-catch
                try 
                {
                    Thread.sleep(RETRY_DELAY);
                } 
                catch (InterruptedException ie) 
                {
                    System.out.println("El hilo fue interrumpido durante el retraso: " + ie.getMessage());
                    Thread.currentThread().interrupt(); // Restablece el estado de interrupción del hilo
                }
            }
        }
    }

    private void waitForStartSignal() throws IOException {
        String serverMessage;
        while (!iniciar) {
            serverMessage = in.readLine();
            if (serverMessage != null && serverMessage.equals("[IniciarIteracion]")) {
                iniciar = true;
            }
        }
    }

 private void performIterations() throws IOException {
    long totalIterationTime = 0; // Almacenar el tiempo total de todas las iteraciones

    for (int i = 0; i < numIteraciones; i++) {
        long iterationStartTime = System.currentTimeMillis();

        sendCoordinates();
        waitForAcks();

        long iterationEndTime = System.currentTimeMillis();
        long iterationTime = iterationEndTime - iterationStartTime;

        totalIterationTime += iterationTime; // Acumular el tiempo de la iteración

        System.out.println("Cliente " + client_id + " - Tiempo de iteración " + (i + 1) + ": " + iterationTime + " ms");
    }

    // Calcular el promedio final
    long averageTime = totalIterationTime / numIteraciones;

    // Enviar el promedio al servidor
    reportAverageTime(averageTime);
    waitForSimulationEnd();
}


    private void sendCoordinates() {
        Random random = new Random();
        int x = random.nextInt(100);
        int y = random.nextInt(100);
        int z = random.nextInt(100);
        String coordenadas = "[EnvioCoordenadas] Cliente: " + client_id + " Coordenadas: " + x + "," + y + "," + z;
        out.println(coordenadas);
    }

    private void waitForAcks() throws IOException {
        String serverMessage;
        while (true) {
            serverMessage = in.readLine();
            if (serverMessage != null && serverMessage.startsWith("[EnvioCoordenadas]")) {
                // Enviar ACK al servidor por las coordenadas recibidas
                String ack = "[ACK] " + client_id;
                out.println(ack);
            }
            if (serverMessage != null && serverMessage.startsWith("[ACK]")) {
                synchronized (ackLock) {
                    ack_count++;
                    if (ack_count == vecinos - 1) {
                        ack_count = 0; // Reiniciar contador para la siguiente iteración
                        break;
                    }
                }
            }
        }
    }

    private void reportAverageTime(long averageTime) {
        out.println("[TiempoMedio] " + averageTime);
        System.out.println("Cliente " + client_id + " reportó tiempo medio: " + averageTime + " ms");
    }

    private void waitForSimulationEnd() throws IOException {
        String serverMessage;
        while (true) {
            serverMessage = in.readLine();
            if (serverMessage != null && serverMessage.equals("[SimulacionFinalizada]")) {
                System.out.println("Cliente " + client_id + " ha recibido el fin de la simulación.");
                iniciar = false;
                break;
            }
        }
    }

    private void closeConnection() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            System.out.println("Cliente " + client_id + " ha cerrado la conexión.");
        }
    }

    public static void main(String[] args) {
        try (Socket configSocket = new Socket("localhost", 6789);
             BufferedReader configIn = new BufferedReader(new InputStreamReader(configSocket.getInputStream(), StandardCharsets.US_ASCII));
             PrintWriter configOut = new PrintWriter(new OutputStreamWriter(configSocket.getOutputStream(), StandardCharsets.US_ASCII), true)) {

            configOut.println("SolicitarNumClientesIter");
            String respuesta = configIn.readLine();

            int numClientes = Integer.parseInt(respuesta.split(" ")[0]);
            int numIteraciones = Integer.parseInt(respuesta.split(" ")[1]);
            int vecinos = Integer.parseInt(respuesta.split(" ")[2]);

            for (int i = 0; i < numClientes; i++) {
                final int client_id = i + 1;
                new Thread(() -> new Cliente(client_id, vecinos, numIteraciones).run()).start();
            }

        } catch (IOException e) {
            System.out.println("Error al obtener configuración: " + e.getMessage());
        }
    }
}
