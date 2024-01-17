# INSY WS02 JDBC

## Theorie

Bisher lieferten Abfragen (SELECT) immer die ganze (ausgewählte) Tabelle zurück.
Dabei kann es zu großen Datenmengen kommen.

Als Lösung wird stattdessen ein Zeiger auf den ausgewählten Datensatz geliefert.
Der zeigt anfangs vor das erste Ergebnis (also ins Leere).
Man kann ihn mit Kommandos weiterbewegen und Daten vom Zeiger holen.

Client und Datenbank kommunizieren mit einem proprietären Protokoll des DBMS.
In Java besteht die Schnittstelle JDBC aus der `java.sql` Library und
dem Connector der Datenbank (für das Protokoll).

Im Code (JDBC) muss man zuerst eine `Connection conn = DriverManager.getConnection(...)` aufbauen,
dann mit `Statement s = conn.createStatement()` ein Statement erschaffen,
dann `ResultSet rs = s.executeQuery(...)` ein Query ausführen (hier kommt in die Klammer ein SQL Befehl).

Jetzt hat man ein ResultSet (Zeiger auf Daten), den man mit  
- `next(): boolean` nach weiteren Daten prüfen kann
- `getXXX(id: int): XXX` nach Daten am Zeiger abfragen kann. XXX ist hier ein Datentyp, z.B. `String`.

Null Werte sind bei `getObject()` null und bei anderen Datentypen der Default Wert.
Man kann mit `wasNull()` das zuletzt geholte überprüfen.

## Praxis

Ich verwende wieder die selbe postgres Datenbank aus der ersten Übung.

### 1. Postgres einrichten

Zuerst richte ich mir die Datenbank ein. Dafür lade ich von der Aufgabenstellung die Datei *webshop.sql* herunter.

Dann kopiere ich es in den Docker Container:  
`docker cp Downloads/webshop.sql postgres:/`

Dann importiere ich es in die Datenbank:  
```sql
CREATE DATABASE webshop;
\c webshop
\i webshop.sql
```

### 2. Java Projekt einrichten

Zuerst wird *Server.java* von der Aufgabenstellung heruntergeladen.

Dann erschaffe ich mir ein Maven Projekt mit `mvn archetype:generate`

Gleichzeitig erschaffe ich auch ein Git Repo.

Dann kopiere ich die (von der Aufgabenstellungh heruntergeladene) Datei *db.properties* ins 
Root-Verzeichnis vom Projekt.

Dadrin ändere ich:  
- die URL zur IP Adresse vom Container (`docker inspect postgres`): **172.17.0.2/webshop**
- das Passwort zu **Pass2023!** (ist noch von der ersten Übung)

In der *pom.xml* Datei muss ich noch Dependencies für JSON und Postgres hinzufügen:  
```xml
<!-- https://mvnrepository.com/artifact/org.json/json -->
<dependency>
    <groupId>org.json</groupId>
    <artifactId>json</artifactId>
    <version>20231013</version>
</dependency>

<!-- https://mvnrepository.com/artifact/org.postgresql/postgresql -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.1</version>
</dependency>
```

Dann noch fürs Builden das Plugin:  
```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-assembly-plugin</artifactId>
  <configuration>
    <archive>
      <manifest>
        <mainClass>insy.Server</mainClass>
      </manifest>
    </archive>
  </configuration>
</plugin>
```

Ich hab noch eine Makefile erstellt, um leicht das Programm auszuführen:  
```Makefile
.PHONY: run

run: 
	mvn assembly:assembly -DdescriptorId=jar-with-dependencies
	java -jar target/jdbc-1.0-SNAPSHOT-jar-with-dependencies.jar 
```

### 3. Website implementieren

Für jede SQL Query schaue ich erstmal mit `\d tabellenname`, welche Spalten es gibt.

Dann überlege ich mir eine SQL Query.

#### Articles

