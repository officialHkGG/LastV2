import java.io.InputStream;
import java.sql.*;
import java.util.Properties;
import java.util.Scanner;

public class ShoeShopProgram {

    static final String DB_PROPERTIES_FILE = "db.properties";

    public static void main(String[] args) {
        try (Connection connection = getConnectionFromProperties();
             Scanner scanner = new Scanner(System.in)) {

            if (!isPasswordColumnExists(connection)) {
                addPasswordColumn(connection);
            }

            System.out.println("Logga in:");
            System.out.print("Användarnamn: ");
            String username = scanner.nextLine();
            System.out.print("Lösenord: ");
            String enteredPassword = scanner.nextLine();

            if (isCustomerCredentialsValid(connection, username, enteredPassword)) {
                displayProducts(connection);

                System.out.print("Välj en produkt att lägga till i din beställning: ");
                int selectedProductId = scanner.nextInt();

                addToCart(connection, username, selectedProductId);

                OrderLogger.logOrder(username, selectedProductId);

                System.out.println("Produkten har lagts till i din beställning.");
            } else {
                System.out.println("Fel användarnamn eller lösenord. Logga in misslyckades.");
            }
        } catch (SQLException e) {
            handleSQLException(e);
        }
    }

    private static Connection getConnectionFromProperties() throws SQLException {
        Properties properties = new Properties();
        try (InputStream input = ShoeShopProgram.class.getClassLoader().getResourceAsStream(DB_PROPERTIES_FILE)) {
            if (input == null) {
                System.err.println("Failed to load properties file: " + DB_PROPERTIES_FILE);
                System.exit(1);
            }
            properties.load(input);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        String url = properties.getProperty("db.url");
        String user = properties.getProperty("db.user");
        String password = properties.getProperty("db.password");

        return DriverManager.getConnection(url, user, password);
    }

    private static boolean isPasswordColumnExists(Connection connection) throws SQLException {
        ResultSet columnCheck = connection.getMetaData().getColumns(null, null, "Customer", "Password");
        return columnCheck.next();
    }
    private static void addPasswordColumn(Connection connection) throws SQLException {
        String addPasswordColumn = "ALTER TABLE Customer ADD COLUMN Password VARCHAR(255)";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(addPasswordColumn);
        }
    }

    private static boolean isCustomerCredentialsValid(Connection connection, String username, String enteredPassword) throws SQLException {
        String query = "SELECT Password FROM Customer WHERE Name = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, username);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    String storedPassword = resultSet.getString("Password");
                    return enteredPassword.equals(storedPassword);
                } else {
                    return false;
                }
            }
        }
    }

    private static void displayProducts(Connection connection) throws SQLException {
        String selectProductsQuery = "SELECT ID, Name, Color, Size, Price FROM Product";
        try (Statement statement = connection.createStatement();
             ResultSet productsResultSet = statement.executeQuery(selectProductsQuery)) {

            printResultSet(productsResultSet, resultSet -> {
                try {
                    int productId = resultSet.getInt("ID");
                    String name = resultSet.getString("Name");
                    String color = resultSet.getString("Color");
                    String size = resultSet.getString("Size");
                    double price = resultSet.getDouble("Price");

                    System.out.printf("%d. %s - %s - %s - %.2fKr\n", productId, name, color, size, price);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private static void addToCart(Connection connection, String username, int selectedProductId) throws SQLException {

        addToCartProcedure(connection, getCustomerIdByUsername(connection, username), selectedProductId, 1);
    }

    private static void addToCartProcedure(Connection connection, int customerId, int productId, int quantity) {
        String addToCartProcedureCall = "{CALL AddToCart(?, ?, ?)}";
        try (CallableStatement addToCartProcedure = connection.prepareCall(addToCartProcedureCall)) {
            addToCartProcedure.setInt(1, customerId);
            addToCartProcedure.setInt(2, productId);
            addToCartProcedure.setInt(3, quantity);
            addToCartProcedure.executeUpdate();
        } catch (SQLException e) {
            handleSQLException(e);
            System.err.println("Failed to add product to the cart.");
        }
    }

    private static int getCustomerIdByUsername(Connection connection, String username) throws SQLException {
        String query = "SELECT ID FROM Customer WHERE Name = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, username);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("ID");
                } else {
                    throw new SQLException("Customer not found for username: " + username);
                }
            }
        }
    }

    private static void printResultSet(ResultSet resultSet, ResultSetConsumer consumer) throws SQLException {
        while (resultSet.next()) {
            consumer.accept(resultSet);
        }
    }

    private static void handleSQLException(SQLException e) {
        e.printStackTrace();
        System.err.println("SQL State: " + e.getSQLState());
        System.err.println("Error Code: " + e.getErrorCode());
        System.err.println("Error Message: " + e.getMessage());
    }

    @FunctionalInterface
    private interface ResultSetConsumer {
        void accept(ResultSet resultSet) throws SQLException;
    }
}
