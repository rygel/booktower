package org.booktower.services

import com.fasterxml.jackson.databind.JsonNode
import org.booktower.config.Json
import org.booktower.models.CreateBookRequest
import org.booktower.models.CreateBookmarkRequest
import org.booktower.models.CreateLibraryRequest
import org.booktower.models.CreateMagicShelfRequest
import org.booktower.models.ReadStatus
import org.booktower.models.ShelfRuleType
import org.booktower.models.UpdateBookRequest
import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private val logger = LoggerFactory.getLogger("booktower.SeedService")

data class SeedResult(
    val libraries: Int,
    val books: Int,
)

data class SeedFilesResult(
    val queued: Int,
    val skipped: Int,
)

private data class SeedAudioBook(
    val title: String,
    val author: String,
    val description: String,
    val librivoxId: Int,
    val tags: List<String> = emptyList(),
)

private const val LIBRIVOX_LIBRARY_NAME = "LibriVox Audiobooks"

private val LIBRIVOX_SEED_BOOKS =
    listOf(
        SeedAudioBook(
            title = "Alice's Adventures in Wonderland",
            author = "Lewis Carroll",
            description =
                "A young girl named Alice falls through a rabbit hole into a surreal fantasy world " +
                    "populated by peculiar creatures. One of the most beloved works of English literature, " +
                    "rich with wordplay, logic, and whimsy.",
            librivoxId = 200,
            tags = listOf("classic", "fantasy", "audiobook"),
        ),
        SeedAudioBook(
            title = "The Metamorphosis",
            author = "Franz Kafka",
            description = "One morning Gregor Samsa wakes to find himself transformed into a monstrous insect. Kafka's landmark novella is an unsettling meditation on alienation, family duty, and existential despair.",
            librivoxId = 527,
            tags = listOf("classic", "philosophy", "audiobook"),
        ),
        SeedAudioBook(
            title = "Treasure Island",
            author = "Robert Louis Stevenson",
            description = "Young Jim Hawkins discovers a treasure map and sets sail on a perilous voyage with the unforgettable Long John Silver. The definitive pirate adventure — action, treachery, and buried gold.",
            librivoxId = 449,
            tags = listOf("adventure", "classic", "audiobook"),
        ),
    )

private data class SeedBook(
    val title: String,
    val author: String,
    val description: String,
    val publishedDate: String,
    val pageCount: Int,
    val tags: List<String> = emptyList(),
    val status: String? = null,
    val rating: Int? = null,
    val series: String? = null,
    val seriesIndex: Double? = null,
    val gutenbergId: Int? = null,
)

private data class SeedLibrary(
    val name: String,
    val books: List<SeedBook>,
)

