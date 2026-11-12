# ParkFlow — Car Parking Management System

A full-stack demo project using **Java 17 + Spring Boot + MySQL 8** with an HTML/CSS/vanilla JavaScript frontend. Spring Boot serves both the frontend and REST API from `http://localhost:8080`.

## Features

- Admin and customer sign-in
- 48 parking slots
- Customer bookings with server-side pricing and overlap checks
- Demo payment confirmation saved in MySQL
- Admin dashboard, parking slots, vehicles, payments and reports
- Automatic demo-data seeder for five customer accounts, 20 bookings and 16 payment records
- BCrypt password hashes for demo accounts

## Requirements

- Java 17 or newer
- Maven 3.8+
- MySQL 8+

## 1. Create the database

Run this once in MySQL Workbench or the MySQL command line:

```sql
CREATE DATABASE parkflow;
```

Hibernate creates/updates the application tables on startup (`spring.jpa.hibernate.ddl-auto=update`).

## 2. Configure MySQL

Open `src/main/resources/application.properties`. The default JDBC URL uses local MySQL port **3307**:

```properties
spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3307/parkflow?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC}
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:}
```

Change the port if your MySQL uses a different one, or set `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` as environment variables. Do not share your real database password.

## 3. Start ParkFlow

From the project folder:

```bash
mvn spring-boot:run
```

Or build and run the JAR:

```bash
mvn clean package
java -jar target/parkflow-1.0.0.jar
```

Open **http://localhost:8080**. Do not open `index.html` directly from File Explorer; the pages call the Spring Boot API.

## Demo login accounts

**Admin**

| Role | Email | Password |
|---|---|---|
| Admin | `admin@parkflow.demo` | `admin123` |

**Five customer accounts** (all customer passwords are `user123`)

| Customer | Email | Password |
|---|---|---|
| Demo User | `user@parkflow.demo` | `user123` |
| Priya Sharma | `priya@parkflow.demo` | `user123` |
| Rahul Verma | `rahul@parkflow.demo` | `user123` |
| Ananya Reddy | `ananya@parkflow.demo` | `user123` |
| Arjun Mehta | `arjun@parkflow.demo` | `user123` |

## Demo data seeding

On the first startup against a database that does not already contain the demo marker booking, ParkFlow automatically inserts:

- 48 parking slots (only if the slots table is empty)
- The admin and five customer demo accounts (existing accounts are not overwritten)
- 20 clearly labelled demo bookings: active, confirmed, pending-payment and completed
- 16 simulated payment records using CASH, UPI, CARD and DEMO methods

The demo booking IDs start with `PFDEMO`. The seeder checks for `PFDEMO001` before inserting, so normal restarts do not duplicate the demo bookings or payments. Existing bookings and payments are not deleted or overwritten. To create a fresh demo dataset, use a separate empty `parkflow` database rather than deleting records from a database you need.

All demo payments are simulated records. No real money is charged.

## Payment note

The payment flow currently uses a **demo payment** endpoint. It saves a payment record and confirms the booking in MySQL, but it does not charge real money. Real Razorpay payments require server-side order creation, signature verification and webhook handling. Never put a Razorpay Key Secret in frontend JavaScript.

## API overview

- `POST /api/auth/login`
- `GET /api/auth/me`
- `GET /api/slots`
- `POST /api/bookings`
- `GET /api/bookings/mine`
- `GET /api/admin/bookings` (admin only)
- `GET /api/admin/payments` (admin only)
- `POST /api/payments/demo/{bookingId}`

## Important deployment notes

This is a learning/demo starter, not a production parking platform. Session tokens are stored in memory and are lost when the server restarts. Add persistent sessions/JWT, HTTPS, rate limiting, stronger account provisioning and database migrations before public deployment. The current demo-payment endpoint is deliberately not a real payment gateway.
