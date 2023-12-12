.PHONY: run

run: 
	mvn assembly:assembly -DdescriptorId=jar-with-dependencies
	java -jar target/jdbc-1.0-SNAPSHOT-jar-with-dependencies.jar 
