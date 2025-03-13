// Ignacio Garbayo Fernández

package PRabbitMQ;

import com.rabbitmq.client.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Send {

    private static final String VALUES_QUEUE = "intervalosRR";  // Cola principal para enviar datos
    private static final String REGISTRATION_QUEUE = "registrationQueue";  // Cola para registrar clientes

    private static final Map<String, Channel> clientChannels = new HashMap<>();
    private static final Map<String, String> consumerTags = new HashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");

        try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {

            // Declara las colas necesarias
            channel.queueDeclare(VALUES_QUEUE, false, false, false, null);
            channel.queueDeclare(REGISTRATION_QUEUE, false, false, false, null);

            // Callback para recibir registros de clientes
            DeliverCallback registerClientCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                String[] parts = message.split(",");
                // ID del cliente
                String clientId = parts[0];
                // Tiempo de suscripción del cliente
                int subscriptionTime = Integer.parseInt(parts[1]);

                // Crear una cola exclusiva para el cliente
                Channel clientChannel = connection.createChannel();

                // Guardamos cola exclusiva para el cliente
                String clientQueue = "queue_" + clientId;
                clientChannel.queueDeclare(clientQueue, false, false, false, null);

                // Almacenar el canal y el consumerTag (para gestionar la desconexión)
                clientChannels.put(clientId, clientChannel);
                consumerTags.put(clientId, consumerTag);

                System.out.println("Cliente registrado: " + clientId + " con tiempo de suscripción: " + subscriptionTime + " segundos");

                // Iniciar el envío de mensajes a la cola exclusiva del cliente
                scheduler.schedule(() -> {
                    closeClientChannel(clientId);
                    cancelClientSubscription(clientId);  // Cancelar la suscripción después del tiempo de suscripción
                }, subscriptionTime, TimeUnit.SECONDS);
            };

            // Inicia el consumo de mensajes de REGISTRATION_QUEUE
            channel.basicConsume(REGISTRATION_QUEUE, true, registerClientCallback, consumerTag -> {});

            // Inicia el envío de datos a las colas exclusivas de los clientes
            sendMessages(channel);
        }
    }

    // Función de envío de mensajes
    private static void sendMessages(Channel channel) throws IOException, InterruptedException {
        InputStream inputStream = Send.class.getResourceAsStream("/values");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // Enviar mensaje solo a los clientes con tiempo de suscripción activo
                for (String clientId : clientChannels.keySet()) {
                    String clientQueue = "queue_" + clientId;
                    String message = line + "," + lineNumber;
                    Channel clientChannel = clientChannels.get(clientId);

                    // Publica el mensaje en la cola exclusiva del cliente, para cada cliente activo
                    if (clientChannel != null) {
                        clientChannel.basicPublish("", clientQueue, null, message.getBytes(StandardCharsets.UTF_8));
                    }
                }
                System.out.println("Enviado: " + lineNumber + " " + line);
                Thread.sleep(1000);  // Procesar una línea cada segundo
            }
        }
    }

    // Se cierra el canal de un cliente
    private static void closeClientChannel(String clientId) {
        Channel clientChannel = clientChannels.remove(clientId);
        if (clientChannel != null) {
            try {
                clientChannel.close();
                System.out.println("Canal cerrado para el cliente " + clientId);
            } catch (Exception e) {
                System.err.println("Error al cerrar el canal para el cliente " + clientId + ": " + e.getMessage());
            }
        }
    }

    // Se cancela la suscripción de un cliente
    private static void cancelClientSubscription(String clientId) {
        String consumerTag = consumerTags.get(clientId);  // Obtiene el consumerTag
        if (consumerTag != null) {
            try {
                Channel clientChannel = clientChannels.get(clientId);
                if (clientChannel != null) {
                    clientChannel.basicCancel(consumerTag);  // Cancela la suscripción
                    System.out.println("Suscripción cancelada para el cliente " + clientId);
                }
            } catch (IOException e) {
                System.err.println("Error al cancelar la suscripción para el cliente " + clientId + ": " + e.getMessage());
            }
        } else {
            System.out.println("No se encontró el consumerTag para el cliente " + clientId);
        }
    }
}