```sql
SELECT * FROM articles;
```

#### Clients

```sql
SELECT * FROM clients;
```

#### Orders

```sql
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
```

#### Place an order

```sql
-- Variablen werden mit %d bzw. %s hier in der Dokumentation beschrieben.

-- Zuerst muss die nächste freie order id gefunden werden
SELECT MAX(id) FROM orders;
-- Zum Einsetzen eines neuen order_lines muss auch die nächste freie order lines id gefunden werden
SELECT MAX(id) FROM order_lines;

-- Jetzt setze ich ein neues order ein.
INSERT INTO orders (id, client_id) VALUES (%d, %d);

-- Dann müssen noch die verfügbaren (amount) Artikel geholt werden.
SELECT amount FROM articles WHERE id = %d;

-- Jetzt muss der amount reduziert werden
UPDATE articles SET amount = amount - %d WHERE id = %d;

-- Zum Schluss wird ein order line inserted
INSERT INTO order_lines VALUES(%d, %d, %d, %d);
```

#### Java

SELECT Queries muss man mit executeQuery() ausführen, INSERT und UPDATE muss man mit executeUpdate() ausführen.

# INSY WS03 ORM

## Praxis

Zuerst habe ich mir in der *pom.xml* Datei die Dependency für Hibernate hinzugefügt.

### XML Dateien

Dann habe ich mir die Datei *hibernate.cfg.xml* erstellt, mit den entsprechenden Werten:  
```xml
<?xml version='1.0' encoding='UTF-8'?>  
<!DOCTYPE hibernate-configuration PUBLIC  
          "-//Hibernate/Hibernate Configuration DTD 5.3//EN"  
          "http://hibernate.sourceforge.net/hibernate-configuration-5.3.dtd">  
  
<hibernate-configuration>  
  
    <session-factory>  
        <property name="hbm2ddl.auto">update</property>  
        <property name="dialect">org.hibernate.dialect.ProgressDialect</property>  
        <property name="connection.url">jdbc:postgresql://172.17.0.2/webshop</property>  
        <property name="connection.username">postgres</property>  
        <property name="connection.password">Pass2023!</property>  
        <property name="connection.driver_class">org.postgresql.Driver</property>  

        <property name="show_sql">true</property>

        <mapping resource="article.hbm.xml"/>  
        <mapping resource="client.hbm.xml"/>  
        <mapping resource="order.hbm.xml"/>  
        <mapping resource="orderLine.hbm.xml"/>  
    </session-factory>  
  
</hibernate-configuration> 
```

Dann habe ich die Mappings erstellt:

TODO

Im Code habe ich folgendes hinzugefügt:  
```java
Configuration configuration = new Configuration()
    .configure();
Server.factory = configuration.buildSessionFactory();
```

Damit soll die hibernate.cfg.xml geladen werden. Dafür muss man aber die Dateien als Resource deklarieren.

Die Dateien waren bei mir im Wurzelverzeichnis des Projektes, ich habe dann ein Verzeichnis *src/main/resources*
erstellt und die Dateien dorthin gegeben. Dann in der *pom.xml*:  
```xml
  <build>
      <resources>
          <resource>
              <directory>src/main/resources</directory>
          </resource>
      </resources>
    <pluginManagement>


        ... (hier sind plugins)


  </build>
```

Jetzt können die xml Dateien als Ressource gefunden werden und werden geladen.

### SessionFactory

```java
public class Server {

    private static SessionFactory factory;

...

    public void start() throws IOException {
        Configuration configuration = new Configuration()
            .configure(); // hibernate.cfg.xml verwenden
        // session factory erstellen
        Server.factory = configuration.buildSessionFactory();
```

### SELECT

Um einfache SELECT Abfragen zu machen, wie z.B. für die Clients, 
muss man `session.createSelectionQuery` und ein HQL (Hibernate Query Language)
Statement ausführen.

