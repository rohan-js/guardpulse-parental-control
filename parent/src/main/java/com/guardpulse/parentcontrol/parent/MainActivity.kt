package com.guardpulse.parentcontrol.parent

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import com.guardpulse.parentcontrol.shared.FirebaseBootstrap
import com.guardpulse.parentcontrol.shared.FirebasePaths
import com.guardpulse.parentcontrol.shared.PackageKeys
import com.guardpulse.parentcontrol.shared.PolicyConstants
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ListenerRegistration(
    val query: Query,
    val listener: ValueEventListener
)

class MainActivity : ComponentActivity() {
    private var db: DatabaseReference? = null
    private var repository: ParentRepository? = null
    private var devices by mutableStateOf(emptyList<ParentDevice>())
    private var apps by mutableStateOf(emptyMap<String, ParentApp>())
    private var policies by mutableStateOf(emptyMap<String, ParentPolicy>())
    private var states by mutableStateOf(emptyMap<String, ParentState>())
    private var security by mutableStateOf(SecurityRuntime())
    private var unlockRequests by mutableStateOf(emptyList<UnlockRequest>())
    private var tamperEvents by mutableStateOf(emptyList<TamperEvent>())
    private var selectedDeviceId by mutableStateOf<String?>(null)
    private var message by mutableStateOf<String?>(null)
    private var signedIn by mutableStateOf(false)
    private var authBusy by mutableStateOf(false)
    private var loadingDevices by mutableStateOf(false)
    private var loadingDeviceDetails by mutableStateOf(false)
    private var deviceListRegistration: ListenerRegistration? = null
    private val detailRegistrations = mutableListOf<ListenerRegistration>()
    private var attachedDeviceDetailsFor: String? = null
    private lateinit var qrScanLauncher: ActivityResultLauncher<ScanOptions>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = AndroidColor.parseColor("#F5F7FB")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
            val payload = result.contents
            if (!payload.isNullOrBlank()) {
                createPairRequest(payload, "", "")
            } else {
                message = "QR scan cancelled. Paste QR payload or enter Device ID + code."
            }
        }
        val status = FirebaseBootstrap.initialize(this)
        message = status.message
        if (status.configured) {
            val database = FirebaseDatabase.getInstance().reference
            db = database
            repository = ParentRepository(database)
            signedIn = FirebaseAuth.getInstance().currentUser != null
            if (signedIn) attachDeviceList()
        }

        setContent {
            ParentTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (db == null) {
                        MissingFirebaseScreen(message.orEmpty())
                    } else if (!signedIn) {
                        AuthScreen(message = message, busy = authBusy, onSignIn = ::signIn, onCreate = ::createAccount)
                    } else {
                        ParentDashboard(
                            message = message,
                            devices = devices,
                            loadingDevices = loadingDevices,
                            selectedDeviceId = selectedDeviceId,
                            apps = apps,
                            policies = policies,
                            states = states,
                            security = security,
                            unlockRequests = unlockRequests,
                            tamperEvents = tamperEvents,
                            loadingDeviceDetails = loadingDeviceDetails,
                            onSignOut = ::signOut,
                            onSelectDevice = { deviceId ->
                                selectedDeviceId = deviceId
                                attachDeviceDetails(deviceId)
                            },
                            onRemoveDevice = ::removePairedDevice,
                            onPair = ::createPairRequest,
                            onUpdatePolicy = ::updatePolicy,
                            onSetPin = ::setPin,
                            onApproveUnlock = { request -> updateUnlock(request, PolicyConstants.UNLOCK_APPROVED) },
                            onDenyUnlock = { request -> updateUnlock(request, PolicyConstants.UNLOCK_DENIED) },
                            onRescan = { selectedDeviceId?.let { sendCommand(it, PolicyConstants.COMMAND_RESCAN_APPS) } },
                            onOpenTvSetup = { selectedDeviceId?.let { sendCommand(it, PolicyConstants.COMMAND_OPEN_SETUP) } },
                            onResetToday = { packageName ->
                                selectedDeviceId?.let {
                                    sendCommand(it, PolicyConstants.COMMAND_RESET_TODAY, packageName)
                                }
                            },
                            onScanQr = ::openExternalQrScanner
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        clearAllListeners()
        super.onDestroy()
    }

    private fun signIn(email: String, password: String) {
        if (!validateAuthInput(email, password)) return
        authBusy = true
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener {
                attachDeviceList()
                signedIn = true
                message = "Signed in"
            }
            .addOnFailureListener { message = it.message }
            .addOnCompleteListener { authBusy = false }
    }

    private fun createAccount(email: String, password: String) {
        if (!validateAuthInput(email, password)) return
        authBusy = true
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener {
                attachDeviceList()
                signedIn = true
                message = "Account created"
            }
            .addOnFailureListener { message = it.message }
            .addOnCompleteListener { authBusy = false }
    }

    private fun validateAuthInput(email: String, password: String): Boolean {
        if (email.isBlank()) {
            message = "Enter an email address"
            return false
        }
        if (password.length < 6) {
            message = "Password must be at least 6 characters"
            return false
        }
        return true
    }

    private fun signOut() {
        clearAllListeners()
        FirebaseAuth.getInstance().signOut()
        signedIn = false
        selectedDeviceId = null
        attachedDeviceDetailsFor = null
        devices = emptyList()
        apps = emptyMap()
        policies = emptyMap()
        states = emptyMap()
        security = SecurityRuntime()
        unlockRequests = emptyList()
        tamperEvents = emptyList()
        loadingDevices = false
        loadingDeviceDetails = false
    }

    private fun attachDeviceList() {
        if (deviceListRegistration != null) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val database = db ?: run {
            message = "Firebase database is not ready"
            return
        }
        loadingDevices = true
        deviceListRegistration = registerListener(database.child(FirebasePaths.userDevices(uid))) { snapshot ->
            loadingDevices = false
            devices = snapshot.children.mapNotNull { child ->
                val deviceId = child.child("deviceId").getValue(String::class.java)
                    ?: child.key
                    ?: return@mapNotNull null
                ParentDevice(
                    deviceId = deviceId,
                    label = child.child("label").getValue(String::class.java) ?: deviceId,
                    lastSeen = child.child("lastSeen").getValue(Long::class.java),
                    online = child.child("online").getValue(Boolean::class.java) ?: false,
                    enforcementMode = child.child("enforcementMode").getValue(String::class.java)
                        ?: PolicyConstants.ENFORCEMENT_UNPROTECTED,
                    protectionHealthy = child.child("protectionHealthy").getValue(Boolean::class.java) ?: false
                )
            }
            if (selectedDeviceId != null && devices.none { it.deviceId == selectedDeviceId }) {
                selectedDeviceId = null
                clearDeviceDetailListeners()
                attachedDeviceDetailsFor = null
                apps = emptyMap()
                policies = emptyMap()
                states = emptyMap()
                security = SecurityRuntime()
                unlockRequests = emptyList()
                tamperEvents = emptyList()
            }
            if (selectedDeviceId == null && devices.size == 1) {
                selectedDeviceId = devices.first().deviceId
                attachDeviceDetails(devices.first().deviceId)
            }
        }
    }

    private fun attachDeviceDetails(deviceId: String) {
        if (attachedDeviceDetailsFor == deviceId) return
        clearDeviceDetailListeners()
        attachedDeviceDetailsFor = deviceId
        loadingDeviceDetails = true
        apps = emptyMap()
        policies = emptyMap()
        states = emptyMap()
        security = SecurityRuntime()
        unlockRequests = emptyList()
        tamperEvents = emptyList()

        val database = db ?: run {
            loadingDeviceDetails = false
            message = "Firebase database is not ready"
            return
        }

        registerDetailListener(database.child(FirebasePaths.deviceApps(deviceId))) {
            loadingDeviceDetails = false
            apps = it.children.mapNotNull { child ->
                val packageName = child.child("packageName").getValue(String::class.java)
                    ?: runCatching { PackageKeys.decode(child.key.orEmpty()) }.getOrNull()
                    ?: return@mapNotNull null
                if (packageName in PolicyConstants.deprecatedVirtualPolicyPackages) return@mapNotNull null
                packageName to ParentApp(
                    packageName = packageName,
                    label = child.child("label").getValue(String::class.java) ?: packageName,
                    blockable = child.child("blockable").getValue(Boolean::class.java) ?: false,
                    protectedReason = child.child("protectedReason").getValue(String::class.java)
                )
            }.toMap()
        }

        registerDetailListener(database.child(FirebasePaths.devicePolicyApps(deviceId))) {
            policies = it.children.mapNotNull { child ->
                val packageName = child.child("packageName").getValue(String::class.java)
                    ?: runCatching { PackageKeys.decode(child.key.orEmpty()) }.getOrNull()
                    ?: return@mapNotNull null
                val limit = child.child("dailyLimitMinutes").getValue(Long::class.java)?.toInt()?.takeIf { value -> value > 0 }
                packageName to ParentPolicy(
                    manualBlocked = child.child("manualBlocked").getValue(Boolean::class.java) ?: false,
                    dailyLimitMinutes = limit
                )
            }.toMap()
        }

        registerDetailListener(database.child(FirebasePaths.deviceStateApps(deviceId))) {
            states = it.children.mapNotNull { child ->
                val packageName = child.child("packageName").getValue(String::class.java)
                    ?: runCatching { PackageKeys.decode(child.key.orEmpty()) }.getOrNull()
                    ?: return@mapNotNull null
                packageName to ParentState(
                    suspended = child.child("suspended").getValue(Boolean::class.java) ?: false,
                    requestedSuspended = child.child("requestedSuspended").getValue(Boolean::class.java) ?: false,
                    manualBlocked = child.child("manualBlocked").getValue(Boolean::class.java) ?: false,
                    dailyLimitBlocked = child.child("dailyLimitBlocked").getValue(Boolean::class.java) ?: false,
                    networkBlocked = child.child("networkBlocked").getValue(Boolean::class.java) ?: false,
                    vpnApplied = child.child("vpnApplied").getValue(Boolean::class.java) ?: false,
                    vpnActive = child.child("vpnActive").getValue(Boolean::class.java) ?: false,
                    lockBlocked = child.child("lockBlocked").getValue(Boolean::class.java) ?: false,
                    lockReason = child.child("lockReason").getValue(String::class.java),
                    vpnLastError = child.child("vpnLastError").getValue(String::class.java),
                    fallbackLocked = child.child("fallbackLocked").getValue(Boolean::class.java) ?: false,
                    enforcementMode = child.child("enforcementMode").getValue(String::class.java)
                        ?: PolicyConstants.ENFORCEMENT_UNPROTECTED,
                    blockReason = child.child("blockReason").getValue(String::class.java),
                    usageMinutesToday = child.child("usageMinutesToday").getValue(Long::class.java) ?: 0L,
                    lastError = child.child("lastError").getValue(String::class.java)
                )
            }.toMap()
        }

        registerDetailListener(database.child(FirebasePaths.deviceSecurityRuntime(deviceId))) {
            security = SecurityRuntime(
                enforcementMode = it.child("enforcementMode").getValue(String::class.java)
                    ?: PolicyConstants.ENFORCEMENT_UNPROTECTED,
                deviceOwner = it.child("deviceOwner").getValue(Boolean::class.java) ?: false,
                deviceAdmin = it.child("deviceAdmin").getValue(Boolean::class.java) ?: false,
                deviceAdminSetupAvailable = it.child("deviceAdminSetupAvailable").getValue(Boolean::class.java) ?: true,
                accessibility = it.child("accessibility").getValue(Boolean::class.java) ?: false,
                usageAccess = it.child("usageAccess").getValue(Boolean::class.java) ?: false,
                vpnPrepared = it.child("vpnPrepared").getValue(Boolean::class.java) ?: false,
                vpnActive = it.child("vpnActive").getValue(Boolean::class.java) ?: false,
                vpnBlockedCount = it.child("vpnBlockedCount").getValue(Long::class.java)?.toInt() ?: 0,
                vpnLastError = it.child("vpnLastError").getValue(String::class.java),
                backgroundUnrestricted = it.child("backgroundUnrestricted").getValue(Boolean::class.java) ?: false,
                pinConfigured = it.child("pinConfigured").getValue(Boolean::class.java) ?: false,
                protectionHealthy = it.child("protectionHealthy").getValue(Boolean::class.java) ?: false,
                lastForegroundPackage = it.child("lastForegroundPackage").getValue(String::class.java),
                lastSyncError = it.child("lastSyncError").getValue(String::class.java)
            )
        }

        registerDetailListener(database.child(FirebasePaths.deviceUnlockRequests(deviceId))) {
            unlockRequests = it.children.mapNotNull { child ->
                UnlockRequest(
                    requestId = child.child("requestId").getValue(String::class.java) ?: child.key ?: return@mapNotNull null,
                    packageName = child.child("packageName").getValue(String::class.java) ?: "",
                    reason = child.child("reason").getValue(String::class.java) ?: "",
                    status = child.child("status").getValue(String::class.java) ?: "",
                    createdAt = child.child("createdAt").getValue(Long::class.java),
                    expiresAt = child.child("expiresAt").getValue(Long::class.java)
                )
            }.sortedByDescending { request -> request.createdAt ?: 0L }
        }

        registerDetailListener(database.child(FirebasePaths.deviceTamperEvents(deviceId)).limitToLast(30)) {
            tamperEvents = it.children.mapNotNull { child ->
                TamperEvent(
                    eventId = child.key ?: return@mapNotNull null,
                    type = child.child("type").getValue(String::class.java) ?: "",
                    message = child.child("message").getValue(String::class.java),
                    createdAt = child.child("createdAt").getValue(Long::class.java)
                )
            }.sortedByDescending { event -> event.createdAt ?: 0L }
        }
    }

    private fun registerDetailListener(query: Query, onData: (DataSnapshot) -> Unit) {
        detailRegistrations += registerListener(query, onData)
    }

    private fun registerListener(query: Query, onData: (DataSnapshot) -> Unit): ListenerRegistration {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onData(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                loadingDevices = false
                loadingDeviceDetails = false
                message = error.message
            }
        }
        query.addValueEventListener(listener)
        return ListenerRegistration(query, listener)
    }

    private fun clearAllListeners() {
        deviceListRegistration?.let { it.query.removeEventListener(it.listener) }
        deviceListRegistration = null
        clearDeviceDetailListeners()
    }

    private fun clearDeviceDetailListeners() {
        detailRegistrations.forEach { it.query.removeEventListener(it.listener) }
        detailRegistrations.clear()
        attachedDeviceDetailsFor = null
    }

    private fun createPairRequest(payload: String, manualDeviceId: String, manualCode: String) {
        val repo = repository ?: run {
            message = "Firebase database is not ready"
            return
        }
        val parsed = parsePairPayload(payload)
        val deviceId = parsed.first ?: manualDeviceId
        val secret = parsed.second
        if (deviceId.isBlank()) {
            message = "Enter a TV device ID or paste the QR payload."
            return
        }
        if (secret.isNullOrBlank() && manualCode.isBlank()) {
            message = "Enter the 6-digit code or paste the QR payload."
            return
        }
        repo.createPairRequest(
            deviceId = deviceId,
            secret = secret,
            manualCode = manualCode,
            onSuccess = { message = "Pair request sent" },
            onError = { message = it }
        )
    }

    private fun parsePairPayload(payload: String): Pair<String?, String?> {
        if (payload.isBlank()) return null to null
        return runCatching {
            val uri = Uri.parse(payload)
            uri.getQueryParameter("deviceId") to uri.getQueryParameter("secret")
        }.getOrElse { null to null }
    }

    private fun updatePolicy(packageName: String, policy: ParentPolicy) {
        val deviceId = selectedDeviceId ?: run {
            message = "Select a TV first"
            return
        }
        val repo = repository ?: run {
            message = "Firebase database is not ready"
            return
        }
        val app = apps[packageName]
        if (app?.blockable == false) {
            message = "This app is protected: ${app.protectedReason ?: "not blockable"}"
            return
        }
        if (policy.dailyLimitMinutes != null && policy.dailyLimitMinutes !in 1..1440) {
            message = "Daily limit must be between 1 and 1440 minutes"
            return
        }
        repo.updatePolicy(
            deviceId = deviceId,
            packageName = packageName,
            policy = policy,
            onSuccess = { message = "Policy updated" },
            onError = { message = it }
        )
    }

    private fun setPin(pin: String) {
        val deviceId = selectedDeviceId ?: run {
            message = "Select a TV before setting a PIN"
            return
        }
        val repo = repository ?: run {
            message = "Firebase database is not ready"
            return
        }
        if (!pin.matches(Regex("\\d{6}"))) {
            message = "PIN must be 6 digits"
            return
        }
        repo.setPin(
            deviceId = deviceId,
            pin = pin,
            onSuccess = { message = "Parent PIN updated" },
            onError = { message = it }
        )
    }

    private fun updateUnlock(request: UnlockRequest, status: String) {
        val deviceId = selectedDeviceId ?: run {
            message = "Select a TV first"
            return
        }
        val repo = repository ?: run {
            message = "Firebase database is not ready"
            return
        }
        if (status == PolicyConstants.UNLOCK_APPROVED &&
            request.expiresAt != null &&
            System.currentTimeMillis() > request.expiresAt
        ) {
            repo.updateUnlock(
                deviceId = deviceId,
                request = request,
                status = PolicyConstants.UNLOCK_EXPIRED,
                onSuccess = { message = "Unlock request expired" },
                onError = { message = it }
            )
            return
        }
        repo.updateUnlock(
            deviceId = deviceId,
            request = request,
            status = status,
            onSuccess = {
                message = if (status == PolicyConstants.UNLOCK_APPROVED) "Unlock approved" else "Unlock denied"
            },
            onError = { message = it }
        )
    }

    private fun sendCommand(deviceId: String, type: String, packageName: String? = null) {
        val repo = repository ?: run {
            message = "Firebase database is not ready"
            return
        }
        repo.sendCommand(
            deviceId = deviceId,
            type = type,
            packageName = packageName,
            onSuccess = { message = "Command sent" },
            onError = { message = it }
        )
    }

    private fun removePairedDevice(deviceId: String) {
        val repo = repository ?: run {
            message = "Firebase database is not ready"
            return
        }
        repo.removePairedDevice(
            deviceId = deviceId,
            onSuccess = {
                if (selectedDeviceId == deviceId) {
                    selectedDeviceId = null
                    clearDeviceDetailListeners()
                    attachedDeviceDetailsFor = null
                    apps = emptyMap()
                    policies = emptyMap()
                    states = emptyMap()
                    security = SecurityRuntime()
                    unlockRequests = emptyList()
                    tamperEvents = emptyList()
                }
                message = "TV removed"
            },
            onError = { message = it }
        )
    }

    private fun openExternalQrScanner() {
        qrScanLauncher.launch(
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setCaptureActivity(PortraitCaptureActivity::class.java)
                setPrompt("Scan the GuardPulse TV pairing QR")
                setBeepEnabled(false)
                setOrientationLocked(true)
            }
        )
    }
}

