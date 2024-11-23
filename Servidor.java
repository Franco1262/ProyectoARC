import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.*;

public class Servidor implements Runnable {
    private DatagramSocket socketUDP;
    private static final List<ClienteInfo> clientes = new ArrayList<>();
    private static int num_cli, num_ite, vecinos;
    private byte[] bufer = new byte[1000];
    private final Lock lock = new ReentrantLock();

    public static void main(String args[]) throws IOException {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Ingrese el número total de clientes (N): ");
        num_cli = scanner.nextInt();


        System.out.print("Ingrese el número de vecinos por grupo (V): ");
        vecinos = scanner.nextInt();

        System.out.print("Ingrese el número de iteraciones de la simulación (S): ");
        num_ite = scanner.nextInt();


        if (num_cli% vecinos != 0) {
            System.out.println("El número de vecinos (V) debe ser un divisor de N. Por favor, reinicie el servidor.");
            return; 
        }

        System.out.print("Inicializando servidor... ");
        DatagramSocket socketUDP = new DatagramSocket(6789);
        System.out.println("Esperando paquetes...");

        Servidor servidor = new Servidor(socketUDP);
        new Thread(servidor).start();
    }

    public Servidor(DatagramSocket socketUDP) {
        this.socketUDP = socketUDP;
    }

    @Override
    public void run() {
       int cli_act = 0;
       int id = 1;
       int tiempo_medio = 0;
       int tiempo_medio_final = 0;
       int time_act = 0;
       int ite_Act = 0;
        try {
            while (ite_Act < num_ite) 
            {
                DatagramPacket peticion = new DatagramPacket(bufer, bufer.length);
                socketUDP.receive(peticion);
                String mensaje = new String(peticion.getData(), 0, peticion.getLength());
                String accion = mensaje.split(":")[0].trim();

                if (accion.equals("Registro")) {
                    clientes.add(new ClienteInfo(peticion.getAddress(), peticion.getPort(), "", id));
                    cli_act++;
                    id++;
                    if (cli_act == num_cli) 
                    {
                        System.out.println("*************** Iniciando Iteración " + (ite_Act + 1) + "***************");
                        enviarInicioIteracion();
                    }
                } else if (accion.equals("Coordenadas")) 
                {
                    int puerto = peticion.getPort();
                    for (ClienteInfo cliente : clientes) {
                        if (cliente.puerto == puerto) {
                            cliente.coordenadas = mensaje;
                        }
                    }

                    if (todosClientesEnviaronCoordenadas()) {
                        enviarCoordenadasAClientes();
                    }
                }
 
                else if (accion.equals("Recibido"))
                {
                    String receivedCoords = mensaje.split(":")[2].trim();
                    int puerto = getPuerto(mensaje.split(":")[1].trim() + ": " +mensaje.split(":")[2].trim());
                    for(ClienteInfo cliente : clientes)
                    {
                        if(cliente.puerto == puerto)
                        {
                            cliente.cont_reci++;
                        }
                    }
                    for(ClienteInfo cliente : clientes)
                    {
                        if(cliente.cont_reci == 3)
                        {
                            String recibidoFinal = "Recibido Final";
                            byte[] recibidoFinalData = recibidoFinal.getBytes(StandardCharsets.US_ASCII);
                            DatagramPacket FinalPacket = new DatagramPacket(recibidoFinalData, recibidoFinalData.length,cliente.direccion,cliente.puerto);
                            socketUDP.send(FinalPacket);
                            cliente.cont_reci = 0;

                        }
                    }

                }
                else if(accion.equals("Tiempo Medio"))
                {
                    lock.lock();
                    try 
                    {    
                       int time = Integer.parseInt (mensaje.split(":")[1].trim());
                       System.out.println("El tiempo de " + getidSesion(peticion.getPort()) + " es de: " + time + " ms");
                       tiempo_medio += time;
                       time_act++;
                       if(time_act == 4)
                       {
                        tiempo_medio = tiempo_medio / 4;
                        tiempo_medio_final += tiempo_medio;
                        System.out.println("El tiempo medio es de: " + tiempo_medio + " ms");
                        time_act = 0;
                        tiempo_medio = 0;
                        cli_act = 0;
                        ite_Act++;
                        System.out.println("***************Iteracion: " + (ite_Act) + " finalizada***************");

                        if (ite_Act < num_ite) {
                            System.out.println("*************** Iniciando Iteración " + (ite_Act + 1) + "***************");
                            enviarInicioIteracion();
                        }
                        }

                    } 
                    finally {
                        lock.unlock();
                    }
                }
            }
            if (ite_Act == num_ite) 
            {
                System.out.println("El tiempo medio de todas las iteraciones es de:" + (tiempo_medio_final / num_ite) + " ms");
                tiempo_medio_final = 0;
                enviarFinalizacionSimulacion();
                System.out.println("*************** Simulación Finalizada ***************");
            }
        }catch (IOException ex) {
            Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            desconectar();
        }
    }

