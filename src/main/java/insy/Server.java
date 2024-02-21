package insy;


import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;
import org.json.*;
import java.sql.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.postgresql.Driver;

/**
 * INSY Webshop Server
 */
public class Server {

    /**
     * Port to bind to for HTTP service
     */
    private int port = 8000;

    /**
     * Connect to the database
     * @throws IOException
     */
    Connection setupDB()  {
        Properties dbProps = new Properties();
        try {
            dbProps.load(new FileInputStream("db.properties"));
            Connection conn = DriverManager.getConnection(
                    dbProps.getProperty("url"),
                    dbProps
                    );
            return conn;
        } catch (Exception throwables) {
            throwables.printStackTrace();
        }
        return null;
    }

    /**
     * Startup the Webserver
     * @throws IOException
     */
    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/articles", new ArticlesHandler());
        server.createContext("/clients", new ClientsHandler());
        server.createContext("/placeOrder", new PlaceOrderHandler());
        server.createContext("/orders", new OrdersHandler());
        server.createContext("/stats", new StatsHandler());
        server.createContext("/", new IndexHandler());

        server.start();
    }


    public static void main(String[] args) throws Throwable {
System.out.println(System.getProperty("java.class.path"));
        Server webshop = new Server();
        webshop.start();
        System.out.println("Webshop running at http://127.0.0.1:" + webshop.port);
    }


    /**
     * Handler for listing all articles
     */
    class ArticlesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            Connection conn = setupDB();

            JSONArray res = new JSONArray();
            
            // read all articles and add them to res
            try (
                    Statement st = conn.createStatement();
                    ResultSet set = st.executeQuery("SELECT * FROM articles;")
                ){

                while(set.next()) {
                    JSONObject article = new JSONObject();
                    article.put("id", set.getInt("id"));
                    article.put("description", set.getString("description"));
                    article.put("price", set.getInt("price"));
                    article.put("amount", set.getInt("amount"));

                    res.put(article);
                }
            }
            catch(SQLException ex) {
                System.err.println(ex);
                answerRequest(t, ex.toString());
            }


            answerRequest(t,res.toString(2));
        }

    }

    /**
     * Handler for listing all clients
     */
    class ClientsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            Connection conn = setupDB();

            JSONArray res = new JSONArray();
            
            try (
                    Statement st = conn.createStatement();
                    ResultSet set = st.executeQuery("SELECT * FROM clients;")
                ){

                while(set.next()) {
                    JSONObject client = new JSONObject();
                    client.put("id", set.getInt("id"));
                    client.put("name", set.getString("name"));
                    client.put("address", set.getString("address"));
                    client.put("city", set.getString("city"));
                    client.put("country", set.getString("country"));

                    res.put(client);
                }
            }
            catch(SQLException ex) {
                System.err.println(ex);
                answerRequest(t, ex.toString());
            }

            answerRequest(t,res.toString(2));
        }

    }


    /**
     * Handler for listing all orders
     */
    class OrdersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            Connection conn = setupDB();

            JSONArray res = new JSONArray();
            
            // read all orders and add them to res
            // Join orders with clients, order lines, and articles
            // Get the order id, client name, number of lines, and total prize of each order and add them to res
            final String QUERY = """
SELECT
    orders.id AS order_id, 
    clients.name AS client_name,
    COUNT(orders.id) AS lines,
    SUM(
        order_lines.amount * 
        articles.price
    )
FROM order_lines
JOIN articles ON order_lines.article_id = articles.id
JOIN orders ON order_lines.order_id = orders.id
JOIN clients ON orders.client_id = clients.id
GROUP BY orders.id, clients.id;
                """;
            try (
                    Statement st = conn.createStatement();
                    ResultSet set = st.executeQuery(QUERY);
                ){

                while(set.next()) {
                    JSONObject order = new JSONObject();
                    order.put("order_id", set.getInt("order_id"));
                    order.put("client_name", set.getString("client_name"));
                    order.put("lines", set.getInt("lines"));
                    order.put("sum", set.getInt("sum"));

                    res.put(order);
                }
            }
            catch(SQLException ex) {
                System.err.println(ex);
                answerRequest(t, ex.toString());
            }

            answerRequest(t, res.toString(2));

        }
    }

    
    /**
     * Handler class to place an order
     */
    class PlaceOrderHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {

            Connection conn = setupDB();
            // start transaction
            try {
                conn.setAutoCommit(false);
                // prevent phantom reads
                conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            Map <String,String> params  = queryToMap(t.getRequestURI().getQuery());

            int client_id = Integer.parseInt(params.get("client_id"));

            String response = "";
            int order_id = 1;
            int order_lines_id = 1;
            try (
                    Statement st = conn.createStatement();
                    ResultSet maxIdSet = st.executeQuery("SELECT MAX(id) FROM orders;");
                    Statement st2 = conn.createStatement();
                    ResultSet maxOrderLinesIdSet = st2.executeQuery("SELECT MAX(id) FROM order_lines;");
                ){
                Thread.sleep(1000);

                // if there are already orders
                if(maxIdSet.next()) {
                    // set the order id to the max + 1
                    order_id = maxIdSet.getInt(1) + 1;
                }
                if(maxOrderLinesIdSet.next()) {

                    order_lines_id = maxOrderLinesIdSet.getInt(1) + 1;
                }

                // Create a new order with this id for client client_id
                JSONObject orderObject = new JSONObject();
                // set order id
                orderObject.put("id", order_id);
                // set client id
                orderObject.put("client_id", client_id);

                // insert a new order
                String insertOrderQuery = String.format(
                        "INSERT INTO orders (id, client_id) VALUES (%d, %d);",
                        order_id,
                        client_id
                        );
                st.executeUpdate(insertOrderQuery);
                // commit transaction
                conn.commit();


                for (int i = 1; i <= (params.size()-1) / 2; ++i ){
                    int article_id = Integer.parseInt(params.get("article_id_"+i));
                    int amount = Integer.parseInt(params.get("amount_"+i));


                // Get the available amount for article article_id
                    int available = 0;
                    String query = String.format("SELECT amount FROM articles WHERE id = %d;", article_id);
                    if(query.indexOf('"') != -1) {
                        throw new IllegalArgumentException("no \" allowed");
                    }
                    try(ResultSet availableAmountSet = st.executeQuery(query)) {
                        if(availableAmountSet.next()) {
                            available = availableAmountSet.getInt(1);
                        }
                    } // catch in the outer catch block


                    if (available < amount)
                        throw new IllegalArgumentException(String.format("Not enough items of article #%d available", article_id));

                    // Decrease the available amount for article article_id by amount

                    String updateArticleAmountQuery = String.format(
                            "UPDATE articles SET amount = amount - %d WHERE id = %d;",
                            amount,
                            article_id
                            );
                    st.executeUpdate(updateArticleAmountQuery);

                    // Insert new order line
                    String insertOrderLineQuery = String.format(
                            "INSERT INTO order_lines VALUES(%d, %d, %d, %d);",
                            order_lines_id++,
                            article_id,
                            order_id,
                            amount
                            );
                    st.executeUpdate(insertOrderLineQuery);

                
                }

                // commit the order
                conn.commit();

                response = String.format("{\"order_id\": %d}", order_id);
            } 
            catch(SQLException | IllegalArgumentException | InterruptedException ex) {
                try {
                    conn.rollback();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                response = String.format("{\"error\":\"%s\"}", ex.toString());
                ex.printStackTrace();
            }

            answerRequest(t, response);


        }
    }

    /**
     * Handler for listing static index page
     */
    class IndexHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String response = "<!doctype html>\n" +
                    "<html><head><title>INSY Webshop</title><link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/water.css@2/out/water.css\"></head>" +
                    "<body><h1>INSY Pseudo-Webshop</h1>" +
                    "<h2>Verf&uuml;gbare Endpoints:</h2><dl>"+
                    "<dt>Alle Artikel anzeigen:</dt><dd><a href=\"http://127.0.0.1:"+port+"/articles\">http://127.0.0.1:"+port+"/articles</a></dd>"+
                    "<dt>Alle Bestellungen anzeigen:</dt><dd><a href=\"http://127.0.0.1:"+port+"/orders\">http://127.0.0.1:"+port+"/orders</a></dd>"+
                    "<dt>Alle Kunden anzeigen:</dt><dd><a href=\"http://127.0.0.1:"+port+"/clients\">http://127.0.0.1:"+port+"/clients</a></dd>"+
                    "<dt>Bestellung abschicken:</dt><dd><a href=\"http://127.0.0.1:"+port+"/placeOrder?client_id=1&article_id_1=1&amount_1=1&article_id_2=2&amount_2=2\">http://127.0.0.1:"+port+"/placeOrder?client_id=&lt;client_id>&article_id_1=&l&amount_1=&lt;amount_1>&article_id_2=&lt;article_id_2>&amount_2=&lt;amount_2></a></dd>"+
                    "<dt>stats:</dt><dd><a href=\"http://127.0.0.1:"+port+"/stats\">http://127.0.0.1:"+port+"/stats</a></dd>"+
                    "</dl></body></html>";

            answerRequest(t, response);
        }

    }

    class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            Connection conn = setupDB();
            JSONObject stats = new JSONObject();

            // start transaction
            try {
                conn.setAutoCommit(false);
                // prevent phantom reads
                conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            } catch (SQLException e) {
                e.printStackTrace();
            }

            // get all countries
            List<String> countries = new ArrayList<>();
            try (
                    Statement statement = conn.createStatement();
                    ResultSet countrySet = statement.executeQuery("SELECT DISTINCT country FROM clients;");
                ) {
                    while(countrySet.next()) {
                        countries.add(countrySet.getString("country"));
                    }
                }
            catch(SQLException e) {
                e.printStackTrace();
            }

            for(String country : countries) {
                final String query = "SELECT orders.* FROM orders JOIN clients ON orders.client_id = clients.id" +
                    " WHERE clients.country = '" + country + "'";
                JSONArray ordersForCountry = new JSONArray();
                try (
                        ResultSet result = conn.createStatement()
                            .executeQuery(query);
                    ) {
                        while(result.next()) {
                            JSONObject order = new JSONObject();

                            order.put("id", result.getInt("id"));
                            order.put("created at", result.getTimestamp("created_at"));
                            order.put("client id", result.getInt("client_id"));

                            ordersForCountry.put(order);
                        }
                    }
                catch(SQLException e) {
                    e.printStackTrace();
                }
                stats.put(country, ordersForCountry);
            }
            // commit transaction
            try {
                conn.commit();
            }
            catch(SQLException e) {
                e.printStackTrace();
            }
            answerRequest(t, stats.toString(2));
        }
    }


    /**
     * Helper function to send an answer given as a String back to the browser
     * @param t HttpExchange of the request
     * @param response Answer to send
     * @throws IOException
     */
    private void answerRequest(HttpExchange t, String response) throws IOException {
        byte[] payload = response.getBytes();
        t.sendResponseHeaders(200, payload.length);
        OutputStream os = t.getResponseBody();
        os.write(payload);
        os.close();
    }

    /**
     * Helper method to parse query paramaters
     * @param query
     * @return
     */
    public static Map<String, String> queryToMap(String query){
        Map<String, String> result = new HashMap<String, String>();
        for (String param : query.split("&")) {
            String pair[] = param.split("=");
            if (pair.length>1) {
                result.put(pair[0], pair[1]);
            }else{
                result.put(pair[0], "");
            }
        }
        return result;
    }

  
}
