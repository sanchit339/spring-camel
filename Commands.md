# Commands used for the project

## ActiveMQ (Docker)
- `docker run -p 61616:61616 -p 8161:8161 rmohr/activemq`
  - `61616` — ActiveMQ broker port (OpenWire). This is the port Camel/Spring Boot connects to for sending and receiving messages.
  - `8161` — ActiveMQ web admin dashboard: http://localhost:8161
  - Dashboard credentials: username `admin`, password `admin`
