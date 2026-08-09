package com.twofold.feature.present

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.twofold.R
import com.twofold.data.document.Clause
import com.twofold.data.document.ClauseSegmenter
import com.twofold.data.document.ClauseSpeaker
import com.twofold.data.document.ClauseTranslator
import com.twofold.data.document.ClientLanguage
import com.twofold.data.document.ModelState
import com.twofold.data.document.DocumentRef
import com.twofold.data.document.DocumentRepository
import com.twofold.data.document.PageStore
import com.twofold.data.document.PdfSource
import com.twofold.data.document.OcrTextExtractor
import com.twofold.data.document.SpeechReadiness
import com.twofold.data.document.PdfTextExtractor
import com.twofold.data.document.ExtractedText
import com.twofold.data.document.TextCache
import com.twofold.data.notes.DocumentNotes
import com.twofold.data.notes.NotesRepository
import com.twofold.data.notes.PageNotes
import com.twofold.data.session.MeetingKind
import com.twofold.data.session.Session
import com.twofold.data.session.SessionLog
import com.twofold.data.session.SignatureRecord
import com.twofold.data.session.SignedPdfExporter
import kotlinx.coroutines.CoroutineScope
import java.io.File

/** An image and the page number it belongs to, carried together so they cannot disagree. */
data class RenderedPage(val bitmap: Bitmap?, val index: Int)

/**
 * Holds the open document and the page currently being shown.
 *
 * Deliberately one page index, shared by both panes. The client's half is not a separate viewer
 * that happens to be kept in sync — it is the same page, drawn twice. There is no code path that
 * can leave the two halves on different pages.
 */