@Composable
private fun ParentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = GuardNavy,
            onPrimary = Color.White,
            primaryContainer = GuardNavySoft,
            secondary = ActionBlue,
            onSecondary = Color.White,
            secondaryContainer = ActionBlue,
            onSecondaryContainer = Color.White,
            tertiary = AlertRed,
            surface = SurfaceLight,
            background = SurfaceLight,
            error = AlertRed,
            errorContainer = ErrorSoft
        ),
        content = content
    )
}

private val GuardNavy = Color(0xFF031636)
private val GuardNavySoft = Color(0xFF1A2B4C)
private val ActionBlue = Color(0xFF316BF3)
private val SurfaceLight = Color(0xFFF8F9FF)
private val SurfaceCard = Color(0xFFFFFFFF)
private val SurfaceTint = Color(0xFFE5EEFF)
private val OutlineSoft = Color(0xFFC5C6CF)
private val TextMuted = Color(0xFF44474E)
private val AlertRed = Color(0xFFBA1A1A)
private val ErrorSoft = Color(0xFFFFDAD6)
private val SuccessGreen = Color(0xFF10B981)
private val SuccessSoft = Color(0xFFDCFCE7)

@Composable
private fun MissingFirebaseScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceLight)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        GuardCard {
            ShieldMark()
            Spacer(Modifier.height(18.dp))
            Text(
                "Firebase not configured",
                style = MaterialTheme.typography.headlineSmall,
                color = GuardNavy,
                fontWeight = FontWeight.Bold
            )
            Text(message, color = TextMuted, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun AuthScreen(
    message: String?,
    busy: Boolean,
    onSignIn: (String, String) -> Unit,
    onCreate: (String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceLight)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ShieldMark()
            Text(
                "GuardPulse",
                style = MaterialTheme.typography.headlineLarge,
                color = GuardNavy,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 18.dp)
            )
            Text(
                "Sign in to manage paired TVs.",
                color = TextMuted,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp, bottom = 28.dp)
            )
            GuardCard {
                GuardTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email",
                    placeholder = "name@example.com",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    leading = { Icon(Icons.Outlined.Security, contentDescription = null, tint = TextMuted) }
                )
                Spacer(Modifier.height(14.dp))
                GuardTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    placeholder = "Password",
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    leading = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = TextMuted) },
                    trailing = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "Hide" else "Show")
                        }
                    }
                )
                Button(
                    enabled = !busy,
                    onClick = { onSignIn(email, password) },
                    colors = ButtonDefaults.buttonColors(containerColor = GuardNavy),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .height(54.dp)
                ) {
                    Text(if (busy) "Working..." else "Sign In")
                    Spacer(Modifier.width(8.dp))
                    Text(">")
                }
                TextButton(
                    enabled = !busy,
                    onClick = { onCreate(email, password) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Create Account", color = ActionBlue)
                }
            }
            Text(
                message ?: "Connect to your Firebase project to get started.",
                color = TextMuted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 22.dp)
            )
        }
    }
}

