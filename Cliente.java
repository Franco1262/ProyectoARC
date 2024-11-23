import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class Cliente {
    public static void main(String args[]) throws java.io.IOException {
        final String host = "localhost";
        final int port = 6789;

        for (int i = 0; i < 4; i++) {
            final int clientId = i;
            new Thread(() -> {
                try {
                    DatagramSocket socket = new DatagramSocket();
                    socket.setSoTimeout(3000); // Tiempo de espera para recibir paquetes
                    byte[] bufferEntrada = new byte[512];

                    // Registro en el servidor
                    String registro = "Registro";
                    byte[] registroData = registro.getBytes(StandardCharsets.US_ASCII);
                    DatagramPacket registroPacket = new DatagramPacket(
                            registroData, registroData.length, new InetSocketAddress(host, port));
                    socket.send(registroPacket);

                    for (int iteracion = 0; iteracion < 3; iteracion++) {
                        boolean startReceived = false;

                        // Esperar la señal del servidor para empezar
                        while (!startReceived) {
                            DatagramPacket startPacket = new DatagramPacket(bufferEntrada, bufferEntrada.length);
                            try {
                                socket.receive(startPacket);
                                String startMessage = new String(startPacket.getData(), 0, startPacket.getLength(), StandardCharsets.US_ASCII);

                                if (startMessage.equals("Iniciar Iteracion")) {
                                    startReceived = true;
                                    // Generar coordenadas aleatorias
                                    Random random = new Random();
                                    int x = random.nextInt(100);
                                    int y = random.nextInt(100);
                                    int z = random.nextInt(100);
                                    String coordenadas = "Coordenadas: " + x + "," + y + "," + z;

                                    // Enviar coordenadas al servidor
                                    byte[] datosCoordenadas = coordenadas.getBytes(StandardCharsets.US_ASCII);
                                    DatagramPacket sendPacket = new DatagramPacket(
                                            datosCoordenadas, datosCoordenadas.length, new InetSocketAddress(host, port));
                                    socket.send(sendPacket);
                                    long initime = System.currentTimeMillis();
 
                                    // Recibir mensajes de vecinos
                                    while (true) {
                                        DatagramPacket neighborPacket = new DatagramPacket(bufferEntrada, bufferEntrada.length);
                                        socket.receive(neighborPacket);
                                        String neighborCoordinates = new String(neighborPacket.getData(), 0, neighborPacket.getLength(), StandardCharsets.US_ASCII);

                                        if (neighborCoordinates.split(":")[0].trim().equals("Coordenadas")) {
                                            String confirmationMessage = "Recibido: " + neighborCoordinates;
                                            byte[] confirmationData = confirmationMessage.getBytes(StandardCharsets.US_ASCII);
                                            DatagramPacket confirmationPacket = new DatagramPacket(
                                                    confirmationData, confirmationData.length, new InetSocketAddress(host, port));
                                            socket.send(confirmationPacket);
                                        }

                                        if (neighborCoordinates.equals("Recibido Final")) {
                                            long fintime = System.currentTimeMillis();
                                            int time = (int) (fintime - initime);

                                            // Enviar tiempo medio al servidor
                                            String tiempomsg = "Tiempo Medio: " + time;
                                            byte[] tiempoData = tiempomsg.getBytes(StandardCharsets.US_ASCII);
                                            DatagramPacket tiempoPacket = new DatagramPacket(
                                                    tiempoData, tiempoData.length, new InetSocketAddress(host, port));
                                            socket.send(tiempoPacket);
                                            break;
                                        }
                                    }
                                }
                            } catch (java.net.SocketTimeoutException e) {
                                // Ignorar timeout y continuar esperando
                                System.out.println("Cliente " + clientId + ": Timeout esperando inicio de la iteración.");
                            }
                        }
                    }

                    // Esperar mensaje de finalización de la simulación
                    boolean finalReceived = false; // Flag para indicar si el mensaje final fue recibido
                    while (!finalReceived) {
                        DatagramPacket finalPacket = new DatagramPacket(bufferEntrada, bufferEntrada.length);
                        try {
                            socket.receive(finalPacket);
                            String finalMessage = new String(finalPacket.getData(), 0, finalPacket.getLength(), StandardCharsets.US_ASCII);
                            if (finalMessage.equals("Simulacion Finalizada")) {
                                System.out.println("Cliente " + clientId + ": Simulación finalizada. Cerrando socket.");
                                finalReceived = true; // Marcar como recibido
                            }
                        } catch (java.net.SocketTimeoutException e) {
                            System.out.println("Cliente " + clientId + ": Timeout esperando mensaje final.");
                        }
                    }

                    socket.close();
                    System.out.println("Cliente " + clientId + ": Socket cerrado.");
                } catch (Exception e) {
                    System.out.println("Error en el cliente " + clientId + ": " + e.getMessage());
                }
            }).start();
        }
    }
}
