# 🚚 Trucksy – Food Truck Aggregator Platform  

## Overview  
**Trucksy** is a backend system that aggregates food trucks in Riyadh, Saudi Arabia.  
It enables customers to easily find and order from nearby food trucks, while owners manage their trucks, menus, orders, and subscriptions.  

Premium owners gain access to **AI-powered insights and analytics** after subscribing.  

---

## Features  

### Customer  
- Sign up / Login.  
- Share location (via **HERE API** → converts city & district into lat/lng).  
- Browse nearby food trucks (distance calculation).  
- Search and filter trucks by category (e.g., burgers, desserts).  
- View truck profiles (menu, reviews, operating hours).  
- Add items to cart and place orders.  
- Pay online (**Moyasar payment gateway**).  
- Track orders (PLACED → READY → COMPLETED).  
- Write reviews and view average ratings.  
- Receive notifications for offers and order updates.  

### Owner  
- Register / Login.  
- Add / Update / Delete food trucks.  
- Upload truck images (**AWS S3**).  
- Manage menu items (CRUD, price updates, availability).  
- Upload item images (**AWS S3**).  
- Create and manage discounts.  
- Open / Close truck.  
- Manage orders (change status).  
- Dashboard with key metrics:  
  - Total orders & revenue.  
  - Peak hours & best-selling items.  
- **Premium Features (after subscription)**:  
  - AI-powered insights on sales trends.  
  - Review sentiment analysis (positive/negative).  
  - AI-based recommendations (e.g., “Busiest time is 8 PM”).  
  - Advanced reporting & analytics.  
- Subscription and renewal through **Moyasar** payment API.  

### Admin  
- View all users.  
- Delete users.  
- Delete food trucks.  
- Access system-wide reports.  

---

## Tech Stack  
- **Backend:** Spring Boot 3, Spring Security, Hibernate/JPA  
- **Database:** MySQL  
- **Storage:** AWS S3 (for truck and item images)  
- **Payment:** Moyasar API  
- **Geocoding:** HERE API  
- **Authentication:** Spring Security with roles (`CLIENT`, `OWNER`, `ADMIN`)  
- **Build Tool:** Maven  

---

## Roles & Example Endpoints  

### Public  
- `POST /api/v1/client/add` → Register a customer.  
- `POST /api/v1/owner/add` → Register an owner.  

### Customer  
- `GET /api/v1/foodTruck/get-nearest/{client_id}?limit=5` → Fetch nearest food trucks.  
- `POST /api/v1/order/add/{clientId}/{truckId}` → Place an order.  
- `POST /api/v1/review/add/{clientId}/{truckId}` → Write a review.  

### Owner  
- `POST /api/v1/foodTruck/add/{ownerId}` → Add a food truck.  
- `POST /api/v1/item/add/{ownerId}/{truckId}` → Add item to a food truck.  
- `POST /api/v1/foodTruck/upload-image/{truckId}` → Upload truck image.  
- `POST /api/v1/owner/subscribe` → Subscribe to premium plan.  
- `POST /api/v1/owner/callback/{ownerId}` → Handle subscription callback.  

### Admin  
- `GET /api/v1/auth/get` → Get all users.  
- `DELETE /api/v1/auth/delete/{user_id}` → Delete a user.  
- `DELETE /api/v1/auth/delete-foodTruck/{truckId}` → Delete a food truck.  

---

## Diagrams

