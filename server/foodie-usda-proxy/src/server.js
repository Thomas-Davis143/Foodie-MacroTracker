import express from "express";
import cors from "cors";
import helmet from "helmet";
import rateLimit from "express-rate-limit";
import axios from "axios";
import "dotenv/config";

const app = express();

/* ======================  Normalization helpers  ====================== */

// USDA nutrient IDs (per 100 g)
const N_IDS = { kcal: 1008, protein: 1003, carbs: 1005, fat: 1004 };

// rounders
const r1 = (x) => (x == null ? null : Math.round(x * 10) / 10);
const r0 = (x) => (x == null ? null : Math.round(x));

// Build a clean "measures" list from USDA foodPortions
function extractMeasures(food) {
  const portions = food.foodPortions || [];
  const seen = new Set();
  const measures = [];

  for (const p of portions) {
    const pieces = [];
    if (p.amount) pieces.push(String(p.amount));           // "1"
    if (p.modifier) pieces.push(p.modifier);               // "chopped"
    if (p.measureUnit?.name) pieces.push(p.measureUnit.name); // "cup"
    if (!p.measureUnit && p.portionDescription) pieces.push(p.portionDescription);

    let label = pieces.join(" ").trim();
    if (!label) label = p.portionDescription || "serving";

    const grams = p.gramWeight;
    if (!grams || grams <= 0) continue;

    const key = `${label}|${grams}`;
    if (seen.has(key)) continue;
    seen.add(key);
    measures.push({ label, gramsPerUnit: grams });
  }
  return measures;
}

// Extract macros per 100 g from USDA foodNutrients (works for both detail & search payloads)
function per100gFromFoodNutrients(foodNutrients) {
  const map = {};
  for (const fn of foodNutrients || []) {
    // search payload uses { nutrientId, value }; detail uses { nutrient: { id }, amount }
    const id = fn.nutrient?.id ?? fn.nutrientId;
    const amt = fn.amount ?? fn.value;
    if (id === N_IDS.kcal) map.calories = amt;
    if (id === N_IDS.protein) map.protein = amt;
    if (id === N_IDS.carbs) map.carbs = amt;
    if (id === N_IDS.fat) map.fat = amt;
  }
  return map;
}

// Extract per-serving (branded) from labelNutrients
function perServingFromLabel(labelNutrients) {
  if (!labelNutrients) return {};
  const g = (n) => (labelNutrients[n] && labelNutrients[n].value) ?? null;
  return {
    calories: g("calories"),
    protein: g("protein"),
    carbs: g("carbohydrates"),
    fat: g("fat"),
  };
}

// Normalize one USDA food object into a unified shape
function normalizeFood(food) {
  const dataType = food.dataType;
  const base = {
    source: "USDA",
    fdcId: food.fdcId,
    description: food.description || food.brandName || "",
    brandName: food.brandName || food.brandOwner || null,
    dataType,
  };

  const units = [
    { label: "gram (g)", gramsPerUnit: 1 },
    { label: "ounce (oz)", gramsPerUnit: 28.3495 },
  ];

  // Add USDA household measures if present (detail payloads)
  const measures = extractMeasures(food);
  for (const m of measures) units.push(m);

  let per100g = {};
  let perServing = {};
  let servingGrams = null;

  if (dataType === "Branded") {
    // Branded: labelNutrients are per serving
    perServing = perServingFromLabel(food.labelNutrients);

    // Serving grams, if USDA declares serving in grams
    if (typeof food.servingSize === "number") {
      const u = (food.servingSizeUnit || "").toLowerCase();
      if (u === "g" || u === "gram" || u === "grams") servingGrams = food.servingSize;
    }

    // Compute per 100 g if we know grams, else fallback to foodNutrients if present
    if (servingGrams && servingGrams > 0) {
      const k = 100 / servingGrams;
      per100g = {
        calories: perServing.calories != null ? perServing.calories * k : null,
        protein: perServing.protein != null ? perServing.protein * k : null,
        carbs: perServing.carbs != null ? perServing.carbs * k : null,
        fat: perServing.fat != null ? perServing.fat * k : null,
      };
    } else if (food.foodNutrients) {
      per100g = per100gFromFoodNutrients(food.foodNutrients);
    }
  } else {
    // Foundation/Survey/SR Legacy usually provide per 100 g
    per100g = per100gFromFoodNutrients(food.foodNutrients);

    // If we have a portion with gramWeight, derive a perServing
    if (measures.length) {
      servingGrams = measures[0].gramsPerUnit;
      perServing = {
        calories: per100g.calories != null ? per100g.calories * (servingGrams / 100) : null,
        protein: per100g.protein != null ? per100g.protein * (servingGrams / 100) : null,
        carbs: per100g.carbs != null ? per100g.carbs * (servingGrams / 100) : null,
        fat: per100g.fat != null ? per100g.fat * (servingGrams / 100) : null,
      };
    }
  }

  if (servingGrams && servingGrams > 0) {
    units.push({ label: "serving", gramsPerUnit: servingGrams });
  }

  return {
    ...base,
    servings: {
      per100g: {
        calories: r0(per100g.calories),
        protein: r1(per100g.protein),
        carbs: r1(per100g.carbs),
        fat: r1(per100g.fat),
      },
      perServing: servingGrams
        ? {
            grams: servingGrams,
            calories: r0(perServing.calories),
            protein: r1(perServing.protein),
            carbs: r1(perServing.carbs),
            fat: r1(perServing.fat),
          }
        : null,
    },
    units, // [{label, gramsPerUnit}]
  };
}

