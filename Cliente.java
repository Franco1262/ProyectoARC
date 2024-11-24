import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

public class Cliente implements Runnable {
    private String host = "localhost";
    private int port = 6789;
    private int ClientId;
    private long time = 0;
    int numIteraciones;

    // Constructor
    public Cliente(int id, int n) 
    {
        ClientId = id;
        numIteraciones = n;
    }

    @Override
    public void run() 
    {
        long startTime = System.currentTimeMillis();
        DatagramSocket socket = null;
        try 
        {
            socket = new DatagramSocket();
            socket.setSoTimeout(20000); // Tiempo de espera para recibir paquetes
            byte[] bufferEntrada = new byte[512];

            // Registro en el servidor
            String registro = "Registro";
            byte[] registroData = registro.getBytes(StandardCharsets.US_ASCII);
            DatagramPacket registroPacket = new DatagramPacket(
                    registroData, registroData.length, new InetSocketAddress(host, port));
            socket.send(registroPacket);

            DatagramPacket startPacket = new DatagramPacket(bufferEntrada, bufferEntrada.length);
            socket.receive(startPacket);
            String startMessage = new String(startPacket.getData(), 0, startPacket.getLength(), StandardCharsets.US_ASCII);

            while(true)
            {
                if(startMessage.equals("Iniciar Iteracion"))
                {
                    break;
                }
            }
            for (int i = 0; i < numIteraciones; i++) {
                Random random = new Random();
                int x = random.nextInt(100);
                int y = random.nextInt(100);
                int z = random.nextInt(100);
                String coordenadas = "[EnvioCoordenadas] Cliente: " + ClientId + " Coordenadas: " + x + "," + y + "," + z;
                
                // Enviar coordenadas al servidor
                byte[] datosCoordenadas = coordenadas.getBytes(StandardCharsets.US_ASCII);
                DatagramPacket sendPacket = new DatagramPacket(
                        datosCoordenadas, datosCoordenadas.length, new InetSocketAddress(host, port));
                socket.send(sendPacket);

                while (true) {
                    try {
                        Arrays.fill(bufferEntrada, (byte) 0); // Limpia el buffer
                        DatagramPacket neighborPacket = new DatagramPacket(bufferEntrada, bufferEntrada.length);
                        socket.receive(neighborPacket);
                        String neighborCoordinates = new String(neighborPacket.getData(), 0, neighborPacket.getLength(), StandardCharsets.US_ASCII);

                        if (neighborCoordinates.split(" ")[0].equals("[EnvioCoordenadas]")) 
                        {
                            System.out.println(neighborCoordinates + "   " + ClientId);
                            String confirmationMessage = "[ACK] Cliente: " + neighborCoordinates.split(" ")[2];
                            byte[] confirmationData = confirmationMessage.getBytes(StandardCharsets.US_ASCII);
                            DatagramPacket confirmationPacket = new DatagramPacket(
                                    confirmationData, confirmationData.length, new InetSocketAddress(host, port));
                            socket.send(confirmationPacket);
                        }

                        if (neighborCoordinates.split(":")[0].trim().equals("[FINALACK]")) {
                            System.out.println(ClientId + " Recibio todos los acuses");
                            break;
                        }
                    } catch (SocketTimeoutException e) {
                        System.out.println("Timeout en iteración " + i);
                        break; // Salir del bucle si no hay respuesta
                    }
                }

                // Esperar entre iteraciones
                try {
                    Thread.sleep(100); // 100 ms de espera
                } catch (InterruptedException e) {
                    System.out.println("Interrupción en el cliente " + ClientId);
                }
            }

            long endTime = System.currentTimeMillis();
            time += (endTime - startTime) / numIteraciones;

            String TiempoMedio = "[TiempoMedio] " + time;
            byte[] datosTiempo = TiempoMedio.getBytes(StandardCharsets.US_ASCII);
            DatagramPacket sendPacket = new DatagramPacket(
                    datosTiempo, datosTiempo.length, new InetSocketAddress(host, port));
            socket.send(sendPacket);

            DatagramPacket neighborPacket = new DatagramPacket(bufferEntrada, bufferEntrada.length);
            socket.receive(neighborPacket);
            String neighborCoordinates = new String(neighborPacket.getData(), 0, neighborPacket.getLength(), StandardCharsets.US_ASCII);
            while(true)
            {
               if(neighborCoordinates.equals("[SimulacionFinalizada]"))
               {
                    break;
               } 
            }
        } 
        catch (Exception e) 
        {
            System.out.println("Error en el cliente " + ClientId + ": " + e.getMessage());
        }

        finally 
        {
            if (socket != null && !socket.isClosed()) {
                socket.close();  // Asegúrate de cerrar el socket al final
                System.out.println("Cliente " + ClientId + ": Socket cerrado.");
            }
        }
    }

    public static void main(String[] args) 
    {
        int numClientes = 0;
        int numIteraciones = 0;
        DatagramSocket socket = null;
        String host = "localhost";
        int port = 6789;
        try 
        {
            socket = new DatagramSocket();
            byte[] bufferEntrada = new byte[512];

            while (true) {
                String solicitudNumCli = "SolicitarNumClientesIter";
                byte[] solicitudData = solicitudNumCli.getBytes(StandardCharsets.US_ASCII);
                DatagramPacket solicitudPacket = new DatagramPacket(
                        solicitudData, solicitudData.length, new InetSocketAddress(host, port));
                socket.send(solicitudPacket);

                // Recibir el número máximo de clientes desde el servidor
                DatagramPacket respuestaPacket = new DatagramPacket(bufferEntrada, bufferEntrada.length);
                socket.receive(respuestaPacket);
                String respuesta = new String(respuestaPacket.getData(), 0, respuestaPacket.getLength(), StandardCharsets.US_ASCII);
                numClientes = Integer.parseInt(respuesta.split(" ")[0]);
                numIteraciones = Integer.parseInt(respuesta.split(" ")[1]);

                // Salir del bucle si se recibe un número de clientes válido (no cero)
                if (numClientes != 0) {
                    break;
                }

                System.out.println("Esperando número de clientes válido...");
            }

            System.out.println("Número total de clientes recibido: " + numClientes);
        } 

        catch (IOException e) 
        {
            System.out.println("Error en la comunicación con el servidor: " + e.getMessage());
        } 

        finally 
        {
            // Cerrar el socket después de que el bucle haya terminado
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }

        for(int i = 0; i < numClientes ; i++)
        {
            Cliente cliente = new Cliente(i, numIteraciones);
            Thread hiloCliente = new Thread(cliente);
            hiloCliente.start();  // Iniciar el hilo
        }
    }

}


