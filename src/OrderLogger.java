import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class OrderLogger {

    private static final String LOG_FILE_PATH = "order_log.txt";

    public static void logOrder(String username, int productId) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE_PATH, true))) {
            String logMessage = String.format("Customer %s made a purchase. Product ID: %d%n", username, productId);
            writer.write(logMessage);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to log the order.");
        }
    }
}