### ER Diagram
![ER Diagram](https://github.com/HassanAL-Hussaini/Trucksy/blob/master/Untitled%20diagram%20_%20Mermaid%20Chart-2025-09-08-220705.png)

> This ER Diagram shows all main entities in Trucksy, including **Users, Clients, Owners, Food Trucks, Items, Orders, Payments, Reviews, Dashboards, Discounts, and BankCards**, along with their relationships.  
> It also visualizes one-to-one, one-to-many, and many-to-many relations clearly.  

### Use Case Diagram
![Use Case Diagram](https://github.com/HassanAL-Hussaini/Trucksy/blob/Env/Use_case_daigram.png)

> The Use Case Diagram illustrates interactions between **Customers, Owners, and Admins** with the Trucksy system.  
> It highlights key functionalities like **ordering, reviewing, managing trucks, and subscription handling**.  

---

### Links

- [🎨 Figma Design](https://www.figma.com/design/hvVvEeGbhr3lqANwAzAeNj/System-Flow?node-id=0-1&t=lzGzHO9Y5U0X9ov2-1)
- [📬 Postman Documentation](https://documenter.getpostman.com/view/41550137/2sB3HnJeqi)  
- [📊 Presentation (Canva)](https://www.canva.com/design/DAGyX97zdi0/MZaNDYxF5TtX6ONkAkyFzQ/edit?utm_content=DAGyX97zdi0&utm_campaign=designshare&utm_medium=link2&utm_source=sharebutton)






## Enhancements & Creative Improvements
- **Colorful diagrams**: Entities and relationships are color-coded for clarity (use Mermaid for dynamic visualization).  
- **Detailed captions**: Each diagram has a short description explaining the functionality.  
- **Better structure**: Features, roles, endpoints, and diagrams are well-organized for quick reading.  
- **Visual cues**: Arrows and symbols in diagrams represent cardinality and relationships.  
- **Future-ready**: New entities like **BankCard** are included in the ERD with relationships to users.  

---

## Summary
Trucksy is designed to be a **full-featured backend solution** for food trucks in Riyadh.  
It balances **customer convenience, owner management, and admin control**, while integrating **AI-powered insights** for premium users.  
With clear data modeling, secure payment integration, and location-based services, Trucksy provides a complete ecosystem for food truck operations.  
--------

# API Endpoints Summary

| Controller | Count |
|---|---:|
| AuthController | 5 |
| BankCardController | 2 |
| ClientController | 4 |
| DashboardController | 8 |
| DiscountController | 7 |
| FoodTruckController | 10 |
| OrderController | 8 |
| OwnerController | 6 |
| ReviewController | 4 |
| WhatsAppController | 1 |
| ItemController | 9 |
| **Total** | **64** |

---

## AuthController (`/api/v1/auth`)
| Method | Path | Description | Name |
|---|---|---|---|
| GET | `/get` | Get all users | muteb |
| DELETE | `/delete/{user_id}` | Delete user | muteb |
| GET | `/get-all-owners` | Get all owners | muteb |
| GET | `/get-all-clients` | Get all clients | muteb |
| DELETE | `/delete-foodTruck/{foodTruck_id}` | Delete a food truck (admin) | muteb |

---

## BankCardController (`/api/v1/bankcard`)
| Method | Path | Description | Name |
|---|---|---|---|
| POST | `/add` | Add bank card (current user) | hassan |
| GET | `/get` | Get bank card by current user | hassan |

---

## ClientController (`/api/v1/client`)
| Method | Path | Description | Name |
|---|---|---|---|
| POST | `/add` | Register client | muath |
| PUT | `/update` | Update client (current user) | muath |
| DELETE | `/delete` | Delete client (current user) | muath |
| PUT | `/update-client-location` | Update client location | muath |

---

## DashboardController (`/api/v1/dashboard`)
| Method | Path | Description | Name |
|---|---|---|---|
| PUT | `/refresh-dashboard` | Refresh owner dashboard | muath |
| GET | `/get-owner-dashboard` | Get owner dashboard | muath |
| GET | `/get-all-order-by-foodTruck/{foodTruck_id}` | Orders by truck | muath |
| GET | `/analyze-reviews/{foodTruckId}` | AI review analyzer | hassan |
| GET | `/analyze-dashboard` | AI dashboard analyzer | hassan |
| GET | `/get-Placed-orders` | Get PLACED orders (owner) | muath |
| GET | `/get-ready-orders` | Get READY orders (owner) | muath |
| GET | `/get-completed-orders` | Get COMPLETED orders (owner) | muath |

---

## DiscountController (`/api/v1/discount`)
| Method | Path | Description | Name |
|---|---|---|---|
| GET | `/getAll/{truckId}` | All discounts for a truck | muteb |
| GET | `/get/{truckId}/{itemId}` | Discount by item | muteb |
| POST | `/add/{truckId}/{itemId}` | Add discount to item | muteb |
| PUT | `/update/{truckId}/{discountId}` | Update discount | muteb |
| DELETE | `/delete/{truckId}/{discountId}` | Delete discount | muteb |
| PUT | `/activate/{truckId}/{discountId}` | Activate discount | muteb |
| PUT | `/deactivate/{truckId}/{discountId}` | Deactivate discount | muteb |

---

## FoodTruckController (`/api/v1/foodTruck`)
| Method | Path | Description | Name |
|---|---|---|---|
| POST | `/add` | Add food truck | muath |
| PUT | `/update/{truck_id}` | Update food truck | muath |
| DELETE | `/delete/{truck_id}` | Delete food truck | muath |
| GET | `/get-all-trucks-by-owner_id` | All trucks for current owner | muath |
| GET | `/get-foodTrucks-by-category/{category}` | Trucks by category | muath |
| GET | `/get-nearest?limit={n}` | Top nearest trucks (client) | muath |
| PUT | `/update-food-truck-location/{foodTruck_id}` | Update truck location | muath |
| PUT | `/open-foodTruck/{foodTruck_id}` | Open truck | muath |
| PUT | `/close-foodTruck/{foodTruck_id}` | Close truck | muath |
| POST | `/upload-image/{truck_id}` | Upload truck image (multipart) | muath |

---

## OrderController (`/api/v1/order`)
| Method | Path | Description | Name |
|---|---|---|---|
| POST | `/add/{foodTruckId}` | Create order (client) | hassan |
| POST | `/callback/{orderId}` | Payment callback (no PID) | hassan |
| POST | `/callback/{orderId}/{paymentId}` | Payment callback (with PID) | hassan |
| PUT | `/status/ready/{foodTruckId}/{orderId}` | Mark order READY (owner) | hassan |
| PUT | `/status/completed/{foodTruckId}/{orderId}` | Mark order COMPLETED (owner) | hassan |
| GET | `/foodtruck/{foodTruckId}` | List orders for a truck | hassan |
| GET | `/client` | List orders for current client | hassan |
| GET | `/foodtruck/{foodTruckId}/{orderId}` | Get single order for a truck | hassan |

---

## OwnerController (`/api/v1/owner`)
| Method | Path | Description | Name |
|---|---|---|---|
| POST | `/add` | Register owner | hassan |
| PUT | `/update` | Update owner (current user) | hassan |
| DELETE | `/delete` | Delete owner (current user) | hassan |
| POST | `/subscribe` | Start subscription payment | hassan |
| POST | `/callback/{ownerId}` | Subscription callback (no PID) | hassan |
| POST | `/callback/{ownerId}/{paymentId}` | Subscription callback (with PID) | hassan |

---

## ReviewController (`/api/v1/review`)
| Method | Path | Description | Name |
|---|---|---|---|
| POST | `/add/{foodTruck_id}` | Add review to truck (client) | muath |
| GET | `/get-reviews-by-truck/{foodTruck_id}` | Reviews by truck | muath |
| GET | `/get-reviews-by-client` | Reviews by current client | muath |
| GET | `/get-truck-rating/{foodTruck_id}` | Average rating for truck | muath |

---

## WhatsAppController (`/api/v1/whatsApp`)
| Method | Path | Description | Name |
|---|---|---|---|
| POST | `/send-text` | Send WhatsApp text | muteb |

---

## ItemController (`/api/v1/item`)
| Method | Path | Description | Name |
|---|---|---|---|
| GET | `/get/{truckId}` | Items of a truck (owner) | muteb |
| POST | `/add/{truckId}` | Add item to truck | muteb |
| PUT | `/update/{truckId}/{itemId}` | Update item | muteb |
| DELETE | `/delete/{truckId}/{itemId}` | Delete item | muteb |
| PUT | `/setAvailable/{truckId}/{itemId}` | Set item available | muteb |
| PUT | `/setNotAvailable/{truckId}/{itemId}` | Set item not available | muteb |
| PUT | `/price/{truckId}/{itemId}/{newPrice}` | Update item price | muteb |
| GET | `/filterByPrice/{truckId}/{min}/{max}` | Filter items by price range | muteb |
| POST | `/image/{truckId}/{itemId}` | Upload item image (multipart) | muath |