@Composable
private fun ShieldMark() {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(GuardNavySoft),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Security, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
    }
}

@Composable
private fun GuardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Column(modifier) {
        Text(
            label.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = { Text(placeholder) },
            leadingIcon = leading,
            trailingIcon = trailing,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun GuardCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, OutlineSoft.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), content = content)
    }
}

@Composable
private fun GuardSectionTitle(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = GuardNavy)
        trailing?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceTint)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun StatusPill(label: String, ok: Boolean, modifier: Modifier = Modifier) {
    val bg = if (ok) SuccessSoft else ErrorSoft
    val fg = if (ok) Color(0xFF166534) else AlertRed
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(fg))
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MetaTile(label: String, value: String, ok: Boolean? = null, modifier: Modifier = Modifier) {
    val tileColor = if (ok == true) SuccessSoft else SurfaceLight
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(tileColor)
            .border(1.dp, if (ok == true) SuccessGreen.copy(alpha = 0.25f) else OutlineSoft.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(label.uppercase(Locale.US), color = TextMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(value, color = if (ok == true) Color(0xFF065F46) else GuardNavy, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun EmptyPanel(title: String, detail: String) {
    GuardCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            Icon(Icons.Outlined.Tv, contentDescription = null, tint = OutlineSoft, modifier = Modifier.size(48.dp))
            Text(title, color = GuardNavy, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Text(detail, color = TextMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun TopBar(selectedDeviceId: String?, onSignOut: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceLight)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("GuardPulse", style = MaterialTheme.typography.headlineSmall, color = GuardNavy, fontWeight = FontWeight.Bold)
            Text(selectedDeviceId ?: "No TV selected", color = TextMuted, style = MaterialTheme.typography.labelMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {}) { Icon(Icons.Outlined.Security, contentDescription = "Protection", tint = GuardNavy) }
            IconButton(onClick = {}) { Icon(Icons.Outlined.Lock, contentDescription = "Settings", tint = GuardNavy) }
            TextButton(onClick = onSignOut) { Text("Sign out", color = AlertRed) }
        }
    }
}

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("Devices", "Apps", "Security", "Events")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceTint)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { index, label ->
            val active = selected == index
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (active) ActionBlue else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = when (index) {
                        0 -> Icons.Outlined.Tv
                        1 -> Icons.Outlined.Add
                        2 -> Icons.Outlined.Security
                        else -> Icons.Outlined.Lock
                    },
                    contentDescription = label,
                    tint = if (active) Color.White else TextMuted
                )
                Text(label, color = if (active) Color.White else GuardNavy, style = MaterialTheme.typography.labelSmall, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ParentDashboard(
    message: String?,
    devices: List<ParentDevice>,
    loadingDevices: Boolean,
    selectedDeviceId: String?,
    apps: Map<String, ParentApp>,
    policies: Map<String, ParentPolicy>,
    states: Map<String, ParentState>,
    security: SecurityRuntime,
    unlockRequests: List<UnlockRequest>,
    tamperEvents: List<TamperEvent>,
    loadingDeviceDetails: Boolean,
    onSignOut: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onRemoveDevice: (String) -> Unit,
    onPair: (String, String, String) -> Unit,
    onUpdatePolicy: (String, ParentPolicy) -> Unit,
    onSetPin: (String) -> Unit,
    onApproveUnlock: (UnlockRequest) -> Unit,
    onDenyUnlock: (UnlockRequest) -> Unit,
    onRescan: () -> Unit,
    onOpenTvSetup: () -> Unit,
    onResetToday: (String) -> Unit,
    onScanQr: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val selectedDevice = devices.firstOrNull { it.deviceId == selectedDeviceId }
    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
        }
    }
    Scaffold(
        containerColor = SurfaceLight,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopBar(selectedDevice?.label ?: selectedDeviceId, onSignOut) },
        bottomBar = { BottomNav(tab) { tab = it } }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> DevicesTab(devices, loadingDevices, selectedDeviceId, onSelectDevice, onRemoveDevice, onPair, onScanQr)
                1 -> AppsTab(selectedDevice, selectedDeviceId, loadingDeviceDetails, apps, policies, states, onUpdatePolicy, onRescan, onResetToday)
                2 -> SecurityTab(selectedDeviceId, loadingDeviceDetails, security, unlockRequests, onSetPin, onApproveUnlock, onDenyUnlock, onOpenTvSetup)
                3 -> EventsTab(tamperEvents)
            }
        }
    }
}

