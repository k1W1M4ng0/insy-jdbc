.PHONY: run

run:
	mvn package && java -cp target/jdbc-*.jar insy.Server