private val SEED_LIBRARIES =
    listOf(
        SeedLibrary(
            "Science Fiction Classics",
            listOf(
                SeedBook(
                    title = "The War of the Worlds",
                    author = "H.G. Wells",
                    description = "A Martian invasion of Earth, witnessed by an unnamed narrator in Surrey, England. Wells's landmark science fiction novel explores themes of imperialism and human vulnerability through vivid descriptions of alien tripod war machines and the collapse of Victorian society.",
                    publishedDate = "1898",
                    pageCount = 192,
                    tags = listOf("sci-fi", "adventure"),
                    status = "FINISHED",
                    rating = 5,
                    gutenbergId = 36,
                ),
                SeedBook(
                    title = "The Time Machine",
                    author = "H.G. Wells",
                    description = "A Victorian scientist builds a machine that allows him to travel through time, journeying to the year 802,701 where he discovers humanity has evolved into two distinct species — the gentle Eloi above ground and the subterranean, predatory Morlocks.",
                    publishedDate = "1895",
                    pageCount = 118,
                    tags = listOf("sci-fi"),
                    status = "FINISHED",
                    rating = 4,
                    gutenbergId = 35,
                ),
                SeedBook(
                    title = "Twenty Thousand Leagues Under the Sea",
                    author = "Jules Verne",
                    description = "Marine biologist Professor Aronnax is captured by the mysterious Captain Nemo aboard the Nautilus, the world's most advanced submarine, and embarks on a breathtaking voyage through the deepest oceans of the world.",
                    publishedDate = "1870",
                    pageCount = 394,
                    tags = listOf("sci-fi", "adventure", "ocean"),
                    status = "READING",
                    series = "Extraordinary Voyages",
                    seriesIndex = 6.0,
                    gutenbergId = 164,
                ),
                SeedBook(
                    title = "Journey to the Center of the Earth",
                    author = "Jules Verne",
                    description = "Professor Otto Lidenbrock leads his nephew and an Icelandic guide through volcanic tunnels deep into the Earth, encountering prehistoric creatures, vast underground seas, and a lost world beneath our feet.",
                    publishedDate = "1864",
                    pageCount = 183,
                    tags = listOf("sci-fi", "adventure", "exploration"),
                    status = "WANT_TO_READ",
                    series = "Extraordinary Voyages",
                    seriesIndex = 3.0,
                    gutenbergId = 3526,
                ),
                SeedBook(
                    title = "Frankenstein",
                    author = "Mary Shelley",
                    description = "Young scientist Victor Frankenstein creates a sapient creature in an unorthodox experiment and then abandons it in horror. This foundational work of science fiction and Gothic literature asks what it means to be human and who bears responsibility for creation.",
                    publishedDate = "1818",
                    pageCount = 280,
                    tags = listOf("sci-fi", "gothic", "horror"),
                    status = "FINISHED",
                    rating = 5,
                    gutenbergId = 84,
                ),
                SeedBook(
                    title = "The Invisible Man",
                    author = "H.G. Wells",
                    description = "A scientist discovers a method to make himself invisible but cannot reverse the process, leading to his descent into paranoia and violence as he terrorises a small English village. A sharp parable on isolation and the corruption of unchecked power.",
                    publishedDate = "1897",
                    pageCount = 180,
                    tags = listOf("sci-fi", "thriller"),
                    status = "WANT_TO_READ",
                    gutenbergId = 5230,
                ),
                SeedBook(
                    title = "From the Earth to the Moon",
                    author = "Jules Verne",
                    description = "Members of the Baltimore Gun Club, bored after the Civil War, decide to build an enormous cannon and fire a capsule to the Moon, presaging many real aspects of the Apollo program by a century in its scientific speculation and detail.",
                    publishedDate = "1865",
                    pageCount = 236,
                    tags = listOf("sci-fi", "adventure", "space"),
                    series = "Extraordinary Voyages",
                    seriesIndex = 4.0,
                    gutenbergId = 83,
                ),
                SeedBook(
                    title = "The Island of Doctor Moreau",
                    author = "H.G. Wells",
                    description = "A shipwrecked Englishman discovers an island where a brilliant but deranged scientist has been surgically transforming animals into humanoid creatures. A disturbing examination of pain, identity, and the ethics of science playing God.",
                    publishedDate = "1896",
                    pageCount = 160,
                    tags = listOf("sci-fi", "horror"),
                    status = "WANT_TO_READ",
                    gutenbergId = 159,
                ),
            ),
        ),
        SeedLibrary(
            "Victorian & Classic Literature",
            listOf(
                SeedBook(
                    title = "Pride and Prejudice",
                    author = "Jane Austen",
                    description = "The Bennet family's five daughters must navigate marriage and society in Regency England. The novel follows the witty Elizabeth Bennet and the proud Mr Darcy as their initial antagonism gradually transforms into deep mutual respect and love.",
                    publishedDate = "1813",
                    pageCount = 432,
                    tags = listOf("classic", "romance"),
                    status = "FINISHED",
                    rating = 5,
                    gutenbergId = 1342,
                ),
                SeedBook(
                    title = "Jane Eyre",
                    author = "Charlotte Brontë",
                    description = "An orphan girl endures hardship at a charity school before becoming governess at Thornfield Hall, where she falls in love with the brooding Mr Rochester. A landmark novel of psychological depth and one of the first proto-feminist works in English literature.",
                    publishedDate = "1847",
                    pageCount = 532,
                    tags = listOf("classic", "romance", "gothic"),
                    status = "READING",
                    gutenbergId = 1260,
                ),
                SeedBook(
                    title = "Wuthering Heights",
                    author = "Emily Brontë",
                    description = "The passionate and destructive relationship between the foundling Heathcliff and Catherine Earnshaw, spanning two generations on the bleak Yorkshire moors. Emily Brontë's only novel is a dark and complex portrait of obsessive love and class.",
                    publishedDate = "1847",
                    pageCount = 342,
                    tags = listOf("classic", "gothic", "romance"),
                    gutenbergId = 768,
                ),
                SeedBook(
                    title = "Great Expectations",
                    author = "Charles Dickens",
                    description = "Orphan Pip unexpectedly becomes a gentleman of great expectations, tracing his moral and social development from humble origins in the Kent marshes to the complicated heights of Victorian London society and the mystery of his secret benefactor.",
                    publishedDate = "1861",
                    pageCount = 544,
                    tags = listOf("classic"),
                    status = "WANT_TO_READ",
                    gutenbergId = 1400,
                ),
                SeedBook(
                    title = "A Tale of Two Cities",
                    author = "Charles Dickens",
                    description = "Set during the tumultuous years leading to and through the French Revolution, the novel interweaves the lives of English and French characters between London and Paris, building to a climax of sacrifice and redemption.",
                    publishedDate = "1859",
                    pageCount = 448,
                    tags = listOf("classic", "history"),
                    gutenbergId = 98,
                ),
                SeedBook(
                    title = "Crime and Punishment",
                    author = "Fyodor Dostoevsky",
                    description = "Impoverished student Raskolnikov murders a pawnbroker and her sister, then struggles with the psychological and moral consequences of his act. Dostoevsky's masterwork is a profound study of guilt, free will, and the nature of human consciousness.",
                    publishedDate = "1866",
                    pageCount = 551,
                    tags = listOf("classic", "psychology"),
                    status = "FINISHED",
                    rating = 5,
                    gutenbergId = 2554,
                ),
                SeedBook(
                    title = "The Brothers Karamazov",
                    author = "Fyodor Dostoevsky",
                    description = "Three brothers struggle with questions of faith, free will, and morality amid the murder of their dissolute father. Widely regarded as the supreme achievement of Dostoevsky's career and one of the greatest novels ever written.",
                    publishedDate = "1880",
                    pageCount = 824,
                    tags = listOf("classic", "philosophy"),
                    status = "WANT_TO_READ",
                    gutenbergId = 28054,
                ),
                SeedBook(
                    title = "Adventures of Huckleberry Finn",
                    author = "Mark Twain",
                    description = "Young Huck Finn escapes his abusive father and travels down the Mississippi River on a raft with Jim, an escaped slave. Both an adventure story and a sharp satire of antebellum Southern society, widely considered Twain's masterpiece.",
                    publishedDate = "1884",
                    pageCount = 366,
                    tags = listOf("classic", "adventure"),
                    status = "FINISHED",
                    rating = 4,
                    gutenbergId = 76,
                ),
            ),
        ),
        SeedLibrary(
            "Adventure & Philosophy",
            listOf(
                SeedBook(
                    title = "Treasure Island",
                    author = "Robert Louis Stevenson",
                    description = "Young Jim Hawkins discovers a treasure map leading to a pirate's buried gold, setting off on a sea voyage with the duplicitous Long John Silver and a crew of dangerous buccaneers. The definitive pirate adventure story.",
                    publishedDate = "1883",
                    pageCount = 292,
                    tags = listOf("adventure", "classic"),
                    status = "FINISHED",
                    rating = 4,
                    gutenbergId = 120,
                ),
                SeedBook(
                    title = "The Count of Monte Cristo",
                    author = "Alexandre Dumas",
                    description = "After being wrongfully imprisoned for thirteen years, the sailor Edmond Dantès escapes and transforms himself into the mysterious and wealthy Count of Monte Cristo to exact a long and elaborate revenge upon those who betrayed him.",
                    publishedDate = "1844",
                    pageCount = 1276,
                    tags = listOf("adventure", "classic", "romance"),
                    status = "READING",
                    gutenbergId = 1184,
                ),
                SeedBook(
                    title = "The Three Musketeers",
                    author = "Alexandre Dumas",
                    description = "Young Gascon d'Artagnan travels to Paris to join the legendary Musketeers of the Guard and becomes embroiled in court intrigue alongside the noble Athos, the vain Porthos, and the devout Aramis.",
                    publishedDate = "1844",
                    pageCount = 700,
                    tags = listOf("adventure", "classic"),
                    gutenbergId = 1257,
                ),
                SeedBook(
                    title = "Robinson Crusoe",
                    author = "Daniel Defoe",
                    description = "English sailor Robinson Crusoe is shipwrecked on a deserted island near Trinidad and survives alone for twenty-eight years, his only companion eventually being a native he names Friday. Often considered the first novel in the English language.",
                    publishedDate = "1719",
                    pageCount = 320,
                    tags = listOf("adventure", "classic"),
                    gutenbergId = 521,
                ),
                SeedBook(
                    title = "The Call of the Wild",
                    author = "Jack London",
                    description = "Buck, a large domesticated dog living in California, is stolen and sold as a sled dog in the Yukon during the 1890s Gold Rush. London's powerful novella traces his transformation from civilised pet to wild creature answering the call of his ancestors.",
                    publishedDate = "1903",
                    pageCount = 172,
                    tags = listOf("adventure", "animals"),
                    status = "FINISHED",
                    rating = 4,
                    gutenbergId = 215,
                ),
                SeedBook(
                    title = "Moby-Dick",
                    author = "Herman Melville",
                    description = "Captain Ahab's obsessive quest for revenge against the white whale Moby Dick takes the ship Pequod and its crew on an epic voyage through the oceans of the world. One of literature's most ambitious and studied novels.",
                    publishedDate = "1851",
                    pageCount = 654,
                    tags = listOf("classic", "adventure", "ocean"),
                    status = "WANT_TO_READ",
                    gutenbergId = 2489,
                ),
                SeedBook(
                    title = "Meditations",
                    author = "Marcus Aurelius",
                    description = "Personal writings of the Roman Emperor Marcus Aurelius, composed as private notes on Stoic philosophy during military campaigns. These reflections on duty, reason, impermanence, and virtue have guided readers for nearly two millennia.",
                    publishedDate = "170",
                    pageCount = 256,
                    tags = listOf("philosophy", "non-fiction"),
                    status = "FINISHED",
                    rating = 5,
                    gutenbergId = 2680,
                ),
                SeedBook(
                    title = "The Art of War",
                    author = "Sun Tzu",
                    description = "The oldest and most celebrated military treatise in Asia, comprising thirteen chapters each devoted to a different aspect of warfare. Its lessons on strategy, deception, adaptability, and the importance of intelligence have been applied far beyond the battlefield.",
                    publishedDate = "500 BC",
                    pageCount = 112,
                    tags = listOf("philosophy", "non-fiction", "strategy"),
                    status = "FINISHED",
                    rating = 4,
                    gutenbergId = 132,
                ),
            ),
        ),
    )

