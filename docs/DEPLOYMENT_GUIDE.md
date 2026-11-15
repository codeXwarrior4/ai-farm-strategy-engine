# AI Farm Strategy & Decision Engine — Free-Tier Cloud Deployment Guide

This guide provides step-by-step instructions to deploy the AI Farm Strategy & Decision Engine to the cloud using free-tier services:
- **Database**: TiDB Cloud Serverless (MySQL 8 compatible, free tier)
- **Backend**: Render (Java 21 Spring Boot 3.4.1 in containerized Docker, free tier)
- **Frontend**: Vercel (React 19 / TypeScript / Vite, free tier)

---

## 1. Architecture Topology

```
                  ┌──────────────────────────────────────────────┐
                  │                 USER BROWSER                 │
                  └──────────────────────┬───────────────────────┘
                                         │
                                         ▼ HTTPS
                          ┌─────────────────────────────┐
                          │   Vercel (React Frontend)   │
                          │   https://<app>.vercel.app  │
                          └──────────────┬──────────────┘
                                         │
                                         ▼ HTTPS / JSON REST
                          ┌─────────────────────────────┐
                          │    Render (Spring Boot)     │
                          │ https://<api>.onrender.com  │
                          └──────┬───────────────┬──────┘
                                 │               │
                     MySQL + TLS │               │ HTTPS REST
                                 ▼               ▼
                   ┌───────────────────┐   ┌───────────────────────────┐
                   │    TiDB Cloud     │   │ External APIs:            │
                   │    Serverless     │   │ - Open-Meteo (Weather)    │
                   │    (MySQL 8.0)    │   │ - AGMARKNET (data.gov.in) │
                   └───────────────────┘   └───────────────────────────┘
```

---

## 2. Step 1: Database Deployment (TiDB Cloud Serverless)

TiDB Cloud Serverless provides a free MySQL 8.0-compatible database with automatic SSL/TLS encryption.