    private void enviarCoordenadasAClientes() {
        
        for (ClienteInfo cliente : clientes) {
            
            StringBuilder mensaje = new StringBuilder(cliente.coordenadas );
            
            for (ClienteInfo otroCliente : clientes) {
                if (!otroCliente.equals(cliente)) { 
                    byte[] datos = mensaje.toString().getBytes();
                    DatagramPacket respuesta = new DatagramPacket(datos, datos.length, otroCliente.direccion, otroCliente.puerto);
                    try 
                    {
                        socketUDP.send(respuesta);
                    } catch (IOException e) {
                        System.out.println("Error al enviar coordenadas a " + getidSesion(otroCliente.puerto));
                    }
                }
            }
        }
    }
    private void enviarInicioIteracion() 
    {
        String mensaje = "Iniciar Iteracion";
        byte[] datos = mensaje.getBytes(StandardCharsets.US_ASCII);

        for (ClienteInfo cliente : clientes) 
        {
            DatagramPacket packet = new DatagramPacket(datos, datos.length, cliente.direccion, cliente.puerto);
            try 
            {
                socketUDP.send(packet);
            } catch (IOException e) {
                System.out.println("Error al enviar inicio de iteración a cliente " + cliente.idSesion);
            }
        }
    }
    private void enviarFinalizacionSimulacion() 
    {
    String mensaje = "Simulacion Finalizada";
    byte[] datos = mensaje.getBytes(StandardCharsets.US_ASCII);

    for (ClienteInfo cliente : clientes) {
        DatagramPacket packet = new DatagramPacket(datos, datos.length, cliente.direccion, cliente.puerto);
        try {
            socketUDP.send(packet);
        } catch (IOException e) {
            System.out.println("Error al enviar mensaje de finalización al cliente " + cliente.idSesion);
        }
    }
}
    private boolean todosClientesEnviaronCoordenadas() {
    for (ClienteInfo cliente : clientes) {
        if (cliente.coordenadas.isEmpty()) {
            return false;
        }
    }
    return true;
}
    public int getPuerto(String coord1)
    {
        int puerto1 = 0;

        for(ClienteInfo cliente : clientes)
        {
            if(cliente.coordenadas.equals(coord1))
            {
                puerto1 = cliente.puerto;
            }
        }
        return puerto1;  
    }
    public String getCoord(int puerto1)
    {
        String coord = "";
      for(ClienteInfo cliente : clientes)
      {
        if(cliente.puerto == puerto1)
        {
            coord = cliente.coordenadas;
        }
      }

      return coord;  
    }
    public int getidSesion(int puerto1)
    {
        int id1 = 0;
      for(ClienteInfo cliente : clientes)
      {
        if(cliente.puerto == puerto1)
        {
            id1 = cliente.idSesion;
        }
      }

      return id1;  
    }
    private void desconectar() {
        if (socketUDP != null && !socketUDP.isClosed()) {
            socketUDP.close();
            System.out.println("Socket cerrado.");
        }
    }
}

class ClienteInfo {
    java.net.InetAddress direccion;
    int puerto;
    String coordenadas;
    int cont_reci;
    int idSesion;

    ClienteInfo(java.net.InetAddress direccion, int puerto, String coordenadas, int idSesion) {
        this.direccion = direccion;
        this.puerto = puerto;
        this.coordenadas = coordenadas;
        this.cont_reci = 0;
        this.idSesion = idSesion;
    }

}
