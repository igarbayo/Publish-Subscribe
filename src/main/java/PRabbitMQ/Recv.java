// Ignacio Garbayo Fernández

package PRabbitMQ;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Recv extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        // Cargamos el formato FXML
        FXMLLoader fxmlLoader = new FXMLLoader(Recv.class.getResource("recv.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);
        // Cargamos los estilos CSS
        scene.getStylesheets().add("/PRabbitMQ/basic.css");
        scene.getStylesheets().add("/PRabbitMQ/button.css");
        scene.getStylesheets().add("/PRabbitMQ/colors.css");
        scene.getStylesheets().add("/PRabbitMQ/text-field.css");
        // Configuramos el stage
        stage.setTitle("Recv");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