### Exact Steps:
1. Open [https://tidbcloud.com/](https://tidbcloud.com/) in your browser.
2. Sign in or register a free account (using GitHub or Google login).
3. In the console, click **Create Cluster** (or **Create Database**).
4. Select **Serverless** (Free forever tier: 5 GiB storage, 50 million Request Units/month).
5. Choose a region closest to your users or backend (e.g., `AWS / us-east-1` or `AWS / ap-south-1 Mumbai`).
6. Click **Create**. Your cluster will provision in ~20 seconds.
7. Click the **Connect** button on the cluster overview page.
8. Select **Connect With: General** or **Java (JDBC)**.
9. Click **Generate Password** (save this password securely).
10. Note the connection parameters provided:
    - **Host**: e.g., `gateway01.us-east-1.prod.aws.tidbcloud.com`
    - **Port**: `4000`
    - **User**: e.g., `2AbcDef.root`
    - **Database**: `test` (or create a dedicated database named `farm_engine`)
11. Construct your Spring Boot JDBC URL in this exact format:
    ```
    jdbc:mysql://<HOST>:4000/<DATABASE>?useSSL=true&sslMode=VERIFY_IDENTITY&serverTimezone=UTC&allowPublicKeyRetrieval=true
    ```
    *(Example: `jdbc:mysql://gateway01.us-east-1.prod.aws.tidbcloud.com:4000/test?useSSL=true&sslMode=VERIFY_IDENTITY&serverTimezone=UTC&allowPublicKeyRetrieval=true`)*

> [!NOTE]
> Flyway will automatically execute migrations `V1` through `V10` upon backend startup and create all required tables (`users`, `farmer_profiles`, `market_price_records`, `crop_master`, `crop_cost_benchmarks`, `crop_yield_benchmarks`, etc.).

---

## 3. Step 2: Backend Deployment (Render)

Render hosts the Java 21 Spring Boot backend using the multi-stage `Dockerfile` prepared in the repository.

### Exact Steps:
1. Open [https://render.com/](https://render.com/) and log in (e.g. with GitHub).
2. Click **New +** and select **Web Service**.
3. Choose **Build and deploy from a Git repository** and connect your repository: `codeXwarrior4/ai-farm-strategy-engine`.
4. Fill in the service configuration:
   - **Name**: `ai-farm-strategy-backend` (or a name of your choice)
   - **Region**: Choose the closest region to your TiDB database (e.g., Ohio or Frankfurt)
   - **Branch**: `main`
   - **Root Directory**: Leave blank (uses repo root `Dockerfile`) or set to `backend`
   - **Runtime**: **Docker**
   - **Instance Type**: **Free** ($0/month, 512 MB RAM, 0.1 CPU)
5. Scroll down to **Environment Variables** and add the following:

| Key | Value / Placeholder | Notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | Activates production profile |
| `DB_URL` | `jdbc:mysql://<HOST>:4000/<DATABASE>?useSSL=true&sslMode=VERIFY_IDENTITY&serverTimezone=UTC&allowPublicKeyRetrieval=true` | From TiDB Cloud |
| `DB_USERNAME` | `<TIDB_USERNAME>` | From TiDB Cloud (e.g. `xxxx.root`) |
| `DB_PASSWORD` | `<TIDB_PASSWORD>` | Generated in TiDB Cloud |
| `JWT_SECRET` | Generate a 64-char random hex string | Required for HMAC-SHA256 tokens |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Temporary until Vercel URL is created |
| `DATA_GOV_IN_API_KEY` | `<YOUR_KEY_OR_BLANK>` | Optional (leave empty if not using live Agmarknet sync) |

6. In **Advanced Settings**:
   - **Health Check Path**: `/api/v1/health`
7. Click **Create Web Service**.
8. Render will pull the repository, build the Docker image, run the Spring Boot application, and execute Flyway migrations V1–V10.
9. When the deployment finishes, Render will assign your backend a public URL:
   `https://ai-farm-strategy-backend-XXXX.onrender.com`
10. Test the backend health endpoint in your browser:
    `https://<YOUR-RENDER-BACKEND>.onrender.com/api/v1/health`
    Expected response:
    ```json
    {
      "status": "UP",
      "service": "ai-farm-strategy-backend",
      "version": "0.1.0",
      "timestamp": "..."
    }
    ```

---

## 4. Step 3: Frontend Deployment (Vercel)

Vercel provides free-tier hosting for the React 19 / Vite single-page application.

### Exact Steps:
1. Open [https://vercel.com/](https://vercel.com/) and log in (e.g. with GitHub).
2. Click **Add New...** > **Project**.
3. Import your repository: `codeXwarrior4/ai-farm-strategy-engine`.
4. Configure the project settings:
   - **Framework Preset**: **Vite**
   - **Root Directory**: Click **Edit** and select `frontend`
   - **Build Command**: `npm run build` (or `tsc -b && vite build`)
   - **Output Directory**: `dist`
   - **Install Command**: `npm install`
5. Expand **Environment Variables** and add:

| Key | Value | Notes |
|---|---|---|
| `VITE_API_BASE_URL` | `https://<YOUR-RENDER-BACKEND>.onrender.com/api/v1` | **Must include `/api/v1`** with no trailing slash |

6. Click **Deploy**.
7. Vercel will install dependencies, build the React SPA, and provide your public site URL:
   `https://<YOUR-APP-NAME>.vercel.app`

---

## 5. Step 4: Final Connection (CORS Update)

Now that your Vercel URL is generated:
1. Go back to your **Render Dashboard** -> `ai-farm-strategy-backend` -> **Environment**.
2. Find `CORS_ALLOWED_ORIGINS`.
3. Update the value to:
   ```
   https://<YOUR-APP-NAME>.vercel.app,http://localhost:5173
   ```
4. Click **Save Changes**. Render will automatically redeploy the backend in ~30 seconds with the allowed origin.

---

## 6. Verification & End-to-End Testing

Once both services are running:
1. **Frontend Landing & Status**:
   - Open `https://<YOUR-APP-NAME>.vercel.app`
   - Verify the dashboard loads cleanly with status indicators showing Backend UP.
2. **User Authentication**:
   - Register a new farmer account (e.g. `testfarmer` / `FarmerPassword123!`).
   - Log in and verify JWT token receipt and storage in localStorage.
3. **Farmer Profile**:
   - Enter landholdings, soil type, irrigation source, coordinates.
   - Save profile and verify database persistence.
4. **Agronomic Strategy & Benchmarks**:
   - Generate crop strategies and verify feasibility rankings.
   - Inspect official Jalna APMC mandi prices loaded from migration V9/V10.
5. **PDF Export**:
   - Click Download PDF Advisory and verify client-side jsPDF generation.

---

## 7. Free-Tier Characteristics & Sleep Behavior

- **Render Cold Starts**: Render free-tier web services sleep after 15 minutes of inactivity. When a request arrives, the first response takes ~30–50 seconds to wake up. This is expected on free hosting.
- **TiDB Serverless Limits**: 5 GiB storage and 50M Request Units per month (far exceeds portfolio/demo requirements).
- **Vercel Limits**: 100 GB bandwidth per month, unlimited deployments.
