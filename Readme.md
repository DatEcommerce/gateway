# Ecommerce Sequence Diagrams

These diagrams describe the main runtime flows implemented by the services in this repository. Solid arrows are synchronous HTTP/database calls; dashed arrows are responses or asynchronous event delivery.

## 1. Registration and login

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Gateway as API Gateway
    participant Auth as Auth Service
    participant Keycloak
    participant AuthDB as Auth MySQL
    participant Kafka
    participant User as User Service
    participant UserDB as User MySQL

    rect rgb(235, 245, 255)
        Note over Client,UserDB: Registration
        Client->>Gateway: POST /api/v1/auth/register
        Gateway->>Auth: Forward registration request
        Auth->>Keycloak: Request service-account token
        Keycloak-->>Auth: Admin access token
        Auth->>Keycloak: Create enabled user with password
        Keycloak-->>Auth: 201 Created
        Auth->>Keycloak: Find ecommerce client UUID
        Keycloak-->>Auth: Client metadata
        Auth->>Keycloak: Read user client-role definition
        Keycloak-->>Auth: user role representation
        Auth->>Keycloak: Find new user ID
        Keycloak-->>Auth: User ID
        Auth->>Keycloak: Assign user client role
        Keycloak-->>Auth: 204 No Content
        Auth->>AuthDB: Save local user record
        Auth-->>Kafka: Publish RegisterResponse to register-topic
        Auth-->>Gateway: userId, username, role
        Gateway-->>Client: Registration response
        Kafka-->>User: Consume RegisterResponse
        User->>UserDB: Save user profile projection
    end

    rect rgb(245, 255, 240)
        Note over Client,Keycloak: Login
        Client->>Gateway: POST /api/v1/auth/login
        Gateway->>Auth: Forward credentials
        Auth->>Keycloak: Password grant token request
        Keycloak-->>Auth: Access token and refresh token
        Auth-->>Gateway: LoginResponse
        Gateway-->>Client: Access token and refresh token
    end
```

## 2. Product creation and inventory synchronization

```mermaid
sequenceDiagram
    autonumber
    actor Seller
    participant Gateway as API Gateway
    participant Product as Product Service
    participant ProductDB as PostgreSQL
    participant Cloudinary
    participant Kafka
    participant Inventory as Inventory Service
    participant InventoryDB as Inventory MySQL
    participant Connect as Debezium Connect

    Seller->>Gateway: POST /api/v1/products/create<br/>Bearer JWT + multipart form
    Gateway->>Gateway: Validate JWT with Keycloak issuer
    Gateway->>Product: Forward authenticated request
    Product->>ProductDB: Save product
    par Asynchronous image upload
        Product->>Cloudinary: Upload image bytes
        Cloudinary-->>Product: secure_url and public_id
        Product->>ProductDB: Update image metadata
    and New-product event
        Product-->>Kafka: Publish new_product_topic
        Note over Kafka,Inventory: Inventory currently listens to inventory-new-product.<br/>The topic names must be aligned for delivery.
    end
    Product-->>Gateway: ProductResponse
    Gateway-->>Seller: API response

    Note over Inventory,ProductDB: Later inventory updates use the outbox path
    Inventory->>InventoryDB: Update stock and outbox_inventory
    Connect->>InventoryDB: Read outbox row through MySQL CDC
    Connect-->>Kafka: Publish outbox.db_inventory.outbox_inventory
    Kafka-->>Product: Deliver inventory CDC event
    Product->>ProductDB: Update product available stock
