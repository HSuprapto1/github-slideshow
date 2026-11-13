package com.roamatlas.data.email

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.Message
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gmail API integration for importing flight bookings from email.
 * 
 * Requires Gmail API setup in Google Cloud Console:
 * 1. Enable Gmail API
 * 2. Create OAuth 2.0 credentials
 * 3. Add gmail.readonly scope
 * 
 * The user signs in with their Google account (e.g., hardian97@googlemail.com)
 * and grants read-only access to scan for booking emails.
 */
@Singleton
class GmailFlightImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookingEmailParser: BookingEmailParser
) {
    private var gmailService: Gmail? = null
    private var googleSignInClient: GoogleSignInClient? = null
    
    // Search queries to find booking emails
    private val BOOKING_SEARCH_QUERIES = listOf(
        "subject:(booking confirmation) OR subject:(flight confirmation) OR subject:(e-ticket) OR subject:(itinerary)",
        "from:(singapore airlines) OR from:(garuda) OR from:(emirates) OR from:(qantas) OR from:(airasia)",
        "subject:(your trip) OR subject:(your flight) OR subject:(reservation confirmed)",
        "(e-ticket OR itinerary OR booking) (flight OR airline) -unsubscribe"
    )
    
    // Known airline sender domains
    private val AIRLINE_DOMAINS = listOf(
        "singaporeair.com",
        "garuda-indonesia.com",
        "emirates.com",
        "qantas.com",
        "airasia.com",
        "cathaypacific.com",
        "qatarairways.com",
        "thaiairways.com",
        "malaysiaairlines.com",
        "lionair.co.id",
        "batikair.com",
        "jetstar.com",
        "scoot.com",
        "delta.com",
        "united.com",
        "aa.com",
        "britishairways.com",
        "lufthansa.com",
        "klm.com"
    )
    
    /**
     * Get the sign-in intent for Google OAuth with Gmail read access.
     */
    fun getSignInIntent(): Intent {
        googleSignInClient = createSignInClient()
        return googleSignInClient!!.signInIntent
    }

    private fun createSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(GmailScopes.GMAIL_READONLY))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }
    
    /**
     * Handle sign-in result and initialize Gmail service.
     */
    suspend fun handleSignInResult(account: GoogleSignInAccount): Boolean = withContext(Dispatchers.IO) {
        try {
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                listOf(GmailScopes.GMAIL_READONLY)
            )
            credential.selectedAccount = account.account
            
            gmailService = Gmail.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName("RoamAtlas")
                .build()
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Check if user is already signed in with Gmail access.
     */
    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null && GoogleSignIn.hasPermissions(account, Scope(GmailScopes.GMAIL_READONLY))
    }
    
    /**
     * Get signed-in email address.
     */
    fun getSignedInEmail(): String? {
        return GoogleSignIn.getLastSignedInAccount(context)?.email
    }

    suspend fun restoreSignedInSession(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        if (!GoogleSignIn.hasPermissions(account, Scope(GmailScopes.GMAIL_READONLY))) {
            return false
        }
        return handleSignInResult(account)
    }
    
    /**
     * Sign out from Gmail.
     */
    suspend fun signOut() = withContext(Dispatchers.Main) {
        val client = googleSignInClient ?: createSignInClient()
        suspendCancellableCoroutine { continuation ->
            client.revokeAccess()
                .addOnSuccessListener {
                    if (continuation.isActive) continuation.resume(Unit)
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
        }
        gmailService = null
    }
    
    /**
     * Scan Gmail for flight booking emails and return parsed itineraries.
     * 
     * @param maxResults Maximum number of emails to scan
     * @param afterDate Only scan emails after this date (milliseconds)
     */
    suspend fun scanForBookingEmails(
        maxResults: Int = 50,
        afterDate: Long? = null
    ): List<ParsedItinerary> = withContext(Dispatchers.IO) {
        val gmail = gmailService ?: throw IllegalStateException("Not signed in to Gmail")
        val results = mutableListOf<ParsedItinerary>()
        
        try {
            // Build search query
            val dateQuery = afterDate?.let {
                val dateStr = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.US)
                    .format(java.util.Date(it))
                "after:$dateStr"
            } ?: ""
            
            for (searchQuery in BOOKING_SEARCH_QUERIES) {
                val fullQuery = "$searchQuery $dateQuery".trim()
                
                val messagesResponse = gmail.users().messages()
                    .list("me")
                    .setQ(fullQuery)
                    .setMaxResults(maxResults.toLong())
                    .execute()
                
                val messages = messagesResponse.messages ?: continue
                
                for (messageRef in messages) {
                    try {
                        val message = gmail.users().messages()
                            .get("me", messageRef.id)
                            .setFormat("full")
                            .execute()
                        
                        val emailContent = extractEmailContent(message)
                        if (emailContent.isNotBlank()) {
                            val itinerary = bookingEmailParser.parseEmail(emailContent)
                            if (itinerary.flights.isNotEmpty()) {
                                results.add(itinerary)
                            }
                        }
                    } catch (e: Exception) {
                        // Skip individual message errors
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
        
        // Remove duplicates based on booking reference
        results.distinctBy { it.bookingReference ?: it.hashCode() }
    }
    
    /**
     * Search for specific booking by reference or flight number.
     */
    suspend fun searchBooking(query: String): List<ParsedItinerary> = withContext(Dispatchers.IO) {
        val gmail = gmailService ?: throw IllegalStateException("Not signed in to Gmail")
        val results = mutableListOf<ParsedItinerary>()
        
        try {
            val messagesResponse = gmail.users().messages()
                .list("me")
                .setQ("$query (booking OR flight OR ticket OR itinerary)")
                .setMaxResults(20)
                .execute()
            
            val messages = messagesResponse.messages ?: return@withContext emptyList()
            
            for (messageRef in messages) {
                try {
                    val message = gmail.users().messages()
                        .get("me", messageRef.id)
                        .setFormat("full")
                        .execute()
                    
                    val emailContent = extractEmailContent(message)
                    if (emailContent.isNotBlank()) {
                        val itinerary = bookingEmailParser.parseEmail(emailContent)
                        if (itinerary.flights.isNotEmpty()) {
                            results.add(itinerary)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
        
        results
    }
    
    private fun extractEmailContent(message: Message): String {
        val payload = message.payload ?: return ""
        
        // Try to get plain text part first
        val textPart = findPart(payload, "text/plain")
        if (textPart != null) {
            return decodeBase64(textPart.body?.data ?: "")
        }
        
        // Fall back to HTML and strip tags
        val htmlPart = findPart(payload, "text/html")
        if (htmlPart != null) {
            val html = decodeBase64(htmlPart.body?.data ?: "")
            return stripHtml(html)
        }
        
        // Try the main body
        val body = payload.body?.data
        if (body != null) {
            return decodeBase64(body)
        }
        
        return ""
    }
    
    private fun findPart(payload: com.google.api.services.gmail.model.MessagePart, mimeType: String): com.google.api.services.gmail.model.MessagePart? {
        if (payload.mimeType == mimeType) {
            return payload
        }
        
        payload.parts?.forEach { part ->
            val found = findPart(part, mimeType)
            if (found != null) return found
        }
        
        return null
    }
    
    private fun decodeBase64(encoded: String): String {
        return try {
            val decoded = Base64.getUrlDecoder().decode(encoded)
            String(decoded, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
    
    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
