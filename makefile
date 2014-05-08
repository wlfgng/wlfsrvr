srvr:
	java AuthServer
srvc:
	javac AuthServer.java
clc:
	javac AuthClientTest.java
clr:
	java AuthClientTest
all:
	javac *.java
clean:
	rm *.class
