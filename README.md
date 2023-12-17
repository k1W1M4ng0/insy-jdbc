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