class PresentState(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val repository = DocumentRepository(context)
    private val notesRepository = NotesRepository(context)
    private val textExtractor = PdfTextExtractor(context)
    private val ocrExtractor = OcrTextExtractor()
    private val translator = ClauseTranslator()
    private val textCache = TextCache(context)
    private val speaker = ClauseSpeaker(context)

    /**
     * The client's language survives a restart.
     *
     * An agent sets Hindi once and expects it to stay set. Losing it on relaunch means discovering
     * mid-meeting that the client's half is in English, which is the moment it matters most. The
     * language pack itself is already cached by ML Kit, so restoring is instant after the first
     * download.
     */
    private val prefs = context.getSharedPreferences("twofold.present", Context.MODE_PRIVATE)

    private var source: PdfSource? = null
    private var store: PageStore? = null

    /**
     * The document as readable units. This — not the page — is what the client's half shows and
     * what navigation moves through, because a whole page is physically unreadable on that half.
     */
    var clauses by mutableStateOf<List<Clause>>(emptyList())
        private set

    var clauseIndex by mutableIntStateOf(0)
        private set

    /** The clause as written. The agent always sees this; the client may see a translation. */
    val sourceClause: Clause? get() = clauses.getOrNull(clauseIndex)

    /**
     * The clause as the client will read it — translated when a language is set.
     *
     * Held separately from the source so the agent always keeps the original wording. The whole
     * point is that the two halves show different renderings of the same clause at the same moment.
     */
    var currentClause by mutableStateOf<Clause?>(null)
        private set

    /** The language the client reads. The agent's half never changes. */
    var clientLanguage by mutableStateOf(
        runCatching { ClientLanguage.valueOf(prefs.getString(KEY_LANGUAGE, null) ?: "ORIGINAL") }
            .getOrDefault(ClientLanguage.ORIGINAL)
    )
        private set

    var modelState by mutableStateOf(ModelState.NOT_NEEDED)
        private set

    /**
     * Chooses the client's language, downloading the pack if needed.
     *
     * Called from the prepare screen rather than mid-meeting: a pack is tens of megabytes, and a
     * client should never be watching a progress bar.
     */
    suspend fun setClientLanguage(language: ClientLanguage) {
        clientLanguage = language
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
        modelState = if (language == ClientLanguage.ORIGINAL) ModelState.NOT_NEEDED else ModelState.DOWNLOADING
        modelState = translator.prepare(language)
        armLanguage()
        refreshClientClause()
    }

    /** Arms the voice for the client's language. See [SpeechReadiness] for why this is tri-state. */
    private suspend fun armLanguage() {
        speech = speaker.prepare(clientLanguage)
    }

    // region speech

    /**
     * Whether this phone has a voice for the client's language.
     *
     * Surfaced rather than swallowed. Marathi in particular is missing on plenty of devices, and an
     * agent needs to discover that while preparing, not when they press a button in front of
     * someone. But only [SpeechReadiness.NO_VOICE] is worth telling them about — see the type.
     */
    var speech by mutableStateOf(SpeechReadiness.UNKNOWN)
        private set

    /** Offered unless we know it cannot work. An unanswered engine still gets the benefit of doubt. */
    val canSpeak: Boolean get() = speech != SpeechReadiness.NO_VOICE

    var isSpeaking by mutableStateOf(false)
        private set

    /**
     * Reads the clause the client is looking at, in the language they are reading it in.
     *
     * Pressed by the agent, on their own half. Deliberately not automatic: a clause that starts
     * talking the moment it appears would talk over the agent, who is the one running the meeting.
     */
    suspend fun speakCurrentClause() {
        val clause = currentClause ?: return
        if (isSpeaking) return stopSpeaking()

        // The engine may still have been binding when the language was chosen. Settling it here, on
        // the press, is what lets the control stay offered while the answer is genuinely unknown.
        if (speech == SpeechReadiness.UNKNOWN) armLanguage()
        if (speech != SpeechReadiness.READY) return

        isSpeaking = true
        speaker.speak(clause) { isSpeaking = false }
    }

    fun stopSpeaking() {
        speaker.stop()
        isSpeaking = false
    }

    // endregion

    // region what the client asked about

    /**
     * The one thing the client can do besides sign.
     *
     * Every other control belongs to the agent, which is correct — it is their meeting. But a
     * document explained entirely by the person selling it, with no way for the other party to say
     * *wait, what does this mean*, is the exact dynamic that produces a signature on something
     * nobody understood.
     *
     * It is also the part with commercial weight. The clauses a client asked about are written into
     * the signed copy, which turns "they signed" into "they were shown each clause, these are the
     * ones they asked about, and they signed after". That record is what an insurer or a bank
     * actually wants when a sale is disputed years later, and it is not obtainable from a paper
     * signature at all.
     */
    var questionedClauses by mutableStateOf<Set<Int>>(emptySet())
        private set

    /** The client's own button, in the client's own language — see [ClientLanguage.explainLabel]. */
    val explainLabel: String get() = clientLanguage.explainLabel

    val currentClauseWasQuestioned: Boolean get() = clauseIndex in questionedClauses

    fun markCurrentClauseQuestioned() {
        if (clauses.isEmpty()) return
        questionedClauses = questionedClauses + clauseIndex
    }

    /** Clause labels, in document order, for the signed record. */
    private fun questionedLabels(): List<String> =
        questionedClauses.sorted().mapNotNull { clauses.getOrNull(it)?.label }

    // endregion

    // region who is across the table

    /**
     * Changes the wording and nothing else — see [MeetingKind].
     *
     * Persisted, because a loan officer is a loan officer every day. Asking them to reselect it each
     * morning would be asking them to maintain the app's state rather than the other way round.
     */
    var meetingKind by mutableStateOf(
        runCatching { MeetingKind.valueOf(prefs.getString(KEY_KIND, null) ?: "INSURANCE") }
            .getOrDefault(MeetingKind.INSURANCE)
    )
        private set

    /** `chooseMeetingKind`, not `setMeetingKind` — the generated property setter owns that name. */
    fun chooseMeetingKind(kind: MeetingKind) {
        meetingKind = kind
        prefs.edit().putString(KEY_KIND, kind.name).apply()
    }

    // endregion

    private suspend fun refreshClientClause() {
        val source = sourceClause
        currentClause = when {
            source == null -> null
            clientLanguage == ClientLanguage.ORIGINAL -> source
            modelState != ModelState.READY -> source
            else -> translator.translate(source)
        }
    }

    /** True when neither the text layer nor OCR produced anything readable. Agent's eyes only. */
    var hasNoText by mutableStateOf(false)
        private set

    /** True while OCR is reading a scanned document. Far slower than the text-layer path. */
    var isRecognising by mutableStateOf(false)
        private set

    /**
     * True when the clauses came from OCR rather than an embedded text layer.
     *
     * It matters because OCR is *approximate*. It reads in visual order, so a two-column benefits
     * table can flatten into the paragraph below it and end up under the wrong heading — observed,
     * not hypothetical. The agent is told; the client is not, because a warning on their half
     * would undermine a document the agent is about to talk them through, and reconciling it is
     * the agent's job rather than theirs.
     */
    var textIsApproximate by mutableStateOf(false)
        private set

    private var notes by mutableStateOf(DocumentNotes(""))

    /** The private layer for the page currently on screen. Agent side only. */
    val currentNotes: PageNotes get() = notes.forPage(rendered.index)

    /**
     * How large the client's half renders the page, 1.0 to 2.0.
     *
     * Lives here rather than in the client's own state because the *agent* controls it. Many
     * clients are over fifty and reading a policy across a table without their glasses, and asking
     * them to pinch-zoom a contract in front of the person selling it to them is not something
     * anyone does. The agent raises it for them.
     */
    var legibility by mutableFloatStateOf(1f)
        private set

    var document by mutableStateOf<DocumentRef?>(null)
        private set

    /** The page the agent has asked for. May briefly lead [rendered] while a render is in flight. */
    var pageIndex by mutableIntStateOf(0)
        private set

    /**
     * What is actually on screen — image and page number together, never separately.
     *
     * These are one value on purpose. Updating the index the moment it is requested, while the
     * bitmap lags behind the render, would show the client "3 / 12" underneath page 2 for a frame
     * or two. Small, but it happens in front of a customer reading a contract, and it reads as a
     * broken app at precisely the wrong moment.
     */
    var rendered by mutableStateOf(RenderedPage(null, 0))
        private set

    var isLoading by mutableStateOf(false)
        private set

    /** Non-null when an import or open failed, for the agent's eyes only. */
    var error by mutableStateOf<String?>(null)
        private set

    val pageCount: Int get() = store?.pageCount ?: 0

    suspend fun importAndOpen(uri: Uri) {
        isLoading = true
        error = null

        val ref = repository.import(uri)
        if (ref == null) {
            error = context.getString(R.string.error_import_failed)
            isLoading = false
            return
        }
        open(ref)
    }

    suspend fun open(ref: DocumentRef) {
        isLoading = true
        error = null

        closeCurrent()

        val opened = PdfSource.open(ref.file)
        if (opened == null) {
            error = context.getString(R.string.error_open_failed)
            isLoading = false
            return
        }

        source = opened
        store = PageStore(context, opened, scope)
        document = ref
        pageIndex = 0
        notes = notesRepository.load(ref.id)

        val text = textCache.load(ref.id, ref.file) ?: extract(ref, opened).also {
            textCache.save(ref.id, ref.file, it)
        }
        textIsApproximate = text.isApproximate
        hasNoText = !textExtractor.hasUsableText(text.pages)
        clauses = if (hasNoText) emptyList() else ClauseSegmenter.segmentAll(text.pages)
        clauseIndex = 0

        // Re-arm the translator for the remembered language before the first clause is shown, so a
        // client never briefly sees English on a device set to Hindi.
        if (clientLanguage != ClientLanguage.ORIGINAL && modelState != ModelState.READY) {
            modelState = translator.prepare(clientLanguage)
        }
        armLanguage()
        refreshClientClause()

        // A new document is a new conversation. Carrying the previous client's questions into it
        // would put someone else's confusion on this one's signed copy.
        questionedClauses = emptySet()

        isLoading = false
        renderCurrent()
    }

    /**
     * Reads the document, the expensive way, exactly once per file — see [TextCache].
     *
     * Text layer first, because it is instant and exact. A scan has none, so fall back to reading
     * the pages with OCR: slow, but those are precisely the documents most likely to need
     * explaining, and an empty client half would be the worst possible answer.
     */
    private suspend fun extract(ref: DocumentRef, opened: PdfSource): ExtractedText {
        val fromLayer = textExtractor.extractPages(ref.file)
        if (textExtractor.hasUsableText(fromLayer)) {
            return ExtractedText(fromLayer, isApproximate = false)
        }

        isRecognising = true
        val recognised = ocrExtractor.extractPages(opened)
        isRecognising = false
        return ExtractedText(recognised, isApproximate = true)
    }

    /**
     * Moves to a clause, and brings the agent's page image along with it.
     *
     * The two halves stay on one document position: the client reads clause N, the agent sees the
     * page clause N is printed on. They cannot drift apart, because there is one index.
     */
    suspend fun goToClause(index: Int) {
        if (clauses.isEmpty()) return
        // Leaving the previous clause still being read aloud over the new one is the single most
        // confusing thing this app could do to someone who is listening rather than reading.
        stopSpeaking()
        clauseIndex = index.coerceIn(0, clauses.lastIndex)
        refreshClientClause()

        val page = currentClause?.pageIndex ?: return
        if (page != pageIndex) {
            pageIndex = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            pagesSeen.add(pageIndex)
            renderCurrent()
        }
    }

    suspend fun nextClause() = goToClause(clauseIndex + 1)

    suspend fun previousClause() = goToClause(clauseIndex - 1)

    // region the private layer

    /** Writes through on every edit. An agent editing notes in a car will not press save. */
    suspend fun setNote(text: String) = updateNotes { it.copy(note = text) }

    suspend fun addTalkTrackLine(line: String) {
        if (line.isBlank()) return
        updateNotes { it.copy(talkTrack = it.talkTrack + line.trim()) }
    }

    suspend fun removeTalkTrackLine(index: Int) = updateNotes {
        it.copy(talkTrack = it.talkTrack.filterIndexed { i, _ -> i != index })
    }

    private suspend fun updateNotes(transform: (PageNotes) -> PageNotes) {
        val page = rendered.index
        notes = notes.withPage(page, transform(notes.forPage(page)))
        notesRepository.save(notes)
    }

    // endregion

    /** Named `adjust` rather than `set` — the generated property setter already owns that name. */
    fun adjustLegibility(scale: Float) {
        legibility = scale.coerceIn(MIN_LEGIBILITY, MAX_LEGIBILITY)
    }

    // region session recording

    private var activeSession: Session? = null
    private val sessionLog = SessionLog(context)
    private val pagesSeen = mutableSetOf<Int>()

    /**
     * Who the agent is sitting with — a first name, "Mrs R", a policy number.
     *
     * One value feeding both the session log and the signature line. They were separate before,
     * which is how the signed PDF ended up attributing the signature to the document's own filename
     * and the session log ended up with an empty client.
     */
    var clientLabel by mutableStateOf("")

    /**
     * Called when the device enters Twofold mode with a document open — i.e. the moment a meeting
     * actually starts. Not on app launch: opening the app on a train is not a client meeting, and
     * a log full of those is a log nobody reads.
     */
    fun beginSession() {
        val ref = document ?: return
        if (activeSession != null) return

        pagesSeen.clear()
        pagesSeen.add(rendered.index)
        activeSession = Session(
            id = System.currentTimeMillis().toString(RADIX_36),
            documentId = ref.id,
            documentTitle = ref.title,
            clientLabel = clientLabel,
            startedAt = System.currentTimeMillis(),
            endedAt = null,
            pagesShown = 1,
            signed = false,
        )
    }

    /** Ends the meeting and writes it to the log. Safe to call when no session is running. */
    suspend fun endSession(signed: Boolean = false) {
        val session = activeSession ?: return
        activeSession = null

        sessionLog.record(
            session.copy(
                endedAt = System.currentTimeMillis(),
                pagesShown = pagesSeen.size,
                signed = signed,
            )
        )
    }

    suspend fun unsignedSessions(): List<Session> = sessionLog.unsigned()

    // endregion

    // region signing

    /** When true the client's half is a signature surface and nothing else. */
    var isSigning by mutableStateOf(false)
        private set

    var signerName by mutableStateOf("")
        private set

    var lastSignedFile by mutableStateOf<File?>(null)
        private set

    fun startSigning() {
        // Falls back to a neutral word rather than the document's filename, which is what it used
        // to do — a signed PDF that says "signed by Term_Life_Policy" is worse than one that says
        // "signed by Client".
        signerName = clientLabel.ifBlank { meetingKind.signerNoun }
        // A spotlight left casting under a signature line would dim the thing being signed.
        isSigning = true
    }

    fun cancelSigning() {
        isSigning = false
    }

    /**
     * Flattens the signature into a signed copy.
     *
     * Returns null on failure and leaves [isSigning] true, so a failed export keeps the client on
     * the signature screen rather than silently dropping them back to the document as though
     * something had been recorded.
     */
    suspend fun completeSigning(
        strokes: List<List<Offset>>,
        padWidth: Float,
        padHeight: Float,
        isPro: Boolean,
    ): File? {
        val currentSource = source ?: return null
        val currentDocument = document ?: return null
        if (strokes.isEmpty()) return null

        val signed = SignedPdfExporter(context).export(
            source = currentSource,
            sourceFile = currentDocument.file,
            signature = SignatureRecord(
                strokes = strokes,
                padWidth = padWidth,
                padHeight = padHeight,
                signerName = signerName.ifBlank { meetingKind.signerNoun },
            ),
            signedPageIndex = rendered.index,
            questionedClauses = questionedLabels(),
            isPro = isPro,
        )

        if (signed != null) {
            lastSignedFile = signed
            isSigning = false
            endSession(signed = true)
        }
        return signed
    }

    // endregion

    private suspend fun renderCurrent() {
        val currentStore = store ?: return
        val target = pageIndex

        // Cached? Swap image and number together, instantly.
        currentStore.peek(target, RENDER_WIDTH_PX)?.let {
            rendered = RenderedPage(it, target)
            currentStore.prefetchAround(target, RENDER_WIDTH_PX)
            return
        }

        val loaded = currentStore.load(target, RENDER_WIDTH_PX)

        // A newer turn may have landed while this render was in flight. Dropping the stale result
        // is what keeps fast repeated taps from showing a page the agent has already moved past.
        if (target == pageIndex && loaded != null) {
            rendered = RenderedPage(loaded, target)
        }
        currentStore.prefetchAround(target, RENDER_WIDTH_PX)
    }

    /** Releases the PdfRenderer and its file descriptor. Not optional — both leak otherwise. */
    fun closeCurrent() {
        speaker.shutdown()
        translator.close()
        store?.clear()
        source?.close()
        source = null
        store = null
        rendered = RenderedPage(null, 0)
    }

    private companion object {
        /**
         * Fixed render width, shared by both panes.
         *
         * Both halves draw the same bitmap scaled to fit, rather than each rendering at its own
         * width. Rendering twice would double the work and, on a page turn, finish at different
         * moments — visibly desynchronising the two halves.
         */
        const val RENDER_WIDTH_PX = 1400

        const val RADIX_36 = 36
        const val KEY_LANGUAGE = "client_language"
        const val KEY_KIND = "meeting_kind"
        const val MIN_LEGIBILITY = 1f
        const val MAX_LEGIBILITY = 2f
    }
}