```java
            Session session = Server.factory.openSession();
            session.beginTransaction();
            
            List<Client> articles = session.createSelectionQuery("from Client", Client.class)
                .list();

            session.getTransaction().commit();
            JSONArray res = new JSONArray(articles);
            answerRequest(t, res.toString(2));
```


### Articles, Bestellungen, Kunden

HQL:  
- articles: "from Article"
- clients: "from Client"

Für orders habe ich einfach die bestehende SQL Query verwendet, und als Klasse `Object[].class` angegeben.

Ein Problem war, dass Hibernate mir die zwei hinteren Spalten als long, nicht als int zurückgegeben hat,
dies habe ich herausgefunden mit:

```java
            JSONArray res = new JSONArray();
            System.out.println("res size: " + results.size());
            for(var obj : results) {
                System.out.println("obj len: " + obj.length);
                System.out.println((Integer)obj[0]);
                System.out.println((String)obj[1]);
                System.out.println(obj[2].getClass());
                System.out.println(obj[3].getClass());
                // OrderJoined orderJoined = new OrderJoined(
                //         (Integer)obj[0],
                //         (String)obj[1],
                //         (Integer)obj[2],
                //         (Integer)obj[3]
                //         );
                // res.put(orderJoined);
            }
```

Anschließend konnte ich OrderHandler so implementieren:  
```java
            // this record saves the result
            record OrderJoined (int id, String clientName, long orders, long price) {};

            Session session = Server.factory.openSession();
            session.beginTransaction();
            

            
            // read all orders and add them to res
            // Join orders with clients, order lines, and articles
            // Get the order id, client name, number of lines, and total price of each order and add them to res
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

            // execute the sql query
            var results = 
                session.createNativeQuery(QUERY, Object[].class)
                .list();

            // convert to OrderJoined and save it in JSONArray (for converting to JSON)
            JSONArray res = new JSONArray();
            for(var obj : results) {
                OrderJoined orderJoined = new OrderJoined(
                        (Integer)obj[0],
                        (String)obj[1],
                        (Long)obj[2],
                        (Long)obj[3]
                        );
                res.put(orderJoined);
            }

            session.getTransaction().commit();
            answerRequest(t, res.toString(2));
```

Dies hat mir aber als Antwort kein JSON zurückgegeben, weshalb ich es auf folgendes geändert habe:

```java
            Session session = Server.factory.openSession();
            session.beginTransaction();
            

            
            // read all orders and add them to res
            // Join orders with clients, order lines, and articles
            // Get the order id, client name, number of lines, and total price of each order and add them to res
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

            // execute the sql query
            var results = 
                session.createNativeQuery(QUERY, Object[].class)
                .list();

            // convert to OrderJoined and save it in JSONArray (for converting to JSON)
            JSONArray res = new JSONArray();
            for(var obj : results) {
                JSONObject order = new JSONObject();
                order.put("id", (Integer)obj[0]);
                order.put("name", (String)obj[1]);
                order.put("amount", (Long)obj[2]);
                order.put("price", (Long)obj[3]);
                res.put(order);
            }

            session.getTransaction().commit();
            answerRequest(t, res.toString(2));
```

### Bestellung abschicken

## Quellen

- [mvn Ordner zum Classpath hinzufügen](https://stackoverflow.com/questions/9063296/how-can-i-write-maven-build-to-add-resources-to-classpath)
- [Hibernate Quickstart Tutorial](https://www.javatpoint.com/example-to-create-hibernate-application-in-eclipse-ide)
- [Hibernate Community Documentation (for Mapping)](https://docs.jboss.org/hibernate/core/3.3/reference/en/html/mapping.html)
- [Hibernate Documentation](https://hibernate.org/orm/documentation/6.4/)
- [Hibernate Documentation HQL](https://docs.jboss.org/hibernate/orm/6.4/querylanguage/html_single/Hibernate_Query_Language.html#syntax)