private data class SeedComic(
    val title: String,
    val author: String,
    val description: String,
    val archiveId: String,
    val fileName: String,
    val tags: List<String> = emptyList(),
)

private const val COMICS_LIBRARY_NAME = "Public Domain Comics"

private val COMIC_SEED_BOOKS =
    listOf(
        SeedComic(
            title = "Dances With Demons #4",
            author = "Marvel UK",
            description = "Dark supernatural comic from 1993, featuring occult themes and horror elements in a British setting.",
            archiveId = "199312DancesWithDemonsV1004",
            fileName = "199312 Dances with Demons v1 004.cbz",
            tags = listOf("horror", "supernatural", "comic"),
        ),
        SeedComic(
            title = "Daring Comics #11",
            author = "Timely Comics",
            description = "Golden age superhero comic from 1945, published by Timely Comics (the predecessor to Marvel). Features wartime heroes and adventure stories.",
            archiveId = "daring-comics-011-timely-1945",
            fileName = "Daring Comics 011 (Timely, 1945) (c2c) (Mark Bowen+Superscan).cbz",
            tags = listOf("superhero", "golden-age", "comic"),
        ),
        SeedComic(
            title = "Detective Comics #26",
            author = "DC Comics",
            description = "Early detective and mystery anthology comic from 1939, published by DC Comics before Batman's debut issue. Features hard-boiled detective stories.",
            archiveId = "detective-comics-026",
            fileName = "Detective Comics 026 (DC) (Apr 1939) (c2c) (Mark Bowen+Superscan).cbz",
            tags = listOf("detective", "mystery", "golden-age", "comic"),
        ),
    )

/** Converts a bare year like "1898" to "1898-01-01"; returns null for non-parseable values. */
private fun normalizeSeedDate(date: String?): String? =
    when {
        date == null -> null
        date.matches(Regex("\\d{4}")) -> "$date-01-01"
        date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> date
        else -> null
    }

/** Converts a title to a filesystem-safe filename, preserving readability. */
private fun safeFilename(
    title: String,
    ext: String,
): String =
    title
        .replace(Regex("[^a-zA-Z0-9\\s._-]"), "")
        .replace(Regex("\\s+"), "_")
        .take(80)
        .trimEnd('_', '.') + ".$ext"