/* ---------- OFF normalization (best effort) so client sees same shape ---------- */

function parseOFFServingGrams(p) {
  // prefer structured fields
  const qty = parseFloat(p.serving_quantity);
  const unit = (p.serving_size_unit || "").toLowerCase();
  if (!Number.isNaN(qty)) {
    if (unit === "g" || unit === "gram" || unit === "grams") return qty;
    if (unit === "oz" || unit === "ounce" || unit === "ounces") return qty * 28.3495;
    // ml not convertible without density → skip
  }
  // fallback: parse "30 g", "1 oz" from serving_size string
  if (p.serving_size) {
    const m = String(p.serving_size).match(/([\d.]+)\s*(g|gram|grams|oz|ounce|ounces)\b/i);
    if (m) {
      const val = parseFloat(m[1]);
      const u = m[2].toLowerCase();
      if (["g", "gram", "grams"].includes(u)) return val;
      if (["oz", "ounce", "ounces"].includes(u)) return val * 28.3495;
    }
  }
  return null;
}

function normalizeOFF(product) {
  const n = product.nutriments || {};
  const pick = (serv, per100) => (n[serv] ?? n[per100] ?? null);

  const perServ = {
    calories: pick("energy-kcal_serving", "energy-kcal_100g"),
    protein: pick("proteins_serving", "proteins_100g"),
    carbs: pick("carbohydrates_serving", "carbohydrates_100g"),
    fat: pick("fat_serving", "fat_100g"),
  };

  const per100 = {
    calories: n["energy-kcal_100g"] ?? null,
    protein: n["proteins_100g"] ?? null,
    carbs: n["carbohydrates_100g"] ?? null,
    fat: n["fat_100g"] ?? null,
  };

  let per100g = { ...per100 };
  let perServing = { ...perServ };
  const servingGrams = parseOFFServingGrams(product);

  // If we have per-serving values and grams, compute per100g
  if (servingGrams && (perServ.calories || perServ.protein || perServ.carbs || perServ.fat)) {
    const k = 100 / servingGrams;
    per100g = {
      calories: perServ.calories != null ? perServ.calories * k : per100.calories,
      protein: perServ.protein != null ? perServ.protein * k : per100.protein,
      carbs: perServ.carbs != null ? perServ.carbs * k : per100.carbs,
      fat: perServ.fat != null ? perServ.fat * k : per100.fat,
    };
  }

  const units = [
    { label: "gram (g)", gramsPerUnit: 1 },
    { label: "ounce (oz)", gramsPerUnit: 28.3495 },
  ];
  if (servingGrams) units.push({ label: "serving", gramsPerUnit: servingGrams });

  return {
    source: "OFF",
    code: product.code || null,
    description: product.product_name || product.generic_name || "",
    brandName: product.brands || null,
    servings: {
      per100g: {
        calories: r0(per100g.calories),
        protein: r1(per100g.protein),
        carbs: r1(per100g.carbs),
        fat: r1(per100g.fat),
      },
      perServing: servingGrams
        ? {
            grams: servingGrams,
            calories: r0(perServing.calories),
            protein: r1(perServing.protein),
            carbs: r1(perServing.carbs),
            fat: r1(perServing.fat),
          }
        : null,
    },
    units,
  };
}

