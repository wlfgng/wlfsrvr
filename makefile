all:
	mvn package -e
srv:
	java -cp target/wlfsrvr-1.0-SNAPSHOT.jar WlfServer
clt:
	java -cp target/wlfsrvr-1.0-SNAPSHOT.jar AuthClientTest
	