class SeedService(
    private val bookService: BookService,
    private val libraryService: LibraryService,
    private val coversPath: String,
    private val booksPath: String,
    private val backgroundTaskService: BackgroundTaskService = BackgroundTaskService(),
    private val bookmarkService: BookmarkService? = null,
    private val readingSessionService: ReadingSessionService? = null,
    private val magicShelfService: MagicShelfService? = null,
    private val userSettingsService: UserSettingsService? = null,
    private val jdbi: Jdbi? = null,
) {
    private val coverExecutor: ExecutorService = Executors.newFixedThreadPool(4)

    /**
     * Retries a failed download task using its stored metadata.
     * Returns true if the retry was queued, false if the task can't be retried.
     */
    fun retryDownload(
        userId: UUID,
        task: BackgroundTask,
    ): Boolean {
        val meta = task.retryMeta ?: return false
        val bookId = meta["bookId"]?.let { UUID.fromString(it) } ?: return false

        return when (task.type) {
            "download-comic" -> {
                val archiveId = meta["archiveId"] ?: return false
                val fileName = meta["fileName"] ?: return false
                val title = meta["title"] ?: "Comic"
                coverExecutor.submit {
                    downloadComic(userId, bookId, SeedComic(title, "", "", archiveId, fileName))
                }
                true
            }

            "download-epub" -> {
                val gutenbergId = meta["gutenbergId"]?.toIntOrNull() ?: return false
                val title = meta["title"] ?: "Book"
                coverExecutor.submit {
                    downloadEpub(userId, bookId, gutenbergId, title)
                }
                true
            }

            "download-audiobook" -> {
                val librivoxId = meta["librivoxId"]?.toIntOrNull() ?: return false
                val title = meta["title"] ?: "Audiobook"
                coverExecutor.submit {
                    downloadLibrivoxChapters(userId, bookId, librivoxId, title)
                }
                true
            }

            else -> {
                false
            }
        }
    }

    /**
     * Seeds demo data for [userId]. Returns null if the user already has libraries (idempotent guard).
     */
    fun seed(userId: UUID): SeedResult? {
        if (libraryService.getLibraries(userId).isNotEmpty()) return null

        var bookCount = 0

        SEED_LIBRARIES.forEach { seedLib ->
            val safeName = seedLib.name.lowercase().replace(Regex("[^a-z0-9]+"), "-")
            val lib =
                libraryService.createLibrary(
                    userId,
                    CreateLibraryRequest(seedLib.name, "./data/libraries/$safeName"),
                )

            seedLib.books.forEach { seedBook ->
                bookService
                    .createBook(
                        userId,
                        CreateBookRequest(seedBook.title, seedBook.author, seedBook.description, lib.id),
                    ).onSuccess { book ->
                        val bookId = UUID.fromString(book.id)

                        bookService.updateBook(
                            userId,
                            bookId,
                            UpdateBookRequest(
                                title = seedBook.title,
                                author = seedBook.author,
                                description = seedBook.description,
                                series = seedBook.series,
                                seriesIndex = seedBook.seriesIndex,
                                isbn = null,
                                publisher = null,
                                publishedDate = normalizeSeedDate(seedBook.publishedDate),
                                pageCount = seedBook.pageCount,
                            ),
                        )

                        seedBook.status?.let { s ->
                            ReadStatus.entries.firstOrNull { it.name == s }?.let { st ->
                                bookService.setStatus(userId, bookId, st)
                            }
                        }

                        seedBook.rating?.let { bookService.setRating(userId, bookId, it) }

                        if (seedBook.tags.isNotEmpty()) {
                            bookService.setTags(userId, bookId, seedBook.tags)
                        }

                        bookCount++

                        // Fetch cover from Open Library asynchronously
                        val title = seedBook.title
                        val author = seedBook.author
                        val bookIdStr = book.id
                        coverExecutor.submit {
                            fetchCover(userId, bookId, bookIdStr, title, author)
                        }
                    }
            }
        }

        logger.info("Demo seed complete for $userId: ${SEED_LIBRARIES.size} libraries, $bookCount books (covers fetching in background)")
        return SeedResult(SEED_LIBRARIES.size, bookCount)
    }

    /**
     * Downloads EPUB files from Project Gutenberg for all seeded books that don't yet have a file.
     * Returns null if the user has no seeded libraries (seed must run first).
     * Downloads happen in background; returns immediately with the count of queued downloads.
     */
    fun seedFiles(userId: UUID): SeedFilesResult? {
        val libraries = libraryService.getLibraries(userId)
        if (libraries.isEmpty()) return null

        var queued = 0
        var skipped = 0

        SEED_LIBRARIES.forEach { seedLib ->
            val lib = libraries.firstOrNull { it.name == seedLib.name } ?: return@forEach
            val books = bookService.getBooks(userId, lib.id, page = 1, pageSize = 50).getBooks()

            seedLib.books.forEach { seedBook ->
                if (seedBook.gutenbergId == null) {
                    skipped++
                    return@forEach
                }
                val book = books.firstOrNull { it.title == seedBook.title } ?: return@forEach
                if (book.fileSize > 0) {
                    skipped++
                    return@forEach
                }

                val bookId = UUID.fromString(book.id)
                val gId = seedBook.gutenbergId
                coverExecutor.submit {
                    downloadEpub(userId, bookId, gId, seedBook.title)
                }
                queued++
            }
        }

        logger.info("Gutenberg file downloads queued for $userId: $queued queued, $skipped skipped")
        return SeedFilesResult(queued, skipped)
    }

    /**
     * Creates a "LibriVox Audiobooks" library and queues chapter downloads for each configured audiobook.
     * Returns null if the library already exists (idempotent).
     */
    fun seedLibrivox(userId: UUID): SeedFilesResult? {
        val existing = libraryService.getLibraries(userId)
        if (existing.any { it.name == LIBRIVOX_LIBRARY_NAME }) return null

        val lib =
            libraryService.createLibrary(
                userId,
                CreateLibraryRequest(LIBRIVOX_LIBRARY_NAME, "./data/libraries/librivox"),
            )

        var queued = 0
        LIBRIVOX_SEED_BOOKS.forEach { seedBook ->
            bookService
                .createBook(
                    userId,
                    CreateBookRequest(seedBook.title, seedBook.author, seedBook.description, lib.id),
                ).onSuccess { book ->
                    val bookId = UUID.fromString(book.id)
                    bookService.updateBook(
                        userId,
                        bookId,
                        UpdateBookRequest(
                            title = seedBook.title,
                            author = seedBook.author,
                            description = seedBook.description,
                            series = null,
                            seriesIndex = null,
                            isbn = null,
                            publisher = "LibriVox",
                            publishedDate = null,
                            pageCount = null,
                        ),
                    )
                    if (seedBook.tags.isNotEmpty()) bookService.setTags(userId, bookId, seedBook.tags)
                    val lId = seedBook.librivoxId
                    val bookIdStr = book.id
                    coverExecutor.submit {
                        fetchCover(userId, bookId, bookIdStr, seedBook.title, seedBook.author)
                        downloadLibrivoxChapters(userId, bookId, lId, seedBook.title)
                    }
                    queued++
                }
        }

        logger.info("LibriVox seed queued $queued audiobooks for $userId")
        return SeedFilesResult(queued, 0)
    }

    /**
     * Creates a "Public Domain Comics" library and queues CBZ downloads from Archive.org.
     * Returns null if the library already exists (idempotent).
     */
    fun seedComics(userId: UUID): SeedFilesResult? {
        val existing = libraryService.getLibraries(userId)
        if (existing.any { it.name == COMICS_LIBRARY_NAME }) return null

        val lib =
            libraryService.createLibrary(
                userId,
                CreateLibraryRequest(COMICS_LIBRARY_NAME, "./data/libraries/comics"),
            )

        var queued = 0
        COMIC_SEED_BOOKS.forEach { comic ->
            bookService
                .createBook(
                    userId,
                    CreateBookRequest(comic.title, comic.author, comic.description, lib.id),
                ).onSuccess { b ->
                    val bookId = UUID.fromString(b.id)
                    if (comic.tags.isNotEmpty()) bookService.setTags(userId, bookId, comic.tags)

                    coverExecutor.submit {
                        downloadComic(userId, bookId, comic)
                    }
                    queued++
                }
        }

        logger.info("Comic seed: queued $queued downloads for user $userId")
        return SeedFilesResult(queued, 0)
    }

    /**
     * Seeds a comprehensive demo touching all features: libraries, books, categories, moods,
     * authors, bookmarks, reading sessions, reading goal, smart shelves, journal entries,
     * notebooks, reviews, and filter presets.
     * Returns null if the basic seed fails (e.g. user already has libraries).
     */
    fun seedFullDemo(userId: UUID): SeedResult? {
        // Step 1: Create basic libraries and books
        val basicResult = seed(userId) ?: return null

        val libraries = libraryService.getLibraries(userId)
        val allBooks =
            libraries.flatMap { lib ->
                bookService.getBooks(userId, lib.id, page = 1, pageSize = 100).getBooks()
            }

        // Step 2: Set categories on some books
        allBooks.take(10).forEachIndexed { i, book ->
            val bookId = UUID.fromString(book.id)
            val cats =
                when (i % 4) {
                    0 -> listOf("Fiction", "Classic Literature")
                    1 -> listOf("Science Fiction", "Adventure")
                    2 -> listOf("Philosophy", "Non-Fiction")
                    else -> listOf("Fantasy", "Young Adult")
                }
            bookService.setCategories(userId, bookId, cats)
        }

        // Step 3: Set moods on some books
        allBooks.take(8).forEachIndexed { i, book ->
            val bookId = UUID.fromString(book.id)
            val moods =
                when (i % 4) {
                    0 -> listOf("dark", "atmospheric")
                    1 -> listOf("adventurous", "exciting")
                    2 -> listOf("thoughtful", "philosophical")
                    else -> listOf("whimsical", "lighthearted")
                }
            bookService.setMoods(userId, bookId, moods)
        }

        // Step 4: Set multiple authors on some books
        allBooks.take(3).forEach { book ->
            val bookId = UUID.fromString(book.id)
            bookService.setAuthors(userId, bookId, listOf(book.author ?: "Unknown", "Editor Name"))
        }

        // Step 5: Create bookmarks
        bookmarkService?.let { svc ->
            allBooks.take(5).forEach { book ->
                svc.createBookmark(userId, CreateBookmarkRequest(book.id, 42, "Important passage", "This part was really interesting"))
                svc.createBookmark(userId, CreateBookmarkRequest(book.id, 100, "Chapter 5 start", null))
            }
        }

        // Step 6: Record reading sessions (for analytics)
        readingSessionService?.let { svc ->
            allBooks.take(6).forEachIndexed { i, book ->
                val bookId = UUID.fromString(book.id)
                repeat(3) { j ->
                    svc.recordSession(userId, bookId, (i + 1) * 10 + j * 5, (i + 1) * 10 + j * 5 + 8, 8)
                }
            }
        }

        // Step 7: Set reading goal
        val year =
            java.time.LocalDate
                .now()
                .year
        userSettingsService?.set(userId, "reading.goal.$year", "24")

        // Step 8: Create smart shelves
        magicShelfService?.let { svc ->
            svc.createShelf(userId, CreateMagicShelfRequest("Currently Reading", ShelfRuleType.STATUS, "READING"))
            svc.createShelf(userId, CreateMagicShelfRequest("Sci-Fi Collection", ShelfRuleType.TAG, "sci-fi"))
            svc.createShelf(userId, CreateMagicShelfRequest("Top Rated", ShelfRuleType.RATING_GTE, "4"))
        }

        // Step 9: Create journal entries (direct JDBI)
        jdbi?.let { db ->
            allBooks.take(3).forEach { book ->
                db.useHandle<Exception> { handle ->
                    handle
                        .createUpdate(
                            """
                            INSERT INTO book_journal_entries (id, user_id, book_id, title, content, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                        ).bind(0, UUID.randomUUID().toString())
                        .bind(1, userId.toString())
                        .bind(2, book.id)
                        .bind(3, "Reading Notes")
                        .bind(4, "Started reading ${book.title}. The opening chapter sets a great tone.")
                        .bind(5, Instant.now().toString())
                        .bind(6, Instant.now().toString())
                        .execute()
                }
            }

            // Step 10: Create notebooks
            allBooks.take(2).forEach { book ->
                db.useHandle<Exception> { handle ->
                    handle
                        .createUpdate(
                            """
                            INSERT INTO book_notebooks (id, book_id, user_id, title, content, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                        ).bind(0, UUID.randomUUID().toString())
                        .bind(1, book.id)
                        .bind(2, userId.toString())
                        .bind(3, "Character Notes")
                        .bind(4, "Main character: interesting development arc. Supporting cast adds depth.")
                        .bind(5, Instant.now().toString())
                        .bind(6, Instant.now().toString())
                        .execute()
                }
            }

            // Step 11: Create reviews
            allBooks.take(4).forEachIndexed { i, book ->
                db.useHandle<Exception> { handle ->
                    handle
                        .createUpdate(
                            """
                            INSERT INTO book_reviews (id, book_id, user_id, rating, title, body, spoiler, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                        ).bind(0, UUID.randomUUID().toString())
                        .bind(1, book.id)
                        .bind(2, userId.toString())
                        .bind(3, 3 + (i % 3))
                        .bind(4, if (i % 2 == 0) "A compelling read" else "Thought-provoking")
                        .bind(5, "This book offers ${if (i % 2 == 0) "a gripping narrative" else "deep insights"}. Well worth the time.")
                        .bind(6, false)
                        .bind(7, Instant.now().toString())
                        .bind(8, Instant.now().toString())
                        .execute()
                }
            }

            // Step 12: Create annotations
            allBooks.take(3).forEachIndexed { i, book ->
                db.useHandle<Exception> { handle ->
                    handle
                        .createUpdate(
                            """
                            INSERT INTO book_annotations (id, user_id, book_id, page, selected_text, color, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                        ).bind(0, UUID.randomUUID().toString())
                        .bind(1, userId.toString())
                        .bind(2, book.id)
                        .bind(3, 15 + i * 10)
                        .bind(4, "A particularly striking passage that captures the essence of the narrative.")
                        .bind(5, listOf("yellow", "blue", "green")[i % 3])
                        .bind(6, Instant.now().toString())
                        .execute()
                }
            }

            // Step 13: Create filter presets
            db.useHandle<Exception> { handle ->
                handle
                    .createUpdate(
                        """
                        INSERT INTO filter_presets (id, user_id, name, filters, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                    ).bind(0, UUID.randomUUID().toString())
                    .bind(1, userId.toString())
                    .bind(2, "Unread Sci-Fi")
                    .bind(3, """{"tagFilter":"sci-fi","statusFilter":"WANT_TO_READ"}""")
                    .bind(4, Instant.now().toString())
                    .bind(5, Instant.now().toString())
                    .execute()

                handle
                    .createUpdate(
                        """
                        INSERT INTO filter_presets (id, user_id, name, filters, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                    ).bind(0, UUID.randomUUID().toString())
                    .bind(1, userId.toString())
                    .bind(2, "Highly Rated")
                    .bind(3, """{"ratingGte":4}""")
                    .bind(4, Instant.now().toString())
                    .bind(5, Instant.now().toString())
                    .execute()
            }
        }

        // Auto-trigger file downloads so books are readable
        seedFiles(userId)
        seedLibrivox(userId)
        seedComics(userId)

        logger.info(
            "Full demo seed complete for $userId: enriched ${allBooks.size} books with categories, moods, " +
                "bookmarks, sessions, shelves, journals, notebooks, reviews, annotations, presets, downloads",
        )
        return basicResult
    }

    /** Human-readable error message for HTTP status codes. */
    private fun httpErrorMessage(
        code: Int,
        host: String,
    ): String {
        val reason =
            when (code) {
                401 -> "Source requires authentication"
                403 -> "Access forbidden by the source server"
                404 -> "File not found on the source server"
                429 -> "Rate limited — too many requests, try again later"
                500 -> "Source server error"
                502 -> "Source server gateway error"
                503 -> "Source server temporarily unavailable"
                else -> "Server returned HTTP $code"
            }
        return "$reason (HTTP $code from $host)"
    }

    private fun downloadComic(
        userId: UUID,
        bookId: UUID,
        comic: SeedComic,
    ) {
        val taskId =
            backgroundTaskService.start(
                userId,
                "download-comic",
                "Downloading comic: ${comic.title}",
                retryMeta = mapOf("bookId" to bookId.toString(), "archiveId" to comic.archiveId, "fileName" to comic.fileName, "title" to comic.title),
            )
        try {
            val encodedName =
                java.net.URLEncoder
                    .encode(comic.fileName, "UTF-8")
                    .replace("+", "%20")
            val url = "https://archive.org/download/${comic.archiveId}/$encodedName"
            val libDir = File("./data/libraries/comics")
            if (!libDir.exists() && !libDir.mkdirs()) logger.warn("Could not create directory: ${libDir.absolutePath}")
            val destFile = File(libDir, safeFilename(comic.title, "cbz"))

            if (destFile.exists() && destFile.length() > 0) {
                logger.info("Skipping download for '${comic.title}' — file already exists (${destFile.length()} bytes)")
                backgroundTaskService.complete(taskId, "Already downloaded")
                return
            }

            // Verify item is freely accessible via metadata API
            try {
                val metaConn =
                    java.net
                        .URI("https://archive.org/metadata/${comic.archiveId}")
                        .toURL()
                        .openConnection() as java.net.HttpURLConnection
                metaConn.connectTimeout = 10_000
                metaConn.readTimeout = 10_000
                metaConn.setRequestProperty("User-Agent", "BookTower/1.0 comic-seed")
                if (metaConn.responseCode == 200) {
                    val meta = metaConn.inputStream.use { Json.mapper.readTree(it) }
                    val collections = meta.get("metadata")?.get("collection")
                    val isRestricted =
                        when {
                            collections == null -> false
                            collections.isArray -> collections.any { it.asText() == "loggedin" || it.asText() == "patron-library-collection" }
                            else -> collections.asText() in listOf("loggedin", "patron-library-collection")
                        }
                    if (isRestricted) {
                        val reason = "Item '${comic.archiveId}' is access-restricted on archive.org (requires login)"
                        logger.warn("Comic download skipped for '${comic.title}': $reason")
                        backgroundTaskService.fail(taskId, reason)
                        return
                    }
                }
            } catch (e: Exception) {
                logger.debug("Metadata check failed for '${comic.archiveId}', proceeding with download: ${e.message}")
            }

            val conn =
                java.net
                    .URI(url)
                    .toURL()
                    .openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 300_000 // comics can be large
            conn.setRequestProperty("User-Agent", "BookTower/1.0 comic-seed")
            conn.instanceFollowRedirects = true
            if (conn.responseCode != 200) {
                val reason = httpErrorMessage(conn.responseCode, conn.url.host)
                logger.warn("Comic download failed for '${comic.title}': $reason")
                backgroundTaskService.fail(taskId, reason)
                return
            }
            conn.inputStream.use { input -> destFile.outputStream().use { input.copyTo(it) } }

            bookService.updateFileInfo(userId, bookId, destFile.absolutePath, destFile.length())
            logger.info("Downloaded comic '${comic.title}' (${destFile.length() / 1024}KB)")
            backgroundTaskService.complete(taskId, "Downloaded ${destFile.length() / 1024}KB")
        } catch (e: Exception) {
            logger.warn("Comic download failed for '${comic.title}': ${e.message}")
            backgroundTaskService.fail(taskId, e.message)
        }
    }

    private fun downloadLibrivoxChapters(
        userId: UUID,
        bookId: UUID,
        librivoxId: Int,
        title: String,
    ) {
        val taskId =
            backgroundTaskService.start(
                userId,
                "download-audiobook",
                "Downloading audiobook: $title",
                retryMeta = mapOf("bookId" to bookId.toString(), "librivoxId" to librivoxId.toString(), "title" to title),
            )
        try {
            // Check if at least one chapter file already exists (resumable)
            val booksDir = File(booksPath)
            val safeTitle = title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(60).trimEnd('_')
            val existingChapters = booksDir.listFiles { f -> (f.name.startsWith("$safeTitle-") || f.name.startsWith("$bookId-")) && f.name.endsWith(".mp3") && f.length() > 0 }
            if (existingChapters != null && existingChapters.isNotEmpty()) {
                logger.info("Skipping download for '$title' — ${existingChapters.size} chapter files already exist")
                backgroundTaskService.complete(taskId, "Already downloaded")
                return
            }

            val conn =
                java.net
                    .URI("https://librivox.org/rss/$librivoxId")
                    .toURL()
                    .openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "BookTower/1.0 librivox-seed")
            if (conn.responseCode != 200) {
                logger.warn("LibriVox RSS returned ${conn.responseCode} for '$title' (id=$librivoxId)")
                backgroundTaskService.fail(taskId, "RSS HTTP ${conn.responseCode}")
                return
            }

            val dbf =
                javax.xml.parsers.DocumentBuilderFactory
                    .newInstance()
                    .also {
                        // Prevent XXE — block external entities while allowing DOCTYPE for RSS feeds
                        it.setFeature("http://xml.org/sax/features/external-general-entities", false)
                        it.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                        it.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                        it.isXIncludeAware = false
                        it.isNamespaceAware = true
                    }
            val items = conn.inputStream.use { dbf.newDocumentBuilder().parse(it) }.getElementsByTagName("item")
            if (items.length == 0) {
                logger.warn("No items in LibriVox RSS for '$title'")
                backgroundTaskService.fail(taskId, "No chapters found in RSS")
                return
            }

            if (!booksDir.exists() && !booksDir.mkdirs()) logger.warn("Could not create directory: ${booksDir.absolutePath}")
            var downloaded = 0
            for (idx in 0 until items.length) {
                if (downloadChapter(userId, bookId, items.item(idx) as org.w3c.dom.Element, booksDir, idx, title)) {
                    downloaded++
                }
            }
            if (downloaded > 0) {
                bookService.updateBookFileAggregateSize(bookId)
                logger.info("LibriVox download complete for '$title': $downloaded/${items.length} chapters")
            }
            backgroundTaskService.complete(taskId, "Downloaded $downloaded/${items.length} chapters")
        } catch (e: Exception) {
            logger.warn("LibriVox download failed for '$title' (id=$librivoxId): ${e.message}")
            backgroundTaskService.fail(taskId, e.message)
        }
    }

    private fun downloadChapter(
        userId: UUID,
        bookId: UUID,
        item: org.w3c.dom.Element,
        booksDir: File,
        idx: Int,
        title: String,
    ): Boolean {
        val listenUrl =
            item
                .getElementsByTagName("enclosure")
                .takeIf { it.length > 0 }
                ?.let { (it.item(0) as org.w3c.dom.Element).getAttribute("url").takeIf { u -> u.isNotBlank() } }
                ?: return false
        val chapterTitle =
            item
                .getElementsByTagName("title")
                .item(0)
                ?.textContent
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        val durationSec =
            parseDuration(
                item.getElementsByTagNameNS("http://www.itunes.com/dtds/podcast-1.0.dtd", "duration").item(0)?.textContent,
            )
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(60).trimEnd('_')
        val destFile = File(booksDir, "$safeTitle-${idx.toString().padStart(4, '0')}.mp3")
        if (destFile.exists()) return false
        return try {
            val mp3Conn =
                java.net
                    .URI(listenUrl)
                    .toURL()
                    .openConnection() as java.net.HttpURLConnection
            mp3Conn.connectTimeout = 30_000
            mp3Conn.readTimeout = 180_000
            mp3Conn.setRequestProperty("User-Agent", "BookTower/1.0 librivox-seed")
            mp3Conn.instanceFollowRedirects = true
            if (mp3Conn.responseCode != 200) {
                logger.warn("Chapter $idx download failed for '$title': HTTP ${mp3Conn.responseCode}")
                return false
            }
            mp3Conn.inputStream.use { input -> destFile.outputStream().use { input.copyTo(it) } }
            bookService.addBookFile(userId, bookId, idx, chapterTitle, destFile.absolutePath, destFile.length(), durationSec)
            logger.debug("Downloaded ch $idx of '$title' (${destFile.length()} bytes)")
            true
        } catch (e: Exception) {
            logger.warn("Chapter $idx download error for '$title': ${e.message}")
            false
        }
    }

    private fun parseDuration(s: String?): Int? {
        if (s.isNullOrBlank()) return null
        val parts = s.trim().split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            else -> null
        }
    }

    private fun downloadEpub(
        userId: UUID,
        bookId: UUID,
        gutenbergId: Int,
        title: String,
    ) {
        val taskId =
            backgroundTaskService.start(
                userId,
                "download-epub",
                "Downloading EPUB: $title",
                retryMeta = mapOf("bookId" to bookId.toString(), "gutenbergId" to gutenbergId.toString(), "title" to title),
            )
        try {
            val url = "https://www.gutenberg.org/cache/epub/$gutenbergId/pg$gutenbergId.epub"
            val booksDir = File(booksPath)
            if (!booksDir.exists() && !booksDir.mkdirs()) logger.warn("Could not create directory: ${booksDir.absolutePath}")
            val destFile = File(booksDir, safeFilename(title, "epub"))

            if (destFile.exists() && destFile.length() > 0) {
                logger.info("Skipping download for '$title' — file already exists (${destFile.length()} bytes)")
                backgroundTaskService.complete(taskId, "Already downloaded")
                return
            }

            val conn =
                java.net
                    .URI(url)
                    .toURL()
                    .openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 120_000
            conn.setRequestProperty("User-Agent", "BookTower/1.0 gutenberg-seed")
            conn.instanceFollowRedirects = true
            if (conn.responseCode != 200) {
                val reason = httpErrorMessage(conn.responseCode, conn.url.host)
                logger.warn("Gutenberg download failed for '$title' (id=$gutenbergId): $reason")
                backgroundTaskService.fail(taskId, reason)
                return
            }
            conn.inputStream.use { input -> destFile.outputStream().use { input.copyTo(it) } }

            bookService.updateFileInfo(userId, bookId, destFile.absolutePath, destFile.length())
            logger.info("Downloaded EPUB for '$title' (${destFile.length()} bytes)")
            backgroundTaskService.complete(taskId, "Downloaded ${destFile.length() / 1024}KB")
        } catch (e: Exception) {
            logger.warn("Gutenberg download failed for '$title': ${e.message}")
            backgroundTaskService.fail(taskId, e.message)
        }
    }

    private fun fetchCover(
        userId: UUID,
        bookId: UUID,
        bookIdStr: String,
        title: String,
        author: String,
    ) {
        try {
            val query = java.net.URLEncoder.encode("$title $author", "UTF-8")
            val searchUrl = java.net.URI("https://openlibrary.org/search.json?q=$query&limit=1&fields=cover_i").toURL()
            val json: JsonNode =
                searchUrl
                    .openConnection()
                    .apply {
                        connectTimeout = 5_000
                        readTimeout = 8_000
                    }.getInputStream()
                    .use { Json.mapper.readTree(it) }

            val coverId =
                json
                    .get("docs")
                    ?.takeIf { it.isArray && it.size() > 0 }
                    ?.get(0)
                    ?.get("cover_i")
                    ?.asLong()
                    ?.takeIf { it > 0 }
                    ?: return

            val bytes =
                java.net
                    .URI("https://covers.openlibrary.org/b/id/$coverId-L.jpg")
                    .toURL()
                    .openConnection()
                    .apply {
                        connectTimeout = 5_000
                        readTimeout = 10_000
                    }.getInputStream()
                    .use { it.readBytes() }
            if (bytes.size < 2_000) return // placeholder / error image

            val coversDir = File(coversPath)
            if (!coversDir.exists() && !coversDir.mkdirs()) logger.warn("Could not create directory: ${coversDir.absolutePath}")
            File(coversDir, "$bookIdStr.jpg").writeBytes(bytes)
            bookService.updateCoverPath(userId, bookId, "$bookIdStr.jpg")

            logger.debug("Cover fetched for '$title'")
        } catch (e: Exception) {
            logger.debug("Cover fetch skipped for '$title': ${e.message}")
        }
    }
}