@Composable
private fun DevicesTab(
    devices: List<ParentDevice>,
    loadingDevices: Boolean,
    selectedDeviceId: String?,
    onSelectDevice: (String) -> Unit,
    onRemoveDevice: (String) -> Unit,
    onPair: (String, String, String) -> Unit,
    onScanQr: () -> Unit
) {
    var payload by remember { mutableStateOf("") }
    var deviceId by remember { mutableStateOf("") }
    var codeDigits by remember { mutableStateOf(List(6) { "" }) }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(18.dp)
    ) {
        item {
            GuardSectionTitle("TV Control")
            val selected = devices.firstOrNull { it.deviceId == selectedDeviceId }
            selected?.let {
                SelectedDeviceBanner(it)
            }
        }
        item {
            GuardCard {
                Text("Pair New TV", style = MaterialTheme.typography.titleLarge, color = GuardNavy, fontWeight = FontWeight.Bold)
                Text("Scan the QR code displayed on your TV or enter details manually.", color = TextMuted, modifier = Modifier.padding(top = 8.dp))
                Button(
                    onClick = onScanQr,
                    colors = ButtonDefaults.buttonColors(containerColor = GuardNavy),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(52.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan QR Code")
                }
                Text("OR MANUAL ENTRY", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 10.dp))
                GuardTextField(payload, { payload = it }, "QR payload", "guardpulse://pair?deviceId=...")
                Spacer(Modifier.height(12.dp))
                GuardTextField(deviceId, { deviceId = it }, "Device ID", "e.g. TV-9A8B7C")
                Text("6-Digit Code", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    codeDigits.forEachIndexed { index, digit ->
                        if (index == 3) Text("-", color = TextMuted)
                        OutlinedTextField(
                            value = digit,
                            onValueChange = { value ->
                                codeDigits = codeDigits.toMutableList().also { it[index] = value.filter(Char::isDigit).take(1) }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(42.dp)
                        )
                    }
                }
                Button(
                    onClick = { onPair(payload, deviceId, codeDigits.joinToString("")) },
                    colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(52.dp)
                ) { Text("Connect Manually") }
            }
        }
        if (loadingDevices) {
            item {
                EmptyPanel("Loading TVs", "Reading paired TVs from Firebase...")
            }
        } else if (devices.isEmpty()) {
            item {
                EmptyPanel("No TVs paired", "Pair the TV using the QR payload or manual code shown on the TV app.")
            }
        }
        if (devices.isNotEmpty()) {
            item { GuardSectionTitle("Paired Devices", "${devices.count { it.online }} Active") }
            items(devices) { device ->
                DeviceCard(
                    device = device,
                    selected = device.deviceId == selectedDeviceId,
                    onSelectDevice = onSelectDevice,
                    onRemoveDevice = onRemoveDevice
                )
            }
        }
    }
}