```

## 3. Order, payment, and inventory saga

The gateway has no order route at present, so the client calls order service directly in this flow.

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Order as Order Service
    participant OrderDB as Order MySQL
    participant Payment as Payment Service
    participant PaymentDB as Payment MySQL
    participant Connect as Debezium Connect
    participant Kafka
    participant Inventory as Inventory Service
    participant InventoryDB as Inventory MySQL
    participant Product as Product Service
    participant ProductDB as PostgreSQL

    Client->>Order: POST /api/v1/orders/create?userId=...
    Order->>Order: Calculate total price

    alt paymentMethod is COD
        Order->>OrderDB: Save order as IN_PROGRESS
        Order-->>Client: OrderResponse
    else Online payment
        Order->>Payment: POST /api/v1/payments/create
        Payment->>PaymentDB: Save PENDING payment
        Payment->>PaymentDB: Save outbox_payment row
        Payment-->>Order: paymentId and paymentUrl
        Order->>OrderDB: Save WAITING_BANKING order
        Order->>OrderDB: Save outbox_order row
        Order-->>Client: OrderResponse with payment URL

        Connect->>OrderDB: Read outbox_order through CDC
        Connect-->>Kafka: Publish outbox.db_order.outbox_order
        Kafka-->>Inventory: Deliver order event
        Inventory->>InventoryDB: Increase reserved stock
        Inventory->>InventoryDB: Save outbox_inventory row
        Connect->>InventoryDB: Read outbox_inventory through CDC
        Connect-->>Kafka: Publish outbox.db_inventory.outbox_inventory
        Kafka-->>Product: Deliver available-stock event
        Product->>ProductDB: Update catalog stock

        Client->>Payment: POST /api/v1/payments/update-status
        Payment->>PaymentDB: Update payment and outbox_payment
        Payment-->>Client: Status update response
        Connect->>PaymentDB: Read outbox_payment through CDC
        Connect-->>Kafka: Publish outbox.db_payment.outbox_payment
        Kafka-->>Order: Deliver payment status event

        alt payment status is SUCCESS
            Order->>OrderDB: Mark order COMPLETED
        else payment status is FAILED
            Order->>OrderDB: Mark order CANCELLED
        end

        Order->>OrderDB: Update outbox_order
        Connect->>OrderDB: Read updated outbox row
        Connect-->>Kafka: Publish updated order event
        Kafka-->>Inventory: Deliver final order status

        opt Order was cancelled
            Inventory->>InventoryDB: Release reserved stock
            Inventory->>InventoryDB: Update outbox_inventory
            Connect-->>Kafka: Publish updated available stock
            Kafka-->>Product: Deliver stock restoration event
            Product->>ProductDB: Restore catalog availability
        end
    end
```

## 4. Product circuit-breaker fallback

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Gateway as API Gateway
    participant Product as Product Service
    participant Fallback as Fallback Service

    Client->>Gateway: Request /api/v1/products/**
    Gateway->>Gateway: Validate JWT unless route is public
    Gateway->>Product: Forward request

    alt Product responds normally
        Product-->>Gateway: Successful response
        Gateway-->>Client: Successful response
    else Product returns 404, 500, or 503
        Product-->>Gateway: Failure response
        Gateway->>Gateway: Circuit breaker invokes forward:/fallback/products
        Gateway->>Fallback: GET /fallback/products<br/>with exception headers
        Fallback->>Fallback: Log exception type and message
        Fallback-->>Gateway: Fallback product service
        Gateway-->>Client: Fallback response
    end
```

## Current integration constraints

The diagrams show the intended end-to-end behavior while preserving the names used by the code. These checked-in settings need alignment before every path can run as one local system:

- Gateway sends auth traffic to port `8081`, but auth service defaults to `8082`; Schema Registry also uses `8081`.
- Product service publishes `new_product_topic`, while inventory service listens to `inventory-new-product`.
- Order service and its hard-coded payment Feign client both use port `8084`.
- Gateway only routes authentication and product endpoints; order, payment, inventory, and user traffic is not routed through it.
- The two inventory listeners for `outbox.db_order.outbox_order` share the `inventory-group` consumer group, so Kafka treats them as competing consumers instead of delivering every event to both handlers.
- Debezium emits database column names, while several consumers read camel-case JSON fields. Confirm the connector payload field names or add an event transform/schema before relying on the CDC paths.
