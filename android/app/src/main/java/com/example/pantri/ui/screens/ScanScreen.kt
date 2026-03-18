package com.example.pantri.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.pantri.ui.theme.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pantri.api.ApiClient
import com.example.pantri.api.FoodInfo
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch

@Composable
fun ScanScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf(0) }
    var protein by remember { mutableStateOf(0.0) }
    var carbs by remember { mutableStateOf(0.0) }
    var fat by remember { mutableStateOf(0.0) }
    var pkg by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var ean by remember { mutableStateOf("") }
    var showResult by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    var notFound by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    fun scan() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E
            )
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode ->
                val raw = barcode.rawValue ?: return@addOnSuccessListener
                ean = raw
                loading = true
                showResult = false
                showSuccess = false
                notFound = false
                scope.launch {
                    val resp = ApiClient.lookupBarcode(raw)
                    loading = false
                    if (resp != null && resp.status == 1 && resp.product != null) {
                        val p = resp.product
                        val n = p.nutriments
                        name = (p.product_name ?: "unknown")
                            .lowercase().replace(Regex("\\s+"), "-")
                        kcal = Math.round(n?.kcal100g ?: 0.0).toInt()
                        protein = Math.round((n?.proteins_100g ?: 0.0) * 10) / 10.0
                        carbs = Math.round((n?.carbohydrates_100g ?: 0.0) * 10) / 10.0
                        fat = Math.round((n?.fat_100g ?: 0.0) * 10) / 10.0
                        pkg = p.quantity ?: ""
                        price = ""
                        showResult = true
                    } else {
                        notFound = true
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Scan failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Scan Barcode",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Button(
            onClick = { scan() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CalGreen)
        ) {
            Text("Open Scanner", fontSize = 16.sp)
        }

        if (loading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SpinningAppIcon()
                    Spacer(Modifier.height(8.dp))
                    Text("Looking up product...", color = Color.Gray)
                }
            }
        }

        if (notFound) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Product not found", style = MaterialTheme.typography.titleMedium)
                    Text("EAN: $ean", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { scan() }) { Text("Try Again") }
                }
            }
        }

        if (showResult) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("EAN: $ean", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MacroChip("kcal", "$kcal", CalGreen)
                        MacroChip("Protein", "${protein}g", ProteinBlue)
                        MacroChip("Carbs", "${carbs}g", CarbsAmber)
                        MacroChip("Fat", "${fat}g", FatRed)
                    }
                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = pkg,
                            onValueChange = { pkg = it },
                            label = { Text("Package") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = price,
                            onValueChange = { price = it },
                            label = { Text("Price (EUR)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                    Spacer(Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                saving = true
                                scope.launch {
                                    try {
                                        val foods = ApiClient.loadFoods().toMutableMap()
                                        val m = Regex("([\\d.]+)\\s*(g|kg|ml|l)", RegexOption.IGNORE_CASE).find(pkg)
                                        var wg = 0.0
                                        if (m != null) {
                                            wg = m.groupValues[1].toDouble()
                                            if (m.groupValues[2].lowercase() in listOf("kg", "l")) wg *= 1000
                                        }
                                        val priceVal = price.toDoubleOrNull() ?: 0.0
                                        val costPer100 = if (wg > 0 && priceVal > 0)
                                            Math.round(priceVal / wg * 100 * 100.0) / 100.0 else 0.0
                                        val centPerGP = if (protein > 0 && costPer100 > 0)
                                            Math.round(costPer100 / protein * 100 * 100.0) / 100.0 else null

                                        foods[name] = FoodInfo(
                                            kcal = kcal,
                                            protein_g = protein,
                                            carbs_g = carbs,
                                            fat_g = fat,
                                            cost_eur_per_100g = costPer100,
                                            cent_per_g_protein = centPerGP
                                        )
                                        ApiClient.saveFoods(foods)
                                        showResult = false
                                        showSuccess = true
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !saving
                        ) {
                            Text(if (saving) "Saving..." else "Add to Foods")
                        }
                        OutlinedButton(
                            onClick = { scan() },
                            modifier = Modifier.weight(1f)
                        ) { Text("Scan Again") }
                    }
                }
            }
        }

        if (showSuccess) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "\u2713",
                        fontSize = 32.sp,
                        color = CalGreen
                    )
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$kcal kcal | ${protein}P ${carbs}C ${fat}F",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { scan() },
                        colors = ButtonDefaults.buttonColors(containerColor = CalGreen)
                    ) {
                        Text("Scan Another")
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MacroChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = Color.Gray)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