@Composable
private fun SelectedDeviceBanner(device: ParentDevice) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GuardNavy)
            .padding(18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Security, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(device.label, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(device.deviceId, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        }
        StatusPill(if (device.online) "Online" else "Offline", device.online)
    }
}

@Composable
private fun DeviceCard(
    device: ParentDevice,
    selected: Boolean,
    onSelectDevice: (String) -> Unit,
    onRemoveDevice: (String) -> Unit
) {
    GuardCard(
        modifier = Modifier
            .border(if (selected) 2.dp else 1.dp, if (selected) GuardNavy else OutlineSoft, RoundedCornerShape(14.dp))
            .clickable { onSelectDevice(device.deviceId) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(GuardNavy), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Tv, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(device.label, style = MaterialTheme.typography.titleLarge, color = GuardNavy, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(device.deviceId, color = TextMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                StatusPill("Active", true, Modifier.weight(1f))
            } else {
                OutlinedButton(
                    onClick = { onSelectDevice(device.deviceId) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Select")
                }
            }
            OutlinedButton(
                onClick = { onRemoveDevice(device.deviceId) },
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, AlertRed)
            ) {
                Icon(Icons.Outlined.Close, contentDescription = null, tint = AlertRed)
                Spacer(Modifier.width(6.dp))
                Text("Remove", color = AlertRed)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetaTile("Mode", device.enforcementMode, device.enforcementMode != PolicyConstants.ENFORCEMENT_UNPROTECTED, Modifier.weight(1f))
            MetaTile("Health", if (device.protectionHealthy) "Healthy" else "Needs setup", device.protectionHealthy, Modifier.weight(1f))
        }
        MetaTile("Last seen", formatTimestamp(device.lastSeen), device.online, Modifier.fillMaxWidth().padding(top = 12.dp))
    }
}

private fun defaultParentPolicy(packageName: String): ParentPolicy {
    return if (PolicyConstants.isDefaultLocked(packageName)) {
        ParentPolicy(manualBlocked = true)
    } else {
        ParentPolicy()
    }
}

@Composable
private fun AppsTab(
    selectedDevice: ParentDevice?,
    selectedDeviceId: String?,
    loadingDeviceDetails: Boolean,
    apps: Map<String, ParentApp>,
    policies: Map<String, ParentPolicy>,
    states: Map<String, ParentState>,
    onUpdatePolicy: (String, ParentPolicy) -> Unit,
    onRescan: () -> Unit,
    onResetToday: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = apps.values
        .filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
        .sortedBy { it.label.lowercase() }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(18.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Managing Device", color = TextMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Tv, contentDescription = null, tint = TextMuted)
                        Spacer(Modifier.width(8.dp))
                        Text(selectedDevice?.label ?: selectedDeviceId ?: "No TV selected", color = GuardNavy, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Button(onClick = onRescan, colors = ButtonDefaults.buttonColors(containerColor = ActionBlue), shape = RoundedCornerShape(50)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Rescan")
                }
            }
        }
        if (selectedDeviceId == null) {
            item {
                EmptyPanel("No TV selected", "Select or pair a TV before managing apps.")
            }
            return@LazyColumn
        }
        item {
            OutlinedTextField(
                query,
                { query = it },
                placeholder = { Text("Search apps...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp)
            )
        }
        if (loadingDeviceDetails) {
            item {
                EmptyPanel("Loading apps", "Waiting for the TV to upload its app list.")
            }
            return@LazyColumn
        }
        if (apps.isEmpty()) {
            item {
                EmptyPanel("No apps yet", "Start Sync Service or Rescan Installed Apps on the TV.")
            }
            return@LazyColumn
        }
        items(filtered) { app ->
            val policy = policies[app.packageName] ?: defaultParentPolicy(app.packageName)
            val state = states[app.packageName] ?: ParentState()
            AppPolicyCard(app, policy, state, onUpdatePolicy, onResetToday)
        }
    }
}

@Composable
private fun AppPolicyCard(
    app: ParentApp,
    policy: ParentPolicy,
    state: ParentState,
    onUpdatePolicy: (String, ParentPolicy) -> Unit,
    onResetToday: (String) -> Unit
) {
    var limitText by remember(app.packageName, policy.dailyLimitMinutes) {
        mutableStateOf(policy.dailyLimitMinutes?.toString().orEmpty())
    }
    var expanded by remember(app.packageName) { mutableStateOf(false) }
    val sourceApp = app.packageName in PolicyConstants.sourceLockPackages
    val settingsApp = app.packageName in PolicyConstants.primarySettingsPackages
    val settingsSection = PolicyConstants.settingsSectionPolicy(app.packageName)
    val settingsSectionApp = settingsSection != null
    val settingsSectionName = settingsSection?.shortLabel ?: "Settings section"
    val lockControlled = app.blockable
    val networkBlocked = false
    val lockBlocked = state.lockBlocked ||
        (lockControlled && (policy.manualBlocked || state.manualBlocked || state.dailyLimitBlocked)) ||
        (!app.blockable && state.fallbackLocked)
    val sourceLocked = sourceApp && lockBlocked
    val settingsLocked = settingsApp && lockBlocked
    val settingsSectionsLocked = settingsSectionApp && lockBlocked
    val blocked = networkBlocked || lockBlocked
    val statusLabel = when {
        !app.blockable -> "Protected"
        sourceLocked -> "Live TV locked"
        settingsSectionsLocked -> "$settingsSectionName locked"
        settingsLocked -> "Settings locked"
        sourceApp -> "Live TV allowed"
        settingsSectionApp -> "$settingsSectionName allowed"
        settingsApp -> "Settings allowed"
        lockBlocked -> "App locked"
        state.dailyLimitBlocked -> "Daily limit lock"
        else -> "App allowed"
    }
    val statusColor = when {
        !app.blockable -> OutlineSoft
        blocked -> AlertRed
        else -> ActionBlue
    }
    GuardCard(modifier = Modifier.clickable { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(5.dp).height(86.dp).clip(RoundedCornerShape(4.dp)).background(statusColor))
            Spacer(Modifier.width(14.dp))
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(
                    when {
                        !app.blockable -> SurfaceTint
                        blocked -> ErrorSoft
                        else -> SurfaceTint
                    }
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (!app.blockable) Icons.Outlined.Lock else Icons.Outlined.Tv,
                    contentDescription = null,
                    tint = if (blocked) AlertRed else GuardNavy
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    app.label,
                    color = GuardNavy,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusLabel(
                    statusLabel,
                    when {
                        !app.blockable -> OutlineSoft
                        blocked -> AlertRed
                        else -> ActionBlue
                    },
                    modifier = Modifier.padding(top = 5.dp)
                )
                Text(
                    app.packageName,
                    color = TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
                val reason = when {
                    !app.blockable -> "Reason: ${app.protectedReason ?: "System critical"}"
                    sourceLocked && state.dailyLimitBlocked -> "Daily limit source lock"
                    sourceLocked && policy.manualBlocked -> "Live TV source locked by parent"
                    sourceLocked -> "Live TV source locked"
                    settingsSectionsLocked && policy.manualBlocked -> "$settingsSectionName locked by parent"
                    settingsSectionsLocked -> "$settingsSectionName locked"
                    settingsLocked && state.dailyLimitBlocked -> "Daily limit settings lock"
                    settingsLocked && policy.manualBlocked -> "Settings locked by parent"
                    settingsLocked -> "Settings locked"
                    lockBlocked && state.dailyLimitBlocked -> "Daily limit lock"
                    lockBlocked && policy.manualBlocked -> "Locked by parent"
                    lockBlocked -> "App locked"
                    state.dailyLimitBlocked -> "Daily limit lock"
                    policy.manualBlocked -> "Locked by parent"
                    policy.dailyLimitMinutes != null -> "Daily Limit Active (${policy.dailyLimitMinutes} mins)"
                    else -> null
                }
                reason?.let {
                    Text(
                        it,
                        color = if (blocked) AlertRed else TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Switch(
                enabled = app.blockable,
                checked = !policy.manualBlocked,
                onCheckedChange = { allowed -> onUpdatePolicy(app.packageName, policy.copy(manualBlocked = !allowed)) }
            )
        }
        if (state.usageMinutesToday > 0 || state.dailyLimitBlocked) {
            Row(
                modifier = Modifier
                    .padding(start = 75.dp, top = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (state.dailyLimitBlocked) ErrorSoft else SurfaceTint)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Security, contentDescription = null, tint = if (state.dailyLimitBlocked) AlertRed else TextMuted)
                Spacer(Modifier.width(8.dp))
                Text("${state.usageMinutesToday} mins used today", color = if (state.dailyLimitBlocked) AlertRed else TextMuted)
            }
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceLight)
                    .padding(14.dp)
            ) {
                Text(app.packageName, color = TextMuted, style = MaterialTheme.typography.bodySmall)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusChip(
                        when {
                            sourceLocked && policy.manualBlocked -> "Source locked by parent"
                            sourceApp -> if (policy.manualBlocked) "Source locked by parent" else "Source allowed by parent"
                            settingsSectionApp -> if (policy.manualBlocked) "$settingsSectionName locked by parent" else "$settingsSectionName allowed by parent"
                            settingsApp -> if (policy.manualBlocked) "Settings locked by parent" else "Settings allowed by parent"
                            policy.manualBlocked -> "Locked by parent"
                            else -> "Allowed by parent"
                        },
                        !policy.manualBlocked,
                        Modifier.weight(1f)
                    )
                    StatusChip(
                        when {
                            sourceLocked -> "Source lock active"
                            settingsSectionsLocked -> "$settingsSectionName lock active"
                            settingsLocked -> "Settings lock active"
                            lockBlocked -> "Screen lock active"
                            else -> "No screen lock"
                        },
                        !lockBlocked && (!networkBlocked || state.vpnApplied),
                        Modifier.weight(1f)
                    )
                }
                StatusChip(
                    "Mode: ${state.enforcementMode}",
                    state.enforcementMode != PolicyConstants.ENFORCEMENT_UNPROTECTED,
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                )
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        limitText,
                        { limitText = it.filter(Char::isDigit).take(4) },
                        label = { Text("Daily Limit") },
                        suffix = { Text("min") },
                        enabled = app.blockable,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(enabled = app.blockable, onClick = {
                        onUpdatePolicy(app.packageName, policy.copy(dailyLimitMinutes = limitText.toIntOrNull()?.takeIf { it > 0 }))
                    }, colors = ButtonDefaults.buttonColors(containerColor = GuardNavy)) { Text("Save") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(enabled = app.blockable && policy.dailyLimitMinutes != null, onClick = {
                        limitText = ""
                        onUpdatePolicy(app.packageName, policy.copy(dailyLimitMinutes = null))
                    }) { Text("Clear") }
                }
                TextButton(enabled = app.blockable, onClick = { onResetToday(app.packageName) }) { Text("Reset today") }
                state.lastError?.let { Text("Error: $it", color = AlertRed) }
            }
        }
    }
}

@Composable
private fun StatusLabel(label: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        label.uppercase(Locale.US),
        color = if (color == OutlineSoft) TextMuted else color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (color == OutlineSoft) SurfaceTint else color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun SecurityTab(
    selectedDeviceId: String?,
    loadingDeviceDetails: Boolean,
    security: SecurityRuntime,
    unlockRequests: List<UnlockRequest>,
    onSetPin: (String) -> Unit,
    onApproveUnlock: (UnlockRequest) -> Unit,
    onDenyUnlock: (UnlockRequest) -> Unit,
    onOpenTvSetup: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(18.dp)) {
        item {
            Text("Security Settings", style = MaterialTheme.typography.headlineSmall, color = GuardNavy, fontWeight = FontWeight.Bold)
            Text("Manage protection layers and review pending requests.", color = TextMuted, modifier = Modifier.padding(top = 4.dp))
        }
        if (selectedDeviceId == null) {
            item {
                EmptyPanel("No TV selected", "Select or pair a TV before changing security settings.")
            }
            return@LazyColumn
        }
        if (loadingDeviceDetails) {
            item {
                EmptyPanel("Loading security", "Reading TV protection health from Firebase...")
            }
        }
        item {
            GuardCard {
                Text("Protection Health", style = MaterialTheme.typography.titleLarge, color = GuardNavy, fontWeight = FontWeight.Bold)
                RuntimeRow("Enforcement Mode", security.enforcementMode, security.enforcementMode != PolicyConstants.ENFORCEMENT_UNPROTECTED)
                RuntimeRow(if (security.deviceAdminSetupAvailable) "Device Admin" else "Device Admin unavailable", if (security.deviceAdmin) "Active" else if (!security.deviceAdminSetupAvailable) "Unavailable" else "Needs setup", security.deviceAdmin || !security.deviceAdminSetupAvailable)
                RuntimeRow("Accessibility", if (security.accessibility) "Active" else "Needs action", security.accessibility)
                RuntimeRow("Usage Access", if (security.usageAccess) "Active" else "Needs action", security.usageAccess)
                RuntimeRow(
                    "Network Filter",
                    "Not required for app locks",
                    true
                )
                RuntimeRow(
                    "Background Access",
                    if (security.backgroundUnrestricted) "Unrestricted" else "Battery restricted",
                    security.backgroundUnrestricted
                )
                RuntimeRow("PIN", if (security.pinConfigured) "Configured" else "Missing", security.pinConfigured)
                RuntimeRow("Healthy", if (security.protectionHealthy) "Healthy" else "Needs setup", security.protectionHealthy)
                if (security.enforcementMode == PolicyConstants.ENFORCEMENT_FALLBACK) {
                    Text(
                        "Fallback mode protects via Accessibility and PIN gate. It is not uninstall-proof.",
                        color = GuardNavy,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceTint)
                            .padding(14.dp)
                    )
                }
                security.lastSyncError?.let { Text("Last sync error: $it", color = AlertRed, modifier = Modifier.padding(top = 10.dp)) }
                security.lastForegroundPackage?.let { Text("Last foreground: $it", color = TextMuted, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
        item {
            GuardCard {
                Text("TV Setup Access", style = MaterialTheme.typography.titleLarge, color = GuardNavy, fontWeight = FontWeight.Bold)
                Text("Open the hidden setup screen on the selected TV. The TV will require the parent PIN before showing setup.", color = TextMuted, modifier = Modifier.padding(top = 6.dp))
                Button(
                    onClick = onOpenTvSetup,
                    colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(52.dp)
                ) {
                    Icon(Icons.Outlined.Tv, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open TV Setup")
                }
            }
        }
        item {
            GuardCard {
                Text("Parent PIN", style = MaterialTheme.typography.titleLarge, color = GuardNavy, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(6) { index ->
                        Box(
                            Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceLight).border(1.dp, OutlineSoft, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (pin.length > index || security.pinConfigured) "*" else "", color = GuardNavy, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                GuardTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                    label = "New 6-digit PIN",
                    placeholder = "Enter PIN",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.padding(top = 14.dp)
                )
                Button(
                    onClick = { onSetPin(pin) },
                    colors = ButtonDefaults.buttonColors(containerColor = GuardNavy),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(52.dp)
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (security.pinConfigured) "Change PIN" else "Set PIN")
                }
            }
        }
        item { GuardSectionTitle("Pending Requests") }
        items(unlockRequests.filter {
            it.status == PolicyConstants.UNLOCK_PENDING &&
                (it.expiresAt == null || System.currentTimeMillis() <= it.expiresAt)
        }) { request ->
            GuardCard(modifier = Modifier.border(2.dp, ActionBlue.copy(alpha = 0.35f), RoundedCornerShape(14.dp))) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(AlertRed), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Tv, contentDescription = null, tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(request.packageName, color = GuardNavy, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(request.reason, color = AlertRed)
                        Text("Requested: ${formatTimestamp(request.createdAt)}", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                        Text("Expires: ${formatTimestamp(request.expiresAt)}", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { onDenyUnlock(request) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Close, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Deny")
                    }
                    Button(onClick = { onApproveUnlock(request) }, colors = ButtonDefaults.buttonColors(containerColor = ActionBlue), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Approve")
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeRow(label: String, value: String, ok: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = GuardNavy)
        StatusPill(value, ok)
    }
}

@Composable
private fun EventsTab(tamperEvents: List<TamperEvent>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(18.dp)) {
        item {
            Text("Events", style = MaterialTheme.typography.headlineSmall, color = GuardNavy, fontWeight = FontWeight.Bold)
            Text("Tamper and protection events from the selected TV.", color = TextMuted, modifier = Modifier.padding(top = 4.dp))
        }
        if (tamperEvents.isEmpty()) {
            item {
                EmptyPanel("No events", "Tamper and protection events will appear here.")
            }
        }
        items(tamperEvents) { event ->
            val critical = event.type.contains("disabled", ignoreCase = true) || event.type.contains("risky", ignoreCase = true)
            GuardCard(modifier = Modifier.border(1.dp, if (critical) AlertRed.copy(alpha = 0.35f) else OutlineSoft, RoundedCornerShape(14.dp))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(if (critical) ErrorSoft else SurfaceTint), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Security, contentDescription = null, tint = if (critical) AlertRed else GuardNavy)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(event.type.ifBlank { "Event" }, color = GuardNavy, fontWeight = FontWeight.Bold)
                        Text(event.message ?: "No details", color = TextMuted)
                        Text("Time: ${formatTimestamp(event.createdAt)}", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, ok: Boolean, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = {},
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            Icon(
                if (ok) Icons.Outlined.Check else Icons.Outlined.Security,
                contentDescription = null
            )
        },
        modifier = modifier
    )
}

@Composable
private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    GuardCard {
        Text(title, style = MaterialTheme.typography.titleMedium, color = GuardNavy, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

private fun formatTimestamp(value: Long?): String {
    if (value == null || value <= 0L) return "unknown"
    return SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()).format(Date(value))
}
