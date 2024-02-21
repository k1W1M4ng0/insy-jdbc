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

# INSY SoSe01 Transaktionen

## Theorie

### 2 Beispiele, warum man Transaktionen braucht:

#### Eine Bank verwaltet Kontostäende, Kunde 1 überweist an Kunde 2.

```sql
update accnts set amnt = amnt - 100 where client id = 1
update accnts set amnt = amnt + 100 where client id = 2
```

Wenn die Datenbank zwischen den Statements abstürzt, geht Geld verloren.

#### Bestellungen bei einem Webshop

```sql
-- prüfen, ob genug produkte da sind
select amount from products where pid = 42

-- bestellung
insert into order (pid, cid, amount) values (42,1,10)

-- menge abziehen
update product set amount = amount-10 where pid = 42
```

Wenn zwei Kunden gleichzeitig ein fast ausverkauftes Produkt kaufen, kann es sein, dass
mehr Produkte als verfügbar verkauft werden.

### ACID

Transaktionen sollen helfen, die ACID Kriterien zu erfüllen.

- **Atomicity**: werden komplett, oder gar nicht ausgeführt
- **Consistency**: Constraints sind am Ende erfüllt
- **Isolation**: unabhängig voneinander
- **Durability**: dauerhaft gespeichert

### Isolation

Isolation bestimmt, wie Transaktionen voneinander getrennt werden.

Davor sollte man sich die 3 Reading Phenomena anschauen.

Diese passieren, wenn eine Transaktion A Daten liest, die von einer anderen Transaktion B geändert werden.

#### Dirty read

Dirty reads passieren, wenn A Daten liest, die von B geändert, aber noch nicht committed wurden.
Wenn B jetzt einen Rollback ausführt, dann sind die von A ausgewählten Daten falsch.

#### Non-repeatable reads

Non-repeatable reads passieren, wenn A zweimal eine Zeile liest, und B diese Zeile ändert und committed, 
dann sind diese Daten beim zweiten Lesen anders.

#### Phantom reads

Phantom reads passieren, wenn A zweimal eine Query ausführt (und z.B. mehrere Reihen auswählen kann),
und B Zeilen hinzufügt oder entfernt. Dann kann es sein, dass Zeilen dazukommen oder ausfallen.

#### Isolation Levels

Es gibt verschiedene Isolation Levels, und diese bestimmen, welche Fehlerklassen erlaubt werden:  
- Read uncommitted: erlaubt alle 
- Read committed: verbietet dirty reads
- Repeatable read: verbietet dirty reads und non-repeatable reads
- Serializable: verbietet alle

Dies kann man setzen mit `SET TRANSACTION ISOLATION LEVEL x`

### Locking

Postgres implementiert Isolation Levels mit MVCC (Multi Version Concurrency Control),
wobei Snapshots der Daten erstellt werden. Dies ist oft nicht ausreichend.

Deswegen gibt es Locking. Es blockiert gezielt Objekte (Tabellen oder Zeilen) für andere Transaktionen.

Locks sind an die aktuelle Transaktion gebunden.

#### Table-level locks

Es gibt 8 Arten von Table-level locks

Sie werden von postgres implizit (automatisch durch DBMS) verwendet.

Man kann sie auch manuell mit `LOCK TABLE` setzen.

#### Row-level locks

Es gibt 4 Arten von Row-level locks:

- FOR UPDATE: 'Ich möchte diese Zeile ändern'
- FOR SHARE: 'Ich möchte, dass diese Zeile nicht geändert wird'
- FOR NO KEY UPDATE: 'Ich möchte keine Schlüssel ändern.'
- FOR KEY SHARE: 'Ich möchte, dass keine Schlüssel geändert werden'

## Praxis

Eine `Connection` ist automatisch im auto-commit Modus. Dort werden `Statement`s automatisch committed.
Um Transaktionen zu verwenden, muss man dies ausschalten mit `conn.setAutoCommit(false);`

Ich brauche Transaktionen nur beim OrderHandler. 

### 1. Generieren einer ID für neue Bestellungen

Hier setze ich auto-commit auf false, damit ich Transaktionen verwende. 
Außerdem setze ich das Isolation Level auf Serializable, damit keine andere Transaktion Zeilen 
hinzufügen oder entfernen kann.

```java
            Connection conn = setupDB();
            // start transaction
            try {
                conn.setAutoCommit(false);
                // prevent phantom reads
                conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            } catch (SQLException e) {
                e.printStackTrace();
            }
```

Diese Transaktion wird nach dem Inserten in order committed:

```java
                // insert a new order
                String insertOrderQuery = String.format(
                        "INSERT INTO orders (id, client_id) VALUES (%d, %d);",
                        order_id,
                        client_id
                        );
                st.executeUpdate(insertOrderQuery);
                // commit transaction
                conn.commit();
```

### 2. Atomizität bei Bestellungen

Jetzt sollen Bestellungen entweder ganz oder gar nicht durchgeführt werden.
Das heißt, ich committe noch einmal ganz am Ende:

```java
                // commit the order
                conn.commit();

                response = String.format("{\"order_id\": %d}", order_id);
```

Wenn ein Fehler auftritt, muss ein rollback ausgeführt werden.

```java
            catch(SQLException | IllegalArgumentException | InterruptedException ex) {
                try {
                    conn.rollback();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                response = String.format("{\"error\":\"%s\"}", ex.toString());
                ex.printStackTrace();
            }
```

### 3. Gleichzeitiges Abschicken von Bestellungen

Wenn Bestellungen gleichzeitig abgeschickt werden, aber nur noch wenige Artikel vorhanden sind,
kann es passieren, dass der Lagerstand negativ wird. Dies wird bei mir schon durch die Transaktion 
am Ende der Bestellungen verhindert.

Man kann dies testen, indem im OrderHandler ein `Thread.sleep(1000);` ausgeführt wird, und zweimal 
gleichzeitig in separaten Terminals der folgende Befehl ausgeführt wird:

```bash
curl 'http://127.0.0.1:8000/placeOrder?client_id=1&article_id_1=1&amount_1=1&article_id_2=2&amount_2=2'
```

### 4. Anzeige von Statistiken

Bei einem Aufruf von /stats sollen Bestellungen für jedes Land angegeben werden.

```java

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
```

## Quellen

- [stackoverflow: non-repeatable read vs phantom read](https://stackoverflow.com/questions/11043712/non-repeatable-read-vs-phantom-read)
- [geeks for geeks: data isolation](https://www.geeksforgeeks.org/data-isolation-in-dbms/)
- [wikipedia en: data isolation](https://en.wikipedia.org/wiki/Isolation_(database_systems))

- [oracle tutorial: jdbc transactions](https://docs.oracle.com/javase/tutorial/jdbc/basics/transactions.html)

