// Ignacio Garbayo Fernández

package PRabbitMQ;

import com.rabbitmq.client.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Controller {

    private static boolean comenzado = false;
    private static final String VALUES_QUEUE = "intervalosRR";
    private static final String REGISTRATION_QUEUE = "registrationQueue";

    @FXML
    private TextField tiempoRenovacion;
    @FXML
    private Button boton;
    @FXML
    private TextField recibido;
    @FXML
    private TextField ultimoMinuto;
    @FXML
    private LineChart<Number, Number> dataChart;

    private NumberAxis xAxis;
    private NumberAxis yAxis;
    private XYChart.Series<Number, Number> dataSeries;
    private ScheduledExecutorService scheduler;

    private Connection connection;
    private Channel channel;
    private String consumerTag;
    private String clientId;
    private String clientQueue;  // Cola exclusiva para el cliente
    private double lastValue = 0;
    private int lastLineNumber = 0;
    private boolean dataUpdated = false;

    @FXML
    public void initialize() {
        xAxis = (NumberAxis) dataChart.getXAxis();
        yAxis = (NumberAxis) dataChart.getYAxis();
        xAxis.setAutoRanging(false);
        xAxis.setLowerBound(0);
        xAxis.setUpperBound(60);
        yAxis.setAutoRanging(true);

        dataSeries = new XYChart.Series<>();
        dataSeries.setName("Valores Recibidos");
        dataChart.setCreateSymbols(false);
        dataChart.getData().add(dataSeries);

        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::addTimeTick, 1, 1, TimeUnit.SECONDS);
    }

    private void addTimeTick() {
        Platform.runLater(() -> {
            if (!dataUpdated && comenzado) {
                lastLineNumber++;
                dataSeries.getData().add(new XYChart.Data<>(lastLineNumber, 0));
                if (dataSeries.getData().size() > 60) {
                    dataSeries.getData().remove(0);
                }
                updateXAxisLimits();
            }
            dataUpdated = false;
        });
    }

    @FXML
    private void accionBotonRenovar() throws IOException, TimeoutException {
        if (connection != null && connection.isOpen()) {
            disconnect();
        }

        int tiempo = Integer.parseInt(tiempoRenovacion.getText());
        // ID único para cada cliente
        clientId = "Cliente_" + System.currentTimeMillis();
        clientQueue = "queue_" + clientId;  // Cola exclusiva para este cliente
        // Construcción del mensaje
        String registrationMessage = clientId + "," + tiempo;

        // Establecimiento de conexión
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        connection = factory.newConnection();
        channel = connection.createChannel();

        // Declaramos la cola
        channel.queueDeclare(REGISTRATION_QUEUE, false, false, false, null);
        channel.basicPublish("", REGISTRATION_QUEUE, null, registrationMessage.getBytes(StandardCharsets.UTF_8));
        System.out.println("Cliente registrado con tiempo de suscripción: " + tiempo + " segundos");

        startListening();  // Comienza a escuchar valores en la cola exclusiva
    }

    private void startListening() {
        // Callback para cada mensaje recibido
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            Platform.runLater(() -> {
                String[] parts = message.split(",");
                if (parts.length == 2) {
                    // Valor recibido
                    lastValue = Double.parseDouble(parts[0]);
                    // Línea recibida
                    lastLineNumber = Integer.parseInt(parts[1]);

                    // Actualizamos la ventana gráfica
                    recibido.setText(String.valueOf(lastValue));
                    addDataToChart(lastValue, lastLineNumber);
                    ultimoMinuto.setText(String.valueOf((int)((lastLineNumber / 60) + 1)));
                }
            });
        };

        try {
            // El cliente se suscribe a su cola exclusiva
            channel.queueDeclare(clientQueue, false, false, false, null);
            consumerTag = channel.basicConsume(clientQueue, true, deliverCallback, consumerTag -> {});

            // Verificar el estado del canal; si no, checkChannelStatus
            scheduler.scheduleAtFixedRate(this::checkChannelStatus, 1, 1, TimeUnit.SECONDS);
        } catch (IOException e) {
            System.err.println("Error al iniciar el consumo: " + e.getMessage());
        }
    }

    // Verifica si el cliente está conectado
    private void checkChannelStatus() {
        if (channel != null && !channel.isOpen()) {
            Platform.runLater(this::disconnect);
        }
    }

    // Se añade un valor al gráfico
    private void addDataToChart(double value, int lineNumber) {
        Platform.runLater(() -> {
            // SOlo añade puntos si se ha comenzado a recibir valores
            if (!comenzado) {
                comenzado = true;
            }
            // Añadimos punto
            dataSeries.getData().add(new XYChart.Data<>(lineNumber, value));
            if (dataSeries.getData().size() > 60) {
                dataSeries.getData().remove(0);
            }
            // AJustamos ejes
            updateXAxisLimits();
            dataUpdated = true;
        });
    }

    // Desplazamos los ejes para reflejar solo el último minuto
    private void updateXAxisLimits() {
        int dataSize = dataSeries.getData().size();
        if (dataSize > 0) {
            int lowerBound = Math.max(0, lastLineNumber - 60);
            int upperBound = lastLineNumber;
            xAxis.setLowerBound(lowerBound);
            xAxis.setUpperBound(upperBound);
        }
    }

    // Desconectamos (se llama en el mecanismo de renovar)
    private void disconnect() {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();  // Cierra el canal exclusivo del cliente
            }
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (IOException | TimeoutException e) {
            System.err.println("Error al desconectar: " + e.getMessage());
        } finally {
            consumerTag = null;
            connection = null;
            channel = null;
        }
    }
}
