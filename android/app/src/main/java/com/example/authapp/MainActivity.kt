package com.example.authapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import kotlinx.coroutines.launch

// --- Models ---
data class AuthRequest(val id_token: String, val email: String)
data class AuthResponse(
    val status: String,
    val email: String,
    val is_verified: Boolean,
    val has_secret: Boolean
)
data class TotpGenerateResponse(val secret: String, val otpauth_url: String)
data class VerifyRequest(val email: String, val code: String)
data class VerifyResponse(val status: String)

// --- API ---
interface ApiService {
    @POST("/auth/google")
    suspend fun googleAuth(@Body req: AuthRequest): AuthResponse

    @POST("/totp/generate")
    suspend fun generateTotp(@Query("email") email: String): TotpGenerateResponse

    @POST("/totp/verify")
    suspend fun verifyTotp(@Body req: VerifyRequest): VerifyResponse
}

object RetrofitClient {
    // Cloudflare Tunnel URL for remote access
    private const val BASE_URL = "https://imagine-undertake-era-armstrong.trycloudflare.com/" 

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

// --- UI ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AuthScreen()
            }
        }
    }
}

@Composable
fun AuthScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // State
    var currentUserEmail by remember { mutableStateOf<String?>(null) }
    var authStep by remember { mutableStateOf(0) }
    var totpSecret by remember { mutableStateOf<String?>(null) }
    var verifyCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    // Debug Log State
    var debugLog by remember { mutableStateOf("Ready to start...\n") }

    fun log(msg: String) {
        debugLog = "$msg\n$debugLog"
    }

    // Google Sign In Launcher
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            val email = account.email
            
            log("Google Auth Success: $email")
            
            if (idToken != null && email != null) {
                isLoading = true
                scope.launch {
                    try {
                        log("Sending token to backend...")
                        val response = RetrofitClient.api.googleAuth(AuthRequest(idToken, email))
                        log("Backend Response: ${response.status}")
                        currentUserEmail = response.email
                        if (response.is_verified) {
                            authStep = 2 
                        } else {
                            if (response.has_secret) {
                                authStep = 1
                            } else {
                                log("Generating TOTP...")
                                val genRes = RetrofitClient.api.generateTotp(response.email)
                                totpSecret = genRes.secret
                                authStep = 1
                            }
                        }
                    } catch (e: Exception) {
                        log("Backend Error: ${e.message}")
                        e.printStackTrace()
                    } finally {
                        isLoading = false
                    }
                }
            } else {
                log("Error: ID Token or Email is null")
            }
        } catch (e: ApiException) {
            log("Google Sign In Failed. Code: ${e.statusCode}")
            log("Message: ${e.message}")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) CircularProgressIndicator()
        
        Spacer(modifier = Modifier.height(16.dp))

        // Debug Log Box
        Card(
            modifier = Modifier.fillMaxWidth().height(150.dp).padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                text = debugLog,
                modifier = Modifier.padding(8.dp).verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }

        when (authStep) {
            0 -> {
                Button(onClick = {
                    try {
                        log("Starting Google Sign In...")
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken("647939797569-momk8km6q0ghc6a5tce42ajp5mpk76ml.apps.googleusercontent.com") 
                            .requestEmail()
                            .build()
                        val client = GoogleSignIn.getClient(context, gso)
                        // Force Sign Out first to ensure account picker appears every time
                        client.signOut().addOnCompleteListener {
                            launcher.launch(client.signInIntent)
                        }
                    } catch (e: Exception) {
                        log("Launcher Error: ${e.message}")
                    }
                }) {
                    Text("Google ile Giriş Yap")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Test Connection Button
                Button(onClick = {
                    scope.launch {
                        try {
                            log("Testing connection to: ${RetrofitClient.api.toString()}")
                            // We can't easily call root /, but let's try a fake auth to see if it reaches server
                            log("Pinging server...")
                             // Just a dummy call to check connectivity, expected 403 or similar but shows reachable
                            try {
                                RetrofitClient.api.googleAuth(AuthRequest("test", "test"))
                            } catch (e: retrofit2.HttpException) {
                                log("Connection OK! Server returned: ${e.code()}")
                            } catch (e: Exception) {
                                log("Connection Failed: ${e.message}")
                            }
                        } catch (e: Exception) {
                            log("Network Error: ${e.message}")
                        }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                    Text("Bağlantıyı Test Et (Authsuz)")
                }
            }
            1 -> {
                Text("Hoşgeldin, $currentUserEmail")
                if (totpSecret != null) {
                    Text("Seed: $totpSecret", style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = {
                         val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                         val clip = ClipData.newPlainText("TOTP Secret", totpSecret)
                         clipboard.setPrimaryClip(clip)
                         log("Seed kopyalando")
                    }) { Text("Kopyala") }
                }
                
                TextField(value = verifyCode, onValueChange = { verifyCode = it }, label = { Text("6 Haneli Kod") })
                Button(onClick = {
                    scope.launch {
                        try {
                            log("Verifying code...")
                            val res = RetrofitClient.api.verifyTotp(VerifyRequest(currentUserEmail!!, verifyCode))
                            if (res.status == "verified") authStep = 2
                        } catch (e: Exception) {
                            log("Verify Failed: ${e.message}")
                        }
                    }
                }) { Text("Doğrula") }
            }
            2 -> {
                Text("Başarılı! ✅", style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}
