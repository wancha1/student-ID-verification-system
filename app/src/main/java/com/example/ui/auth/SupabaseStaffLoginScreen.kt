package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.auth.SupabaseClientFactory
import com.example.data.auth.SupabaseStaffAuthenticator
import com.example.model.UserRole
import kotlinx.coroutines.launch

/**
 * Production staff login. Role and active status are read from the authenticated
 * user's protected staff_memberships row; the UI never chooses a role.
 */
@Composable
fun SupabaseStaffLoginScreen(
    onAuthenticated: (UserRole, String?, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // A missing build-time configuration must not crash the entire application.
    // Keep the login surface available and show an actionable configuration error.
    val authenticatorResult = remember(context) {
        runCatching {
            SupabaseStaffAuthenticator(SupabaseClientFactory.getInstance(context.applicationContext))
        }
    }
    val authenticator = authenticatorResult.getOrNull()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember {
        mutableStateOf(
            if (authenticator == null) {
                "This build is not configured for Supabase. Contact the app administrator."
            } else null
        )
    }

    val midnight = Color(0xFF071321)
    val deepBlue = Color(0xFF102941)
    val champagne = Color(0xFFE8C77A)
    val mutedText = Color(0xFF627286)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(midnight, deepBlue, Color(0xFF091522))
                )
            )
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(14.dp))
            Image(
                painter = painterResource(id = R.drawable.ltc_school_crest),
                contentDescription = "Lira Town College crest",
                modifier = Modifier.size(92.dp)
            )
            Spacer(Modifier.height(22.dp))
            Text(
                text = "LIRA TOWN COLLEGE",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Every student.\nAccounted for.",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "A secure identity and access workspace for the people who keep LTC moving.",
                color = Color.White.copy(alpha = 0.76f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.fillMaxWidth(0.88f)
            )
            Spacer(Modifier.height(28.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.78f)), RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFCFDFE)),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "STAFF PORTAL",
                                color = Color(0xFF9A742D),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.sp
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "Welcome back",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF142236)
                            )
                            Text(
                                "Sign in with your school-issued account.",
                                style = MaterialTheme.typography.bodySmall,
                                color = mutedText
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFE8F4EE),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Secure sign in",
                                    tint = Color(0xFF23724C),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; errorMessage = null },
                        label = { Text("Staff email") },
                        placeholder = { Text("you@ltc.ac.ug") },
                        singleLine = true,
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(15.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF172536),
                            unfocusedTextColor = Color(0xFF172536),
                            disabledTextColor = Color(0xFF667085),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            disabledContainerColor = Color(0xFFF5F7FA),
                            focusedBorderColor = Color(0xFF9A742D),
                            unfocusedBorderColor = Color(0xFF8795A5),
                            disabledBorderColor = Color(0xFFCBD5E1),
                            focusedLabelColor = Color(0xFF8D6827),
                            unfocusedLabelColor = Color(0xFF526273),
                            disabledLabelColor = Color(0xFF667085),
                            focusedPlaceholderColor = Color(0xFF6B7785),
                            unfocusedPlaceholderColor = Color(0xFF6B7785),
                            disabledPlaceholderColor = Color(0xFF98A2B3),
                            cursorColor = Color(0xFF8D6827)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = null },
                        label = { Text("Password") },
                        singleLine = true,
                        enabled = !isLoading,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                enabled = !isLoading
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = Color(0xFF627286)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        shape = RoundedCornerShape(15.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF172536),
                            unfocusedTextColor = Color(0xFF172536),
                            disabledTextColor = Color(0xFF667085),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            disabledContainerColor = Color(0xFFF5F7FA),
                            focusedBorderColor = Color(0xFF9A742D),
                            unfocusedBorderColor = Color(0xFF8795A5),
                            disabledBorderColor = Color(0xFFCBD5E1),
                            focusedLabelColor = Color(0xFF8D6827),
                            unfocusedLabelColor = Color(0xFF526273),
                            disabledLabelColor = Color(0xFF667085),
                            focusedPlaceholderColor = Color(0xFF6B7785),
                            unfocusedPlaceholderColor = Color(0xFF6B7785),
                            disabledPlaceholderColor = Color(0xFF98A2B3),
                            cursorColor = Color(0xFF8D6827)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMessage != null) {
                        Surface(
                            color = Color(0xFFFFF0F0),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFF1C6C6))
                        ) {
                            Text(
                                text = errorMessage.orEmpty(),
                                color = Color(0xFF9B2C2C),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                try {
                                    val staff = requireNotNull(authenticator) {
                                        "Supabase is not configured for this build."
                                    }.signIn(email, password)
                                    password = ""
                                    onAuthenticated(
                                        staff.role,
                                        staff.displayName,
                                        staff.hasExplicitFinanceAccess
                                    )
                                } catch (error: Exception) {
                                    val message = error.message.orEmpty()
                                    errorMessage = when {
                                        message.contains("not provisioned", ignoreCase = true) ->
                                            "Your account is not registered as active Lira Town College staff. Ask the administrator to verify your staff profile."
                                        message.contains("inactive", ignoreCase = true) ->
                                            "This LTC staff account is inactive. Ask the administrator to review it."
                                        message.contains("unsupported role", ignoreCase = true) ->
                                            "Your LTC staff profile has an unsupported role. Ask the administrator to correct it."
                                        message.contains("Invalid login", ignoreCase = true) ||
                                            message.contains("invalid_credentials", ignoreCase = true) ||
                                            message.contains("invalid email or password", ignoreCase = true) ->
                                            "Email or password isn't correct. Check both and try again."
                                        message.contains("Enter your staff email", ignoreCase = true) ->
                                            "Enter your staff email address."
                                        message.contains("Enter your password", ignoreCase = true) ->
                                            "Enter your password."
                                        message.contains("not configured", ignoreCase = true) ||
                                            message.contains("Supabase URL", ignoreCase = true) ->
                                            "This build is not configured for Supabase. Contact the app administrator."
                                        else ->
                                            "Sign-in or staff verification couldn't be completed. Check your connection and try again."
                                    }
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = authenticator != null && !isLoading && email.isNotBlank() && password.isNotBlank(),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF102941),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFFB8C2CC),
                            disabledContentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(19.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Verifying account…", fontWeight = FontWeight.SemiBold)
                        } else {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(9.dp))
                            Text("Sign in securely", fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(color = Color(0xFFE8F4EE), shape = CircleShape, modifier = Modifier.size(7.dp)) {}
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "Role permissions verified by LTC",
                            color = mutedText,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "LTC AT THE CENTRE  •  STUDENT ACCESS SYSTEM",
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
