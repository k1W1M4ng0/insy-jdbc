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
- die IP Adresse in der URL zum Container (`docker inspect postgres`): **172.17.0.2**
- das Passwort zu **Pass2023!** (ist noch von der ersten Übung)

