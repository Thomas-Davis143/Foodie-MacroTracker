import express from "express";
import cors from "cors";
import helmet from "helmet";
import rateLimit from "express-rate-limit";
import axios from "axios";
import "dotenv/config";

const app = express();

// --- Security / basics ---
const ALLOWED = (process.env.ALLOWED_ORIGINS || "")
  .split(",").map(s => s.trim()).filter(Boolean);

app.use(cors({
  origin: (origin, cb) => {
    if (!origin || ALLOWED.length === 0 || ALLOWED.includes(origin)) return cb(null, true);
    return cb(new Error("Not allowed by CORS"));
  }
}));
app.use(helmet());
app.use(express.json());
app.use(rateLimit({ windowMs: 60_000, max: 60 }));

app.get("/health", (_req, res) => res.json({ ok: true }));

// --- Helpers ---
const NUTR_IDS = { kcal: 1008, protein: 1003, carbs: 1005, fat: 1004 };
const extract = (food, id) =>
  (food.foodNutrients || []).find(n => n.nutrientId === id)?.value ?? null;

const mapFood = f => ({
  fdcId: f.fdcId,
  description: f.description,
  brand: f.brandName || f.brandOwner || null,
  servingSize: f.servingSize ?? null,
  servingSizeUnit: f.servingSizeUnit ?? null,
  calories: extract(f, NUTR_IDS.kcal),
  protein:  extract(f, NUTR_IDS.protein),
  carbs:    extract(f, NUTR_IDS.carbs),
  fat:      extract(f, NUTR_IDS.fat)
});

// --- Search endpoint (proxy to USDA FDC /v1/foods/search) ---
app.get("/api/foods/search", async (req, res) => {
  try {
    const q = (req.query.q || "").toString().trim();
    if (!q) return res.status(400).json({ error: "Missing query ?q=" });

    const pageSize   = Math.min(Math.max(parseInt(req.query.pageSize || "25", 10), 1), 200);
    const pageNumber = Math.max(parseInt(req.query.pageNumber || "1", 10), 1);

    // Proper param serialization (USDA expects repeated keys, not arrays)
    const params = new URLSearchParams();
    params.append("api_key", process.env.USDA_FDC_API_KEY || "");
    params.append("query", q);
    params.append("pageSize", String(pageSize));
    params.append("pageNumber", String(pageNumber));
    ["Branded", "Survey (FNDDS)", "SR Legacy", "Foundation"].forEach(v =>
      params.append("dataType", v)
    );
    params.append("requireAllWords", "false");

    const { data } = await axios.get("https://api.nal.usda.gov/fdc/v1/foods/search", {
      params,
      paramsSerializer: p => p.toString(),
      timeout: 10_000,
      headers: { Accept: "application/json" }
    });

    res.json({
      totalHits: data.totalHits,
      pageNumber: data.currentPage,
      items: (data.foods || []).map(mapFood)
    });
  } catch (err) {
    const status = err.response?.status || 500;
    return res.status(status).json({
      error: "USDA proxy failed",
      details: err.message,
      usda: err.response?.data || null
    });
  }
});

// --- Optional: details endpoint (/v1/food/{fdcId}) ---
app.get("/api/foods/:fdcId", async (req, res) => {
  try {
    const id = req.params.fdcId;
    if (!id) return res.status(400).json({ error: "Missing fdcId" });

    const params = new URLSearchParams();
    params.append("api_key", process.env.USDA_FDC_API_KEY || "");

    const { data } = await axios.get(`https://api.nal.usda.gov/fdc/v1/food/${id}`, {
      params,
      paramsSerializer: p => p.toString(),
      timeout: 10_000,
      headers: { Accept: "application/json" }
    });

    res.json({
      fdcId: data.fdcId,
      description: data.description,
      brand: data.brandName || data.brandOwner || null,
      servingSize: data.servingSize ?? null,
      servingSizeUnit: data.servingSizeUnit ?? null,
      calories: extract(data, NUTR_IDS.kcal),
      protein:  extract(data, NUTR_IDS.protein),
      carbs:    extract(data, NUTR_IDS.carbs),
      fat:      extract(data, NUTR_IDS.fat),
      raw: data // include full USDA payload if you want; remove if not needed
    });
  } catch (err) {
    const status = err.response?.status || 500;
    return res.status(status).json({
      error: "USDA detail failed",
      details: err.message,
      usda: err.response?.data || null
    });
  }
});

// GET /api/barcode/:code  → Open Food Facts
app.get("/api/barcode/:code", async (req, res) => {
  try {
    const code = (req.params.code || "").trim();
    if (!code) return res.status(400).json({ error: "Missing barcode" });

    const { data } = await axios.get(
      `https://world.openfoodfacts.org/api/v2/product/${code}`,
      {
        headers: { "User-Agent": "FoodieMacroTracker/1.0 (+https://example.com)" },
        timeout: 10000
      }
    );

    if (data.status !== 1 || !data.product) {
      return res.status(404).json({ error: "Product not found" });
    }

    const p = data.product;
    const n = p.nutriments || {};
    const pick = (serv, per100) => (n[serv] ?? n[per100] ?? null);

    // kcal first, then macros (prefer per serving; fallback to per 100g)
    const result = {
      code,
      description: p.product_name || null,
      brand: p.brands || null,
      servingSize: p.serving_size || null,
      servingSizeUnit: null, // OFF serving_size is a string (e.g., "30 g")
      calories: pick("energy-kcal_serving", "energy-kcal_100g"),
      protein:  pick("proteins_serving", "proteins_100g"),
      carbs:    pick("carbohydrates_serving", "carbohydrates_100g"),
      fat:      pick("fat_serving", "fat_100g")
    };

    res.json(result);
  } catch (err) {
    res.status(err.response?.status || 500).json({
      error: "OFF proxy failed",
      details: err.message,
      off: err.response?.data || null
    });
  }
});


const port = process.env.PORT || 8080;
app.listen(port, () => console.log(`USDA proxy listening on http://localhost:${port}`));