/* ======================  Security / basics  ====================== */

const ALLOWED = (process.env.ALLOWED_ORIGINS || "")
  .split(",")
  .map((s) => s.trim())
  .filter(Boolean);

app.use(
  cors({
    origin: (origin, cb) => {
      if (!origin || ALLOWED.length === 0 || ALLOWED.includes(origin)) return cb(null, true);
      return cb(new Error("Not allowed by CORS"));
    },
  })
);
app.use(helmet());
app.use(express.json());
app.use(rateLimit({ windowMs: 60_000, max: 60 }));

app.get("/health", (_req, res) => res.json({ ok: true }));

/* ======================  USDA: Search  ====================== */

app.get("/api/foods/search", async (req, res) => {
  try {
    const q = String(req.query.q || "").trim();
    if (!q) return res.status(400).json({ error: "Missing query ?q=" });

    const pageSize = Math.min(Math.max(parseInt(req.query.pageSize || "25", 10), 1), 200);
    const pageNumber = Math.max(parseInt(req.query.pageNumber || "1", 10), 1);

    const params = new URLSearchParams();
    params.append("api_key", process.env.USDA_FDC_API_KEY || "");
    params.append("query", q);
    params.append("pageSize", String(pageSize));
    params.append("pageNumber", String(pageNumber));
    ["Branded", "Survey (FNDDS)", "SR Legacy", "Foundation"].forEach((v) =>
      params.append("dataType", v)
    );
    params.append("requireAllWords", "false");

    const { data } = await axios.get("https://api.nal.usda.gov/fdc/v1/foods/search", {
      params,
      paramsSerializer: (p) => p.toString(),
      timeout: 10_000,
      headers: { Accept: "application/json" },
    });

    const items = (data.foods || []).map((f) => normalizeFood(f));
    res.json({ totalHits: data.totalHits, pageNumber: data.currentPage, items });
  } catch (err) {
    const status = err.response?.status || 500;
    res.status(status).json({
      error: "USDA proxy failed",
      details: err.message,
      usda: err.response?.data || null,
    });
  }
});

/* ======================  USDA: Details  ====================== */

app.get("/api/foods/:fdcId", async (req, res) => {
  try {
    const id = req.params.fdcId;
    if (!id) return res.status(400).json({ error: "Missing fdcId" });

    const params = new URLSearchParams();
    params.append("api_key", process.env.USDA_FDC_API_KEY || "");

    const { data } = await axios.get(`https://api.nal.usda.gov/fdc/v1/food/${id}`, {
      params,
      paramsSerializer: (p) => p.toString(),
      timeout: 10_000,
      headers: { Accept: "application/json" },
    });

    const item = normalizeFood(data);
    res.json({ item });
  } catch (err) {
    const status = err.response?.status || 500;
    res.status(status).json({
      error: "USDA detail failed",
      details: err.message,
      usda: err.response?.data || null,
    });
  }
});

/* ======================  Open Food Facts: Barcode  ====================== */

app.get("/api/barcode/:code", async (req, res) => {
  try {
    const code = String(req.params.code || "").trim();
    if (!code) return res.status(400).json({ error: "Missing barcode" });

    const { data } = await axios.get(
      `https://world.openfoodfacts.org/api/v2/product/${encodeURIComponent(code)}`,
      {
        headers: { "User-Agent": "FoodieMacroTracker/1.0 (+https://example.com)" },
        timeout: 10_000,
      }
    );

    if (data.status !== 1 || !data.product) {
      return res.status(404).json({ error: "Product not found" });
    }

    const item = normalizeOFF(data.product);
    res.json({ item });
  } catch (err) {
    res.status(err.response?.status || 500).json({
      error: "OFF proxy failed",
      details: err.message,
      off: err.response?.data || null,
    });
  }
});

/* ======================  Startup  ====================== */

const port = process.env.PORT || 8080;
app.listen(port, () => console.log(`USDA proxy listening on http://localhost:${port}`));
