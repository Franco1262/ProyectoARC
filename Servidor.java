import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.*;

public class Servidor implements Runnable 
{
    private DatagramSocket socketUDP;
    private static final List<ClienteInfo> clientes = new ArrayList<>();
    private List<List<ClienteInfo>> Grupos = new ArrayList<List<ClienteInfo>>();
    private static int num_cli, num_ite, vecinos;
    private byte[] bufer = new byte[1000];
    private final Lock lock = new ReentrantLock();
    int n_grupos;

    public static void main(String args[]) throws IOException 
    {
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

    public Servidor(DatagramSocket socketUDP) 
    {
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
            while (true) 
            {
                // Receive client request
                DatagramPacket peticion = new DatagramPacket(bufer, bufer.length);
                socketUDP.receive(peticion);
                String mensaje = new String(peticion.getData(), 0, peticion.getLength());
                String accion = mensaje.split(" ")[0];  

                // Handle different actions based on received message
                if (accion.equals("Registro")) {
                    // Register a new client
                    clientes.add(new ClienteInfo(peticion.getAddress(), peticion.getPort(), "", id));
                    cli_act++;
                    id++;
                   
                    if (cli_act == num_cli) 
                    {
                        n_grupos = num_cli / vecinos;
                        for(int i = 0; i < n_grupos ; i++)
                        {
                            ArrayList<ClienteInfo> grupo = new ArrayList<ClienteInfo>();
                            for(int j = 0; j < vecinos; j++)
                            {
                                grupo.add(clientes.get((vecinos * i) + j));
                            }
                            Grupos.add(grupo);
                        }
                        enviarInicioIteracion();
                    }
                } 

                else if (accion.equals("SolicitarNumClientesIter")) {
                    // Send number of clients back
                    String respuesta = String.valueOf(num_cli + " " + num_ite);
                    byte[] respuestaData = respuesta.getBytes(StandardCharsets.US_ASCII);
                    DatagramPacket respuestaPacket = new DatagramPacket(
                        respuestaData, respuestaData.length, peticion.getAddress(), peticion.getPort());
                    socketUDP.send(respuestaPacket);
                    System.out.println("Enviado número de clientes (" + num_cli + ") a " + peticion.getAddress() + ":" + peticion.getPort());
                }

                else if (accion.equals("[EnvioCoordenadas]")) 
                {
                    int puerto = peticion.getPort();
                    for (int i = 0; i < Grupos.size(); i++) {
                        List<ClienteInfo> grupo = Grupos.get(i);
                
                        // Verificar si el cliente está en este grupo
                        for (ClienteInfo cliente : grupo) 
                        {
                            if (cliente.puerto == puerto) 
                            {
                                cliente.coordenadas = mensaje;
                                EnviarCoordenadasVecinos(cliente, i);
                                break; // Si se encuentra el cliente, no es necesario seguir buscando en este grupo
                            }
                        }
                    }               
                }

                else if (accion.equals("[ACK]")) 
                {
                    int ClientId = Integer.parseInt(mensaje.split(" ")[2]);  
                    ClienteInfo cliente = clientes.get(ClientId);
                    cliente.cont_reci++;
                    // Once all clients have confirmed receipt, send final acknowledgment
                    if(cliente.cont_reci == vecinos-1)
                    {
                        String recibidoFinal = "[FINALACK]";
                        byte[] recibidoFinalData = recibidoFinal.getBytes(StandardCharsets.US_ASCII);
                        DatagramPacket FinalPacket = new DatagramPacket(
                            recibidoFinalData, recibidoFinalData.length, cliente.direccion, cliente.puerto);
                        socketUDP.send(FinalPacket);
                        cliente.cont_reci = 0;
                    }                                   
                }

                else if (accion.equals("[TiempoMedio]")) 
                {
                    int port = peticion.getPort();
                    for(ClienteInfo cliente : clientes)
                    {
                        if(cliente.puerto == port)
                        {
                            cliente.TiempoMedio = Integer.parseInt(mensaje.split(" ")[1]);   
                        }
                    }

                    if(IterationsFinished())
                    {
                        CalcularTiempos();
                        enviarFinalizacionSimulacion();
                        break;                     
                    }
                }
            }
    
        } catch (IOException ex) {
            Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            desconectar();
        }
    }

    private void EnviarCoordenadasVecinos(ClienteInfo cliente, int Grupo)
    {
        StringBuilder mensaje = new StringBuilder(cliente.coordenadas);
        for(ClienteInfo client : Grupos.get(Grupo))
        {
            if(!client.equals(cliente))
            {
                byte[] datos = mensaje.toString().getBytes();
                DatagramPacket respuesta = new DatagramPacket(datos, datos.length, client.direccion, client.puerto);
                try 
                {
                    socketUDP.send(respuesta);
                } catch (IOException e) {
                    System.out.println("Error al enviar coordenadas a " + getidSesion(client.puerto));
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

    private void CalcularTiempos()
    {
        long total_time = 0;
        for(int i = 0; i < n_grupos; i++)
        {
            long time = 0;
            for(ClienteInfo cliente : Grupos.get(i))
            {
                time += cliente.TiempoMedio;
            }
            total_time += time;
            System.out.println("Tiempo de iteracion para el grupo " + (i) + ": " + time);
        } 
        System.out.println("Tiempo total: " + total_time);      
    }

    private Boolean IterationsFinished()
    {
        for(ClienteInfo cliente : clientes)
        {
            if(cliente.TiempoMedio == 0)
                return false;
        }

        return true;
    }

    private void enviarFinalizacionSimulacion() 
    {
    String mensaje = "[SimulacionFinalizada]";
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
    int TiempoMedio = 0;

    ClienteInfo(java.net.InetAddress direccion, int puerto, String coordenadas, int idSesion) {
        this.direccion = direccion;
        this.puerto = puerto;
        this.coordenadas = coordenadas;
        this.cont_reci = 0;
        this.idSesion = idSesion;
    }

}
