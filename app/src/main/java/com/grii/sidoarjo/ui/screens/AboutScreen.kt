package com.grii.sidoarjo.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.grii.sidoarjo.R
import com.grii.sidoarjo.data.local.PortalSessionStore
import com.grii.sidoarjo.data.model.PortalUserSession
import com.grii.sidoarjo.data.repository.PortalRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    openLoginOnStart: Boolean = false,
    onLoginSuccess: () -> Unit = {},
    onOpenRegistration: () -> Unit,
    liveStreamState: com.grii.sidoarjo.data.model.LiveStreamState = com.grii.sidoarjo.data.model.LiveStreamState(),
    onUpdateLiveStream: (Boolean, String, (Result<Unit>) -> Unit) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val portalSessionStore = remember { PortalSessionStore(context) }
    val portalRepository = remember { PortalRepository() }
    val portalSession by portalSessionStore.sessionFlow.collectAsState(initial = null)
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    
    val authErrors = remember { mutableStateMapOf<String, String>() }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loginMessage by remember { mutableStateOf<String?>(null) }
    var loginMessageIsError by remember { mutableStateOf(false) }
    var isSubmittingPortalAuth by remember { mutableStateOf(false) }
    var showHubLoginDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var showCredentialDialog by remember { mutableStateOf(false) }
    var newUsername by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var credentialMessage by remember { mutableStateOf<String?>(null) }

    val isHubActive = portalSession?.status.equals("ACTIVE", ignoreCase = true)

    LaunchedEffect(openLoginOnStart, isHubActive) {
        if (openLoginOnStart && !isHubActive) showHubLoginDialog = true
        else if (openLoginOnStart && isHubActive) onLoginSuccess()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                AboutHeader()
            }

            item {
                SectionHeaderTitle("Service Guide")
                Spacer(Modifier.height(12.dp))
                GuidelineItem(Icons.Default.CheckCircle, "Theology & Discipleship", "Content supports gospel proclamation, biblical reformed teaching, and congregation faith growth.")
                GuidelineItem(Icons.Default.Lock, "Copyright & Originality", "Does not violate copyright of music, videos, praise songs, or third-party content.")
                GuidelineItem(Icons.Default.Done, "Publication License", "Provides official broadcasting license for the digital ministry of GRII Sidoarjo TV.")
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            "We are committed to presenting reformed Christian spiritual content faithful to the Bible for the glory of God.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            if (isHubActive) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Admin Panel",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text = "Status: Authenticated (${portalSession?.username})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Button(
                                onClick = onOpenRegistration,
                                modifier = Modifier.fillMaxWidth(),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                 Spacer(Modifier.width(8.dp))
                                 Text("Upload Video", fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                 Button(
                                     onClick = {
                                         newUsername = portalSession?.username.orEmpty()
                                         newPassword = ""
                                         showCredentialDialog = true
                                     },
                                     modifier = Modifier.weight(1f),
                                     border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary),
                                     colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                 ) {
                                     Text("Edit Account")
                                 }
                                 Button(
                                     onClick = { showLogoutConfirmDialog = true },
                                     modifier = Modifier.weight(1f),
                                     colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                 ) {
                                     Text("Log Out")
                                 }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHubLoginDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSubmittingPortalAuth) {
                    showHubLoginDialog = false
                    authErrors.clear()
                    loginMessage = null
                    loginMessageIsError = false
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
             title = { Text("Admin Login") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Log in using admin username and password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            authErrors.remove("login_username")
                        },
                        label = { Text("Username") },
                        isError = authErrors["login_username"] != null,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.onPrimary,
                            focusedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        supportingText = {
                            authErrors["login_username"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            authErrors.remove("login_password")
                        },
                        label = { Text("Password") },
                        isError = authErrors["login_password"] != null,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.onPrimary,
                            focusedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        supportingText = {
                            authErrors["login_password"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    )
                    loginMessage?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (loginMessageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSubmittingPortalAuth,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary),
                    onClick = {
                        authErrors.clear()
                        loginMessage = null
                    loginMessageIsError = false
                        
                        isSubmittingPortalAuth = true
                        scope.launch {
                            portalRepository.loginAdmin(username.trim(), password).onSuccess { session ->
                                 portalSessionStore.saveSession(session)
                                 loginMessage = "Login successful."
                                 loginMessageIsError = false
                                 showHubLoginDialog = false
                                 username = ""
                                 password = ""
                                 authErrors.clear()
                                 credentialMessage = null
                                 onLoginSuccess()
                             }.onFailure { error ->
                                 loginMessage = error.message ?: "Failed to login."
                                 loginMessageIsError = true
                             }
                             isSubmittingPortalAuth = false
                        }
                    }
                ) {
                    Text(if (isSubmittingPortalAuth) "Logging in..." else "Login")
                }
            },
            dismissButton = {
                    TextButton(
                        enabled = !isSubmittingPortalAuth,
                        onClick = {
                            showHubLoginDialog = false
                            authErrors.clear()
                            loginMessage = null
                            loginMessageIsError = false
                            username = ""
                            password = ""
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        Text("Cancel")
                    }
            }
        )
    }

    
    if (showCredentialDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSubmittingPortalAuth) {
                    showCredentialDialog = false
                    credentialMessage = null
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
             title = { Text("Edit Admin Account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newUsername,
                        onValueChange = {
                            newUsername = it
                            credentialMessage = null
                        },
                        label = { Text("New Username") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.onPrimary,
                            focusedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        enabled = !isSubmittingPortalAuth
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = {
                            newPassword = it
                            credentialMessage = null
                        },
                        label = { Text("New Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.onPrimary,
                            focusedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        enabled = !isSubmittingPortalAuth
                    )
                    credentialMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSubmittingPortalAuth,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary),
                    onClick = {
                        val session = portalSession
                         if (session == null || !isHubActive) {
                             credentialMessage = "Admin login required."
                             return@Button
                         }
                        

                        isSubmittingPortalAuth = true
                        scope.launch {
                            portalRepository.updateAdminCredentials(
                                currentUsername = session.username,
                                newUsername = newUsername.trim(),
                                newPassword = newPassword
                            ).onSuccess { updatedSession ->
                                portalSessionStore.saveSession(updatedSession)
                                showCredentialDialog = false
                                credentialMessage = null
                            }.onFailure { error ->
                                 credentialMessage = error.message ?: "Failed to update admin account."
                             }
                             isSubmittingPortalAuth = false
                         }
                     }
                 ) {
                     Text(if (isSubmittingPortalAuth) "Saving..." else "Submit")
                }
            },
            dismissButton = {
                  TextButton(
                      enabled = !isSubmittingPortalAuth,
                      onClick = {
                          showCredentialDialog = false
                          credentialMessage = null
                      },
                      colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                  ) {
                      Text("Cancel")
                  }
            }
        )
    }
    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSubmittingPortalAuth) {
                    showLogoutConfirmDialog = false
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
             title = { Text("Log Out") },
            text = { Text("Are you sure you want to log out from the admin account?") },
            confirmButton = {
                 Button(
                    enabled = !isSubmittingPortalAuth,
                    onClick = {
                        portalSessionStore.clearSession()
                        showLogoutConfirmDialog = false
                        loginMessage = null
                    loginMessageIsError = false
                        authErrors.clear()
                        username = ""
                        password = ""
                    }
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSubmittingPortalAuth,
                    onClick = { showLogoutConfirmDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AboutHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.height(16.dp))
         Text(
            "About Us",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Reformed Evangelical Church of Indonesia (GRII) Sidoarjo is part of the GRII synod founded by Rev. Dr. Stephen Tong. Holding fast to Reformed theology and the Evangelical spirit, GRII Sidoarjo is committed to proclaiming the Gospel of Jesus Christ, nurturing congregation faith in the truth of God's Word, and being a witness of Christ in the community.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val context = LocalContext.current
            val openUrl = { url: String ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // ignore
                }
            }

            // Instagram
            IconButton(
                onClick = { openUrl("https://www.instagram.com/griisidoarjo") },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_instagram),
                    contentDescription = "Instagram",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // YouTube
            IconButton(
                onClick = { openUrl("https://www.youtube.com/@griisidoarjo8020") },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_youtube),
                    contentDescription = "YouTube",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // TikTok
            IconButton(
                onClick = { openUrl("https://www.tiktok.com/@griisidoarjo") },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_tiktok),
                    contentDescription = "TikTok",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Google Maps
            IconButton(
                onClick = { openUrl("https://maps.app.goo.gl/LQFHZrmkpjTkDVjU7") },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = "Google Maps",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // WhatsApp
            IconButton(
                onClick = { openUrl("http://wa.me/+620811323345") },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_whatsapp),
                    contentDescription = "WhatsApp",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun GuidelineItem(icon: ImageVector, title: String, desc: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeaderTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
}











